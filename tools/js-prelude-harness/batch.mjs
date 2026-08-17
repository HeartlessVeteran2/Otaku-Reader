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
        for (const f of [scriptPath, configPath]) { try { fs.unlinkSync(f); } catch {} }
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
