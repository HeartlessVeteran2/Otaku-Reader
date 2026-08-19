/* Run getPopular for many real JS extensions and report which survive the prelude. */
import fs from 'node:fs';
import { execFileSync } from 'node:child_process';

const index = JSON.parse(fs.readFileSync('../idx.json', 'utf8'));
const candidates = index.filter((e) => e.sourceCodeLanguage === 1 && e.itemType === 0);

const wanted = Number(process.argv[2] || 12);
const picked = [];
for (const e of candidates) {
    if (picked.length >= wanted) break;
    if (picked.some((p) => p.name === e.name)) continue;
    picked.push(e);
}

const results = [];
for (const e of picked) {
    const slug = String(e.id);
    const scriptPath = `tmp_${slug}.js`;
    const configPath = `tmp_${slug}.json`;
    try {
        const res = await fetch(e.sourceCodeUrl);
        if (!res.ok) { results.push({ name: e.name, status: `script ${res.status}` }); continue; }
        const body = await res.text();
        if (body.length < 200) { results.push({ name: e.name, status: 'script too small' }); continue; }
        fs.writeFileSync(scriptPath, body);
        fs.writeFileSync(configPath, JSON.stringify({
            id: slug, name: e.name, baseUrl: e.baseUrl, apiUrl: e.apiUrl || '',
            lang: e.lang, isNsfw: !!e.isNsfw, preferences: {},
        }));

        const out = execFileSync('node', ['run.mjs', scriptPath, configPath, 'getPopular', '1'],
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
        const msg = (err.stderr || err.stdout || String(err)).toString().toString().split('\n').find(l=>l.includes('FAILED'));
        results.push({ name: e.name, status: 'FAIL', error: msg?.slice(0, 130) });
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
