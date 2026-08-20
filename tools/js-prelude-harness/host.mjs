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

    /**
     * Mirror QuickJsHost's header handling: drop null/undefined entries, stringify the rest.
     *
     * Not cosmetic. Sources build header maps from preferences that are frequently unset —
     * `{"user-agent": this.getPreference("custom_user_agent")}` is the common shape — and passing
     * the null through would send a literal `User-Agent: null` here while Android sends no header
     * at all. The entire value of this harness is that a source behaves the same in both, so any
     * divergence makes a green run meaningless.
     */
    const normalizeHeaders = (headers) => {
        const out = {};
        for (const [k, v] of Object.entries(headers || {})) {
            if (v === null || v === undefined) continue;
            out[k] = String(v);
        }
        return out;
    };

    // --- Client: engine.define("Client") { asyncFunction("get"/"post") } ---
    global.Client = {
        async get(url, headers) {
            return JSON.stringify(await onRequest({ url, method: 'GET', headers: normalizeHeaders(headers), body: null }));
        },
        async post(url, headers, body) {
            return JSON.stringify(await onRequest({ url, method: 'POST', headers: normalizeHeaders(headers), body }));
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
            // Jsoup's Element.text() collapses whitespace runs, trims, and puts a boundary between
            // block-level elements; cheerio's .text() concatenates raw text nodes, so
            // `<p>a</p><p>b</p>` yields "ab" there and "a b" on Android. Insert a separator at
            // block boundaries and collapse, which covers the cases sources actually depend on
            // (multi-line titles, chapter names split across tags).
            //
            // This is an approximation of Jsoup, not a reimplementation — exotic inline/block
            // nesting can still differ. It is close enough that a source parsing correctly here is
            // strong evidence, and the Android run remains the authority.
            const $ = cheerio.load(html ?? '');
            $('br').replaceWith(' ');
            $('p,div,li,tr,td,th,h1,h2,h3,h4,h5,h6,section,article,blockquote').each((_, el) => {
                $(el).after(' ');
            });
            return $.root().text().replace(/\s+/g, ' ').trim();
        },
        attr(html, name) {
            const $ = cheerio.load(html ?? '');
            const el = $('body').children().first();
            if (!el.length) return '';
            // Raw, matching the Kotlin `attr` binding. Resolution lives in absAttr.
            return el.attr(name) ?? '';
        },
        absAttr(html, name) {
            const $ = cheerio.load(html ?? '');
            const el = $('body').children().first();
            if (!el.length) return '';
            const raw = el.attr(name);
            if (raw === undefined) return '';
            return absUrl(raw) || raw;
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
            // Mirrors the Kotlin binding's `?.toString().orEmpty()`: a nullish write stores an
            // empty string, not the literal "null".
            //
            // The prelude never sends one — it drops nullish writes before they reach here, and
            // says why. This stays as the binding's own floor, and keeping it identical to the
            // Kotlin is the point of the file: the harness must not be the reason a behaviour
            // looks correct.
            prefs[key] = value === null || value === undefined ? '' : String(value);
            return null;
        },
    };

    return {
        stats: () => ({ liveDocuments: documents.size, peakLiveDocuments }),
        preferences: () => ({ ...prefs }),
    };
}
