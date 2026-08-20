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

// Bounds on a single source request. Neither exists to be tuned — they exist so that one
// misbehaving site cannot stall or exhaust a sweep of dozens of sources. A server that accepts the
// connection and then dribbles bytes forever would otherwise hang the run with no output at all,
// and a source pointed at a video or a multi-gigabyte file would be read entirely into a string
// before anything noticed.
const REQUEST_TIMEOUT_MS = 30_000;
const MAX_RESPONSE_BYTES = 32 * 1024 * 1024;

let requestCount = 0;
async function onRequest({ url, method: verb, headers, body }) {
    requestCount++;
    try {
        const res = await fetch(url, {
            method: verb,
            headers,
            body: body ?? undefined,
            redirect: 'follow',
            signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
        });
        // Read incrementally rather than via res.text(): the cap has to be enforced while the body
        // is arriving, since by the time text() resolves the whole thing is already in memory.
        const chunks = [];
        let total = 0;
        for await (const chunk of res.body ?? []) {
            total += chunk.length;
            if (total > MAX_RESPONSE_BYTES) {
                return {
                    ok: false,
                    code: res.status,
                    headers: Object.fromEntries(res.headers.entries()),
                    body: '',
                    error: `response exceeded ${MAX_RESPONSE_BYTES} bytes`,
                };
            }
            chunks.push(chunk);
        }
        return {
            ok: res.ok,
            code: res.status,
            headers: Object.fromEntries(res.headers.entries()),
            body: Buffer.concat(chunks).toString('utf8'),
        };
    } catch (e) {
        return { ok: false, code: 0, headers: {}, body: '', error: String(e) };
    }
}

// Deliberately spare. QuickJsHost's own header states that "capability arrives solely through the
// globals installed below", and it installs exactly three: Client, Document, SharedPreferences.
// Anything handed out here that Android does not have — `fetch`, and timers, which a source would
// reach for to throttle or retry — makes the harness report an extension as compatible when it
// cannot run on the device, and that is the one verdict this tool must never give. Being stricter
// than the device is the safe direction: it costs a false failure, which a human then checks.
const sandbox = { console, URL, TextEncoder, TextDecoder };
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
