/* Run getPopular for many real JS extensions and report which survive the prelude. */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

// Anchored to this file, so the harness works from any working directory and the path matches
// what the README tells you to download.
const HARNESS_DIR = path.dirname(fileURLToPath(import.meta.url));
const INDEX_PATH = process.env.OTAKU_INDEX ?? path.join(HARNESS_DIR, 'idx.json');

if (!fs.existsSync(INDEX_PATH)) {
    console.error(`No index at ${INDEX_PATH}.\nFetch it first:\n` +
        `  curl -s -o "${INDEX_PATH}" https://kodjodevf.github.io/mangayomi-extensions/index.json`);
    process.exit(2);
}

const index = JSON.parse(fs.readFileSync(INDEX_PATH, 'utf8'));
const candidates = index.filter((e) => e.sourceCodeLanguage === 1 && e.itemType === 0);

const wanted = Number.parseInt(process.argv[2] ?? '12', 10);
if (!Number.isInteger(wanted) || wanted < 1) {
    console.error(`Sample size must be a positive integer; got "${process.argv[2]}".`);
    process.exit(2);
}

// Deduped by sourceCodeUrl — the script this harness actually downloads and runs.
//
// This used to dedupe by id, on the stated grounds that same-named entries are "genuinely
// different entries with different base URLs". Measured against the live index, they are not:
// all 45 MangaDex entries share one baseUrl, one apiUrl and one sourceCodeUrl, and across all
// 114 JavaScript entries there are 114 distinct ids but only 18 distinct scripts. The index
// publishes one entry per language, all pointing at the same file.
//
// So deduping by id defeated the sweep it was meant to widen: because ids are all distinct,
// nothing was ever skipped, and the default run of 12 fetched mangadex.js five times while
// covering 8 distinct scripts. Keying on sourceCodeUrl covers 12, reaching Mangafire, Webtoons,
// Comick and MangaWorld, which the id-keyed sweep never got to.
const picked = [];
const seen = new Set();
for (const e of candidates) {
    if (picked.length >= wanted) break;
    const key = e.sourceCodeUrl;
    if (seen.has(key)) continue;
    seen.add(key);
    picked.push(e);
}

const results = [];
for (const e of picked) {
    const slug = String(e.id);
    const scriptPath = path.join(HARNESS_DIR, `tmp_${slug}.js`);
    const configPath = path.join(HARNESS_DIR, `tmp_${slug}.json`);
    try {
        // Bounded: the child-process timeout does not start until the download finishes, so a
        // server that accepts the connection and then stalls would hang the whole sweep.
        const res = await fetch(e.sourceCodeUrl, { signal: AbortSignal.timeout(60_000) });
        if (!res.ok) { results.push({ name: e.name, status: `script ${res.status}` }); continue; }
        const body = await res.text();
        if (body.length < 200) { results.push({ name: e.name, status: 'script too small' }); continue; }
        fs.writeFileSync(scriptPath, body);
        fs.writeFileSync(configPath, JSON.stringify({
            id: slug, name: e.name, baseUrl: e.baseUrl, apiUrl: e.apiUrl || '',
            lang: e.lang, isNsfw: !!e.isNsfw, preferences: {},
        }));

        const out = execFileSync('node', [path.join(HARNESS_DIR, 'run.mjs'), scriptPath, configPath, 'getPopular', '1'],
            { timeout: 90000, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
        const parsed = JSON.parse(out);
        const list = parsed.result?.list ?? [];
        results.push({
            name: e.name,
            status: list.length > 0 ? 'OK' : 'empty list',
            items: list.length,
            peakDocs: parsed.documentStats.peakLiveDocuments,
            liveLeaked: parsed.documentStats.liveDocuments,
            sample: list[0]?.name?.slice(0, 34),
        });
    } catch (err) {
        // run.mjs prints a line tagged FAILED for the two failures it recognises. Anything else —
        // a timeout kill, an ENOENT, a crash before that point — has no such line, and reporting
        // an empty error column there is how a real regression gets read as noise. Fall back to
        // the first non-empty line of whatever the child did say.
        const raw = (err.stderr || err.stdout || String(err)).toString();
        const lines = raw.split('\n').map((l) => l.trim()).filter(Boolean);
        const msg = lines.find((l) => l.includes('FAILED')) ?? lines[0] ?? String(err);
        // run.mjs prints its handle counts before any non-zero exit, so a source that leaks *and*
        // throws still reaches the leak gate below. Without this the gate only ever saw sources
        // that succeeded — and a leak severe enough to exhaust the pool always throws.
        const stats = lines.find((l) => l.startsWith('DOCSTATS '));
        let liveLeaked;
        if (stats) {
            try { liveLeaked = JSON.parse(stats.slice('DOCSTATS '.length)).liveDocuments; }
            catch { liveLeaked = undefined; }
        }
        results.push({ name: e.name, status: 'FAIL', error: msg.slice(0, 130), liveLeaked });
    } finally {
        for (const f of [scriptPath, configPath]) {
            // rmSync with force ignores a missing file, so the cleanup needs no empty catch —
            // the temp files legitimately may not exist when the download itself failed.
            fs.rmSync(f, { force: true });
        }
    }
}

for (const r of results) {
    const tag = r.status === 'OK' ? 'PASS' : (r.status === 'FAIL' ? 'FAIL' : 'WARN');
    console.log(`[${tag}] ${r.name.slice(0, 26).padEnd(26)} ${String(r.status).padEnd(13)}` +
        (r.items !== undefined ? ` items=${String(r.items).padEnd(3)} peakDocs=${r.peakDocs} leaked=${r.liveLeaked} ${r.sample ?? ''}` : ` ${r.error ?? ''}`));
}
const pass = results.filter((r) => r.status === 'OK').length;
console.log(`\n${pass}/${results.length} returned a populated list; ` +
    `${results.filter(r => r.status === 'FAIL').length} hard failures`);

// A leaked document handle is the one failure this harness must never merely report.
//
// Most rows here go red for reasons outside the repository — a Cloudflare interstitial, a site
// that moved domain — so a non-zero exit on those would make the harness useless. A leak is the
// opposite: it is always ours, and it stays invisible until a page carries enough rows to exhaust
// the 32-handle pool, which is exactly the size of input nobody tests with. Fail the run on it so
// the invariant is asserted rather than left for a reader to notice in a column of numbers.
const leaked = results.filter((r) => r.liveLeaked > 0);
if (leaked.length > 0) {
    console.error(`\nLEAKED DOCUMENT HANDLES in ${leaked.length} source(s):`);
    for (const r of leaked) console.error(`  ${r.name}: ${r.liveLeaked} handle(s) still live`);
    process.exit(1);
}
