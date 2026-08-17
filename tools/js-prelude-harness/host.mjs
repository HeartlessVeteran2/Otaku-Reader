/*
 * A faithful stand-in for QuickJsHost's bindings, so prelude.js can be exercised outside Android.
 *
 * Every function here mirrors the Kotlin binding it is named after: same argument order, same
 * return type, same handle discipline, same cap. Where Kotlin uses Jsoup, this uses cheerio.
 * If the prelude works against this, the only thing left to differ is Jsoup vs cheerio selector
 * behaviour — not the shape of the API.
 */
import * as cheerio from 'cheerio';

const MAX_LIVE_DOCUMENTS = 32;

export function installHost(global, { baseUrl, preferences = {}, onRequest }) {
    const documents = new Map();
    let nextHandle = 1;
    let peakLiveDocuments = 0;
    const prefs = { ...preferences };

    // --- Client: engine.define("Client") { asyncFunction("get"/"post") } ---
    global.Client = {
        async get(url, headers) {
            return JSON.stringify(await onRequest({ url, method: 'GET', headers: headers || {}, body: null }));
        },
        async post(url, headers, body) {
            return JSON.stringify(await onRequest({ url, method: 'POST', headers: headers || {}, body }));
        },
    };

    // --- Document: handle-based, returns outerHtml strings ---
    const absUrl = (value) => {
        if (!value) return '';
        try { return new URL(value, baseUrl).toString(); } catch { return value; }
    };

    global.Document = {
        parse(html) {
            if (documents.size >= MAX_LIVE_DOCUMENTS) {
                throw new Error(`Source holds more than ${MAX_LIVE_DOCUMENTS} parsed documents; release them`);
            }
            const handle = nextHandle++;
            documents.set(handle, cheerio.load(html ?? ''));
            peakLiveDocuments = Math.max(peakLiveDocuments, documents.size);
            return handle;
        },
        select(handle, selector) {
            const $ = documents.get(handle);
            if (!$) return [];
            return $(selector).toArray().map((el) => $.html(el));
        },
        selectFirst(handle, selector) {
            const $ = documents.get(handle);
            if (!$) return null;
            const found = $(selector).first();
            return found.length ? $.html(found) : null;
        },
        text(html) {
            return cheerio.load(html ?? '').root().text();
        },
        attr(html, name) {
            const $ = cheerio.load(html ?? '');
            const el = $('body').children().first();
            if (!el.length) return '';
            const raw = el.attr(name);
            if (raw === undefined) return '';
            // Kotlin prefers absUrl and falls back to the raw attribute.
            return (name === 'href' || name === 'src') ? absUrl(raw) : raw;
        },
        release(handle) {
            documents.delete(handle);
            return null;
        },
    };

    // --- SharedPreferences ---
    global.SharedPreferences = {
        get(key) {
            return Object.prototype.hasOwnProperty.call(prefs, key) ? prefs[key] : null;
        },
        set(key, value) {
            prefs[key] = String(value);
            return null;
        },
    };

    return {
        stats: () => ({ liveDocuments: documents.size, peakLiveDocuments }),
        preferences: () => ({ ...prefs }),
    };
}
