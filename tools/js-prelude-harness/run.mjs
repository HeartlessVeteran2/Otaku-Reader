/*
 * The kill-criterion check: does a real, unmodified Mangayomi extension run against prelude.js?
 *
 * Mirrors QuickJsHost.call end to end — install host globals, evaluate the source config global,
 * evaluate the prelude, evaluate the extension script, then run the same invocation
 * buildInvocation() produces.
 */
import fs from 'node:fs';
import vm from 'node:vm';
import { installHost } from './host.mjs';

const PRELUDE = '/home/user/Otaku-Reader/core/js-runtime/src/main/resources/js/prelude.js';

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

const sandbox = { console, URL, TextEncoder, TextDecoder, setTimeout, clearTimeout, fetch };
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
const call = {
    getPopular: `getPopular(${argRaw || 1})`,
    getLatestUpdates: `getLatestUpdates(${argRaw || 1})`,
    getDetail: `getDetail(${JSON.stringify(argRaw || '')})`,
    getPageList: `getPageList(${JSON.stringify(argRaw || '')})`,
    getFilterList: `getFilterList()`,
}[method];

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
