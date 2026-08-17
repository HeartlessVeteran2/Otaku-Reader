# JavaScript prelude harness

Runs real, unmodified Mangayomi extensions against
`core/js-runtime/src/main/resources/js/prelude.js` — outside Android, on Node.

## Why this exists

The prelude is the compatibility layer between the object-oriented API community JavaScript
sources are written against (`new Client()`, `new Document(html)`, `class DefaultExtension extends
MProvider`) and the flat, primitives-only bindings `QuickJsHost` installs.

It cannot be unit-tested from the JVM: QuickJS ships as an Android artifact, so there is no engine
to evaluate it in. `JsPreludeTest` therefore only checks that the file is packaged and still
publishes the right globals — it cannot tell you whether a real source *works*.

This harness can. `host.mjs` reimplements every `QuickJsHost` binding with the same argument
order, the same return types, the same handle discipline and the same 32-document cap, backed by
cheerio instead of Jsoup. `run.mjs` then reproduces `QuickJsHost.call` exactly: host globals →
source config global → prelude → extension script → the invocation `buildInvocation()` emits.

It is deliberately **not** a Gradle module and never runs in CI: it needs live network and real
third-party sites, so it fails for reasons that have nothing to do with this repository.

## Use

```bash
cd tools/js-prelude-harness
npm install

# Fetch the index once.
curl -s -o idx.json https://kodjodevf.github.io/mangayomi-extensions/index.json

# One extension, one method.
node run.mjs <script.js> <config.json> getPopular 1
node run.mjs <script.js> <config.json> getDetail "/manga/<id>"

# Or sweep the first N JavaScript sources in the index.
node batch.mjs 14
```

`config.json` mirrors `JsSourceConfig`:

```json
{ "id": "810342358", "name": "MangaDex", "baseUrl": "https://mangadex.org",
  "apiUrl": "https://api.mangadex.org", "lang": "en", "isNsfw": false, "preferences": {} }
```

## Reading the output

`batch.mjs` prints `peakDocs` and `leaked` per source. `leaked` must always be `0` — a non-zero
value means a selector path returned without releasing its handle, which only becomes a visible
failure against a page with enough rows to exhaust the 32-handle pool.

**Most failures are not the prelude.** In the sweep that validated this layer, every hard failure
traced to something external: a Cloudflare interstitial, a site that had moved domain since its
extension was published (leaving a stale declared `overrideBaseUrl1` default), an upstream error
from the site itself, and one host blocked by the local egress proxy. Check the site with `curl`
before suspecting this code.

Two real defects *were* found this way, which is the argument for keeping the harness:

- extensions read preferences the user has never set (`new SharedPreferences().get("overrideBaseUrl1")`),
  so the prelude has to fall back to the default the extension declares in `getSourcePreferences()`;
- a preference with no value produced a literal `User-Agent: null` header, because the Kotlin
  binding stringified a null rather than dropping the entry.
