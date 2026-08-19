/*
 * Mangayomi compatibility prelude.
 *
 * Evaluated by QuickJsHost immediately before every extension script, in the same context.
 *
 * ### Why this file exists
 *
 * Community JavaScript sources are published against the Mangayomi runtime, which hands scripts
 * an object-oriented API: `new Client()`, `new Document(html)`, `new SharedPreferences()`, and a
 * `MProvider` base class that every extension extends. QuickJsHost deliberately installs
 * something different — flat namespaces whose arguments and returns are all primitives, so no
 * host object reference ever crosses into JavaScript.
 *
 * Both designs are right for their own reasons, and this file is the adapter between them. It
 * runs entirely in JavaScript, so the Kotlin boundary keeps passing primitives only and the
 * isolation property QuickJsHost documents is preserved exactly: the element objects below wrap
 * strings and integers that the host produced, never a Jsoup node.
 *
 * The alternative — teaching the Kotlin bindings to hand out objects — would have traded that
 * property away for the same result.
 *
 * ### The one rule to preserve when editing
 *
 * `Document.parse` allocates a handle from a pool capped at MAX_LIVE_DOCUMENTS (32), and the host
 * *refuses* rather than evicts once the cap is hit. A selector call on an element therefore parses
 * and releases inside the same function, in a `finally`. Holding a handle across an `await` — or
 * forgetting the `finally` on a throwing path — turns a page of 20 results into a hard failure
 * that only appears against real-sized data.
 */
(function (global) {
    'use strict';

    // The host bindings, captured before the classes below shadow them. After this point the
    // names `Client`, `Document` and `SharedPreferences` refer to the compatibility classes, so
    // these locals are the only remaining way to reach the host.
    var hostClient = global.Client;
    var hostDocument = global.Document;
    var hostPreferences = global.SharedPreferences;

    // Injected by QuickJsHost as a JS string literal holding JSON, so exactly one parse is
    // correct: the JavaScript parser already removed the source-level quoting while evaluating
    // the assignment. Parsing twice throws.
    //
    // Extensions read `this.source.baseUrl`, `this.source.apiUrl` and `this.source.lang`
    // constantly, so an absent config is a broken source rather than a degraded one.
    var sourceConfig = global.__otakuSourceConfig
        ? JSON.parse(global.__otakuSourceConfig)
        : {};

    // ---------------------------------------------------------------------------------------
    // HTTP
    // ---------------------------------------------------------------------------------------

    /**
     * The response shape Mangayomi extensions expect.
     *
     * `statusCode` rather than the host's `code`: extensions branch on `res.statusCode`, and a
     * missing property would read as `undefined` and compare falsely against every number
     * instead of failing loudly.
     */
    function MResponse(raw) {
        this.body = typeof raw.body === 'string' ? raw.body : '';
        this.statusCode = typeof raw.code === 'number' ? raw.code : 0;
        this.headers = raw.headers || {};
        this.ok = !!raw.ok;
        this.error = raw.error === undefined ? null : raw.error;
    }

    function decodeResponse(payload) {
        // The host returns the serialized JsHttpResponse. A non-JSON body would mean the bridge
        // itself failed, which is not something a source can handle, so let the parse throw.
        return new MResponse(JSON.parse(payload));
    }

    function MClient() {
        // Mangayomi's constructor accepts an options bag that only configures redirect and
        // cookie behaviour. Both are owned by the app's shared OkHttp client here — that is the
        // entire point of routing source traffic through it — so the argument is accepted and
        // ignored rather than rejected, which would fail sources that pass one out of habit.
    }

    MClient.prototype.get = function (url, headers) {
        return hostClient.get(url, headers || {}).then(decodeResponse);
    };

    MClient.prototype.post = function (url, headers, body) {
        // Serialise object bodies. The host binding reads the body with `as? String`, so a JS
        // object arrives as a Map, fails the cast, and becomes null — the request then goes out
        // with no payload at all and the source sees an unexplained empty response. Sources that
        // already encoded their body pass a string, which is forwarded untouched.
        var payload = body === undefined || body === null ? null
            : (typeof body === 'string' ? body : JSON.stringify(body));
        return hostClient.post(url, headers || {}, payload).then(decodeResponse);
    };

    /** Some sources build a request descriptor instead of calling get/post directly. */
    MClient.prototype.request = function (options) {
        var opts = options || {};
        var method = (opts.method || 'GET').toUpperCase();
        if (method === 'POST') {
            return this.post(opts.url, opts.headers, opts.body);
        }
        return this.get(opts.url, opts.headers);
    };

    // ---------------------------------------------------------------------------------------
    // HTML
    // ---------------------------------------------------------------------------------------

    /**
     * One node, represented by its outer HTML.
     *
     * A document and an element are the same type here, because the host's selector functions
     * take HTML in and give HTML back — there is no separate document identity to model. That
     * also makes `new Document(html)` and the result of `selectFirst` interchangeable, which is
     * what extensions assume when they pass a selected node to a helper that selects again.
     */
    function MElement(html) {
        this.html = typeof html === 'string' ? html : '';
    }

    /**
     * Run `fn` against a freshly parsed handle and release it before returning.
     *
     * The `finally` is load-bearing: a selector that throws must still release, or the handle
     * pool leaks one slot per failure and the source dies at the 32nd with an error naming
     * documents rather than the selector that actually broke.
     */
    function withHandle(html, fn) {
        var handle = hostDocument.parse(html);
        try {
            return fn(handle);
        } finally {
            hostDocument.release(handle);
        }
    }

    MElement.prototype.select = function (selector) {
        var found = withHandle(this.html, function (handle) {
            return hostDocument.select(handle, selector);
        });
        var out = [];
        for (var i = 0; i < found.length; i++) {
            out.push(new MElement(found[i]));
        }
        return out;
    };

    MElement.prototype.selectFirst = function (selector) {
        var found = withHandle(this.html, function (handle) {
            return hostDocument.selectFirst(handle, selector);
        });
        // An empty element on a miss, never null.
        //
        // An earlier version returned null, reasoning that sources guard with
        // `el.selectFirst(s)?.text`. Measured against the published sources, that is the minority:
        // 38 call sites read a property straight off the result with no optional chain, against 17
        // that guard. Returning null makes a missed selector throw and take the whole source down,
        // where an empty element degrades to an empty string — and a source that fails to find one
        // optional field should not stop returning the other twenty.
        //
        // The cost is that `?? fallback` after a miss now sees "" rather than nullish and keeps
        // the empty string. That is the lesser failure, and it matches what sources are written
        // against.
        return new MElement(found === null || found === undefined ? '' : found);
    };

    MElement.prototype.attr = function (name) {
        // Raw, as written. Mangayomi's `attr` is not URL-aware; `getHref`/`getSrc` are.
        return hostDocument.attr(this.html, name);
    };

    /** The attribute resolved against the source's base URL. */
    MElement.prototype.absAttr = function (name) {
        return hostDocument.absAttr(this.html, name);
    };

    MElement.prototype.getAttribute = function (name) {
        return this.attr(name);
    };

    MElement.prototype.selectAll = function (selector) {
        return this.select(selector);
    };

    MElement.prototype.toString = function () {
        return this.html;
    };

    Object.defineProperties(MElement.prototype, {
        // `text`, `html` and `outerHtml` are properties, not methods — verified against the
        // published sources, which write `el.selectFirst(s)?.text` with no call parentheses.
        // Defining them as functions would silently yield a function object that stringifies to
        // its own source code, which is the kind of failure that reaches a user as garbled titles
        // rather than an error.
        text: {
            get: function () {
                return hostDocument.text(this.html);
            }
        },
        outerHtml: {
            get: function () {
                return this.html;
            }
        },
        // No `innerHtml`. An element here is represented by the outer HTML the host returned, so
        // the obvious implementation returns the wrapping tag as well as its contents — an
        // extension asking for a node's contents would silently receive malformed markup, which
        // is worse than the property being absent, because absence throws where it is used.
        // Nothing in the published sources sampled for this layer reads it. If one does, give it
        // a real host binding rather than approximating it here.
        getHref: {
            get: function () {
                return this.absAttr('href');
            }
        },
        getSrc: {
            get: function () {
                return this.absAttr('src');
            }
        },
        /**
         * Attributes of this node's opening tag.
         *
         * Read off the tag text rather than from Jsoup, because the host exposes attributes only
         * one name at a time and adding a bulk binding would mean a Kotlin change for something
         * the published sources use almost exclusively on JSON payloads rather than on DOM nodes.
         *
         * Values are returned raw. Unlike `attr()`, which resolves against the source base URL,
         * a relative href read from here stays relative — use `attr('href')` when an absolute URL
         * is what is wanted.
         */
        attributes: {
            get: function () {
                var out = {};
                var openingTag = /<[a-zA-Z][^\s/>]*((?:\s+[^\s=/>]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s"'>]+))?)*)/.exec(this.html);
                if (!openingTag) {
                    return out;
                }
                var pattern = /([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?/g;
                var match;
                while ((match = pattern.exec(openingTag[1])) !== null) {
                    var value = match[2];
                    if (value === undefined) { value = match[3]; }
                    if (value === undefined) { value = match[4]; }
                    out[match[1]] = value === undefined ? '' : value;
                }
                return out;
            }
        }
    });

    // ---------------------------------------------------------------------------------------
    // Preferences
    // ---------------------------------------------------------------------------------------

    /**
     * The most recently constructed provider, used only to reach `getSourcePreferences()`.
     *
     * Set by the MProvider constructor, which the invocation runs before it calls any method, so
     * this is populated by the time a source can ask for a preference.
     */
    var currentProvider = null;
    var declaredDefaults = null;

    /**
     * Default values the extension declares for its own preferences.
     *
     * Sources routinely read a preference the user has never set — the near-universal case is a
     * mirror or base-URL override, `new SharedPreferences().get("overrideBaseUrl1")` — and the
     * extension supplies the working value as that preference's declared default. Without this,
     * such a source builds every request against `null/...` and fails completely at browse time
     * rather than degrading.
     *
     * Resolved lazily and cached: `getSourcePreferences()` is a plain constructor of literals, but
     * it is called once per preference read otherwise, and a source can read one per result row.
     *
     * A stored value always wins over a declared one. That ordering is what makes a source's
     * updated default — a moved mirror domain, say — reach a user who never touched the setting,
     * while still never overriding a choice the user did make.
     */
    function defaultFor(key) {
        if (declaredDefaults === null) {
            declaredDefaults = {};
            try {
                var declared = currentProvider && typeof currentProvider.getSourcePreferences === 'function'
                    ? currentProvider.getSourcePreferences()
                    : [];
                for (var i = 0; i < declared.length; i++) {
                    var entry = declared[i];
                    if (!entry || !entry.key) { continue; }
                    var edit = entry.editTextPreference;
                    var list = entry.listPreference;
                    var multi = entry.multiSelectListPreference;
                    var toggle = entry.switchPreferenceCompat || entry.checkBoxPreference;
                    if (edit) {
                        declaredDefaults[entry.key] = edit.value;
                    } else if (list) {
                        // The default is an index into entryValues, not a value itself.
                        var values = list.entryValues || [];
                        var chosen = typeof list.valueIndex === 'number' ? list.valueIndex : 0;
                        declaredDefaults[entry.key] = list.value !== undefined ? list.value : values[chosen];
                    } else if (multi) {
                        declaredDefaults[entry.key] = multi.values || [];
                    } else if (toggle) {
                        declaredDefaults[entry.key] = toggle.value;
                    }
                }
            } catch (e) {
                // A source whose preference declaration throws still has to be usable for
                // everything that does not depend on a preference.
                declaredDefaults = {};
            }
        }
        return Object.prototype.hasOwnProperty.call(declaredDefaults, key)
            ? declaredDefaults[key]
            : undefined;
    }

    function MSharedPreferences() {}

    /**
     * Read a stored preference, falling back to `defaultValue`.
     *
     * The host stores strings only. When the caller's default is an array or object the stored
     * text is JSON-decoded, because sources keep multi-select preferences — enabled languages,
     * selected categories — as lists and then call `.join()` on what comes back. A decode failure
     * yields the default rather than throwing: a corrupt preference should degrade the source to
     * its defaults, not make it unusable with no way for the user to reset it.
     */
    MSharedPreferences.prototype.get = function (key, defaultValue) {
        var stored = hostPreferences.get(key);
        if (stored === null || stored === undefined) {
            // Nothing stored: fall back to what the extension declared, then to what the caller
            // asked for. The declared value comes first because it is the source's own statement
            // of what the setting means when untouched, whereas the caller's argument is usually
            // just a type hint written at the call site.
            var declared = defaultFor(key);
            if (declared !== undefined) {
                return declared;
            }
            return defaultValue === undefined ? null : defaultValue;
        }
        if (defaultValue !== null && typeof defaultValue === 'object') {
            try {
                return JSON.parse(stored);
            } catch (e) {
                return defaultValue;
            }
        }
        if (typeof defaultValue === 'boolean') {
            return stored === 'true';
        }
        if (typeof defaultValue === 'number') {
            var parsed = Number(stored);
            return isNaN(parsed) ? defaultValue : parsed;
        }
        return stored;
    };

    MSharedPreferences.prototype.set = function (key, value) {
        // Nullish writes store an empty string, mirroring the Kotlin binding's
        // `?.toString().orEmpty()`. `String(null)` would persist the four characters "null", which
        // then reads back as a set value and permanently shadows both the extension's declared
        // default and the caller's — a source writing a not-yet-computed value would poison that
        // preference for good.
        var stored = value === null || value === undefined ? ''
            : (typeof value === 'object' ? JSON.stringify(value) : String(value));
        hostPreferences.set(key, stored);
    };

    MSharedPreferences.prototype.getString = function (key, defaultValue) {
        return this.get(key, defaultValue === undefined ? '' : defaultValue);
    };
    MSharedPreferences.prototype.getBool = function (key, defaultValue) {
        return this.get(key, defaultValue === undefined ? false : defaultValue);
    };
    MSharedPreferences.prototype.getInt = function (key, defaultValue) {
        return this.get(key, defaultValue === undefined ? 0 : defaultValue);
    };
    MSharedPreferences.prototype.getStringList = function (key, defaultValue) {
        return this.get(key, defaultValue === undefined ? [] : defaultValue);
    };
    MSharedPreferences.prototype.setString = function (key, value) { this.set(key, value); };
    MSharedPreferences.prototype.setBool = function (key, value) { this.set(key, value); };
    MSharedPreferences.prototype.setInt = function (key, value) { this.set(key, value); };
    MSharedPreferences.prototype.setStringList = function (key, value) { this.set(key, value); };

    // ---------------------------------------------------------------------------------------
    // The base class every extension extends
    // ---------------------------------------------------------------------------------------

    /**
     * `class DefaultExtension extends MProvider` is the published contract, so this name must
     * exist before the extension script is evaluated — not before it is *called*. A missing
     * `MProvider` fails at class-definition time with a ReferenceError, which is why the prelude
     * is evaluated ahead of the script rather than folded into the invocation.
     */
    function MProvider() {
        this.source = sourceConfig;
        this.client = new MClient();
        this.preferences = new MSharedPreferences();
        // Publish this instance so `new SharedPreferences()` — which sources construct standalone,
        // with no reference to the provider — can still reach the declared preference defaults.
        currentProvider = this;
        declaredDefaults = null;
    }

    MProvider.prototype.getPreference = function (key, defaultValue) {
        return this.preferences.get(key, defaultValue);
    };

    MProvider.prototype.setPreference = function (key, value) {
        this.preferences.set(key, value);
    };

    /**
     * Default request headers.
     *
     * Most sources override this. The base returns an empty object rather than inventing a
     * User-Agent, because the app's shared OkHttp client already sets one and a value chosen here
     * would silently override it for exactly the sources that did not ask for anything.
     */
    MProvider.prototype.getHeaders = function () {
        return {};
    };

    MProvider.prototype.getBaseUrl = function () {
        return sourceConfig.baseUrl || '';
    };

    /** Sources that expose no filters still get called for them; an empty list is the answer. */
    MProvider.prototype.getFilterList = function () {
        return [];
    };

    MProvider.prototype.getSourcePreferences = function () {
        return [];
    };

    // ---------------------------------------------------------------------------------------
    // Publish
    // ---------------------------------------------------------------------------------------

    global.Client = MClient;
    global.Document = MElement;
    global.Element = MElement;
    global.SharedPreferences = MSharedPreferences;
    global.MProvider = MProvider;
    global.MResponse = MResponse;
})(globalThis);
