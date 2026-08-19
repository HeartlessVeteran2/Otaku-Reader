/*
 * The kill-criterion check: does a real, unmodified Mangayomi extension run against prelude.js?
 *
 * Mirrors QuickJsHost.call end to end — install host globals, evaluate the source config global,
 * evaluate the prelude, evaluate the extension script, then run the same invocation
 * buildInvocation() produces.
 */
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import { installHost } from './host.mjs';

// Resolved from this file's own location, never from the working directory or an absolute path.
// The harness is checked in for other people to run, so anything anchored to one machine's
// checkout fails with ENOENT before a single extension is exercised.
const HARNESS_DIR = path.dirname(fileURLToPath(import.meta.url));
const PRELUDE = process.env.OTAKU_PRELUDE ??
    path.resolve(HARNESS_DIR, '../../core/js-runtime/src/main/resources/js/prelude.js');

const [, , scriptPath, configPath, method, argRaw] = process.argv;
const script = fs.readFileSync(scriptPath, 'utf8');
const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));

let requestCount = 0;
async function onRequest({ url, method: verb, headers, body }) {
    requestCount++;
    try {
        const res = await fetch(url, {
            method: verb,
            headers,
            body: body ?? undefined,
            redirect: 'follow',
        });
        const text = await res.text();
        return {
            ok: res.ok,
            code: res.status,
            headers: Object.fromEntries(res.headers.entries()),
            body: text,
        };
    } catch (e) {
        return { ok: false, code: 0, headers: {}, body: '', error: String(e) };
    }
}

// No `fetch` in the sandbox. The Android QuickJS context provides none, so a source reaching for
// it must fail here too — otherwise the harness reports an extension as compatible that cannot run
// on the device, which is the one verdict it must never give.
const sandbox = { console, URL, TextEncoder, TextDecoder, setTimeout, clearTimeout };
sandbox.globalThis = sandbox;
const context = vm.createContext(sandbox);

const probe = installHost(sandbox, { baseUrl: config.baseUrl, preferences: config.preferences || {}, onRequest });

// Same order as QuickJsHost.call: host globals, then config, then prelude, then the script.
vm.runInContext(`globalThis.__otakuSourceConfig = ${JSON.stringify(JSON.stringify(config))};`, context);
vm.runInContext(fs.readFileSync(PRELUDE, 'utf8'), context, { filename: 'prelude.js' });

try {
    vm.runInContext(script, context, { filename: scriptPath });
} catch (e) {
    console.error(`SCRIPT EVALUATION FAILED: ${e}`);
    process.exit(2);
}

// The invocation buildInvocation() emits.
// Mirrors QuickJsHost.buildInvocation exactly — every method it dispatches, with the same
// argument order. An entry missing here silently invokes `provider.undefined`.
const call = {
    getPopular: `getPopular(${Number(argRaw) || 1})`,
    getLatestUpdates: `getLatestUpdates(${Number(argRaw) || 1})`,
    search: `search(${JSON.stringify(argRaw || '')}, 1, [])`,
    getDetail: `getDetail(${JSON.stringify(argRaw || '')})`,
    getPageList: `getPageList(${JSON.stringify(argRaw || '')})`,
    getHtmlContent: `getHtmlContent(${JSON.stringify(argRaw || '')})`,
    getFilterList: `getFilterList()`,
}[method];

if (!call) {
    console.error(`Unknown method "${method}". Supported: getPopular, getLatestUpdates, search, ` +
        `getDetail, getPageList, getHtmlContent, getFilterList`);
    process.exit(4);
}

const invocation = `
    (async () => {
        const provider = new DefaultExtension();
        const result = await provider.${call};
        __otakuEmitResult(JSON.stringify(result === undefined ? null : result));
    })()
`;

let captured = null;
sandbox.__otakuEmitResult = (json) => { captured = json; };

try {
    await vm.runInContext(invocation, context, { filename: 'invocation' });
} catch (e) {
    console.error(`INVOCATION FAILED: ${e && e.stack ? e.stack : e}`);
    process.exit(3);
}

console.log(JSON.stringify({
    method,
    requests: requestCount,
    documentStats: probe.stats(),
    preferencesAfter: probe.preferences(),
    result: captured ? JSON.parse(captured) : null,
}, null, 1));
