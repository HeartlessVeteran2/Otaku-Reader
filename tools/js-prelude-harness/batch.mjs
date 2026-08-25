/* Run real JS extensions through popular -> detail -> pageList and report what survives. */
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
// An entry with no script URL cannot be run at all — there is nothing to download. Today's
// Mangayomi index has none, but OTAKU_INDEX can point this harness at any repository, so drop
// them here and say how many, rather than letting them reach the dedupe below where they would
// share one empty key and vanish without a word. `!e.sourceCodeUrl` covers both the missing
// field and the empty string the DTO defaults to; `??` would only catch the former.
const jsCandidates = index.filter((e) => e.sourceCodeLanguage === 1 && e.itemType === 0);
const candidates = jsCandidates.filter((e) => e.sourceCodeUrl);
if (candidates.length < jsCandidates.length) {
    console.error(`Skipping ${jsCandidates.length - candidates.length} entr` +
        `${jsCandidates.length - candidates.length === 1 ? 'y' : 'ies'} with no sourceCodeUrl.`);
}

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

/**
 * The live-handle count a failed leg reported before it died, or 0.
 *
 * run.mjs prints DOCSTATS before any non-zero exit precisely so a throwing source still reaches
 * the leak gate — a leak bad enough to exhaust the 32-handle pool always throws, so a gate that
 * only reads succeeding runs is a gate that never fires on the case it exists for.
 */
function leakedFrom(err) {
    const raw = (err?.stderr || err?.stdout || String(err)).toString();
    const line = raw.split('\n').map((l) => l.trim()).find((l) => l.startsWith('DOCSTATS '));
    if (!line) return 0;
    try {
        return JSON.parse(line.slice('DOCSTATS '.length)).liveDocuments ?? 0;
    } catch {
        return 0;
    }
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

        // popular -> detail -> pageList, because that is the journey a reader actually makes
        // and only the first leg was ever swept. getPageList is the leg that matters most: it is
        // where a chapter's images come from, and where the 32-handle document pool is pushed
        // hardest, so a leak or a selector fault there is invisible to a getPopular-only sweep.
        const invoke = (method, arg) => {
            const out = execFileSync(
                'node',
                [path.join(HARNESS_DIR, 'run.mjs'), scriptPath, configPath, method, String(arg)],
                { timeout: 90000, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] },
            );
            const parsed = JSON.parse(out);
            // Carry preference writes into the next leg. Each leg is its own process, so without
            // this the run loses them at the process boundary — and the app does not: the sidecar
            // hands `changedPreferences` back and they are persisted. Sources routinely resolve a
            // mirror or base URL during the listing and read it back when fetching chapters, so a
            // harness that drops the write exercises a path the app never takes.
            if (parsed.preferencesAfter && Object.keys(parsed.preferencesAfter).length > 0) {
                const current = JSON.parse(fs.readFileSync(configPath, 'utf8'));
                current.preferences = parsed.preferencesAfter;
                fs.writeFileSync(configPath, JSON.stringify(current));
            }
            return parsed;
        };
        // Sources disagree on which field carries the URL: the Mangayomi shape is `link`, but
        // several emit `url`. Taking either is the harness's job, not the source's.
        const refOf = (item) => item?.link ?? item?.url ?? null;

        const popular = invoke('getPopular', 1);
        const list = popular.result?.list ?? [];
        let peakDocs = popular.documentStats.peakLiveDocuments;
        let liveLeaked = popular.documentStats.liveDocuments;

        let detailStatus = '-';
        let pagesStatus = '-';
        let pages = 0;

        // Each later leg is attempted only if the previous produced something to feed it, and a
        // throw in one leg is recorded rather than failing the row outright — a source can list
        // fine and still break on chapters, which is exactly the split worth seeing.
        const firstRef = refOf(list[0]);
        if (firstRef) {
            try {
                const detail = invoke('getDetail', firstRef);
                peakDocs = Math.max(peakDocs, detail.documentStats.peakLiveDocuments);
                liveLeaked += detail.documentStats.liveDocuments;
                const chapters = detail.result?.chapters ?? [];
                detailStatus = chapters.length > 0 ? `${chapters.length}ch` : 'no chapters';

                const chapterRef = refOf(chapters[0]);
                if (chapterRef) {
                    try {
                        const pageList = invoke('getPageList', chapterRef);
                        peakDocs = Math.max(peakDocs, pageList.documentStats.peakLiveDocuments);
                        liveLeaked += pageList.documentStats.liveDocuments;
                        const p = pageList.result;
                        pages = Array.isArray(p) ? p.length : (p?.length ?? 0);
                        pagesStatus = pages > 0 ? `${pages}pg` : 'no pages';
                    } catch (pageErr) {
                        pagesStatus = 'THREW';
                        liveLeaked += leakedFrom(pageErr);
                    }
                }
            } catch (detailErr) {
                detailStatus = 'THREW';
                liveLeaked += leakedFrom(detailErr);
            }
        }

        results.push({
            name: e.name,
            status: list.length > 0 ? 'OK' : 'empty list',
            items: list.length,
            peakDocs,
            liveLeaked,
            detail: detailStatus,
            pages: pagesStatus,
            sample: list[0]?.name?.slice(0, 24),
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
        const liveLeaked = leakedFrom(err);
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
    console.log(`[${tag}] ${r.name.slice(0, 22).padEnd(22)} ${String(r.status).padEnd(11)}` +
        (r.items !== undefined
            ? ` items=${String(r.items).padEnd(3)} detail=${String(r.detail).padEnd(11)}` +
              ` pages=${String(r.pages).padEnd(7)} peak=${r.peakDocs} leaked=${r.liveLeaked} ${r.sample ?? ''}`
            : ` ${r.error ?? ''}`));
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
