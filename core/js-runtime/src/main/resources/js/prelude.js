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

    function decodeResponse(payload, method, url) {
        // The host returns the serialized JsHttpResponse. A non-JSON body would mean the bridge
        // itself failed, which is not something a source can handle, so let the parse throw.
        var raw = JSON.parse(payload);

        // No HTTP response was obtained at all — throw instead of handing back an empty body.
        //
        // `code` is the discriminator, and it is exact: every JsHttpBridge path that never
        // reached a server (transport exception, malformed URL, refused non-HTTPS, refused
        // private address, too many redirects, unsupported method) leaves `code` at its default
        // 0, while every path that did get a response carries the real status — including the
        // ones that then reject it, like a refused redirect or an oversized body. So a source
        // branching on `statusCode` for a genuine 404 or 403 is unaffected by this.
        //
        // Without it the failure surfaces as whatever the source does with `body === ''`, which
        // is almost always `JSON.parse('')` -> "Unexpected end of JSON input". That names
        // neither the URL nor the cause, and the bridge's `error` string — "Refused request to
        // private address", "Request failed" — is discarded on the floor. Throwing keeps it, and
        // a source that already guards its requests can still catch it.
        if (raw.ok === false && (raw.code === 0 || raw.code === undefined)) {
            throw new Error(
                (method || 'GET') + ' ' + (url || '<unknown url>') + ' failed: ' +
                    (raw.error || 'no response')
            );
        }
        return new MResponse(raw);
    }

    function MClient() {
        // Mangayomi's constructor accepts an options bag that only configures redirect and
        // cookie behaviour. Both are owned by the app's shared OkHttp client here — that is the
        // entire point of routing source traffic through it — so the argument is accepted and
        // ignored rather than rejected, which would fail sources that pass one out of habit.
    }

    MClient.prototype.get = function (url, headers) {
        return hostClient.get(url, headers || {}).then(function (payload) {
            return decodeResponse(payload, 'GET', url);
        });
    };

    MClient.prototype.post = function (url, headers, body) {
        // Serialise object bodies. The host binding reads the body with `as? String`, so a JS
        // object arrives as a Map, fails the cast, and becomes null — the request then goes out
        // with no payload at all and the source sees an unexplained empty response. Sources that
        // already encoded their body pass a string, which is forwarded untouched.
        var payload = body === undefined || body === null ? null
            : (typeof body === 'string' ? body : JSON.stringify(body));
        return hostClient.post(url, headers || {}, payload).then(function (raw) {
            return decodeResponse(raw, 'POST', url);
        });
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
         * Values are returned exactly as the tag spells them, which is also what `attr()` does —
         * neither resolves a relative URL. `getHref`/`getSrc` (and `absAttr`) are the resolving
         * accessors; reach for one of those when an absolute URL is what is wanted.
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
    /**
     * The value one declaration entry carries, or `undefined` if it declares none.
     *
     * Split out from the cache below so each half stays readable on its own: this is the part that
     * knows the four shapes Mangayomi's preference declarations take, and it is where a fifth one
     * would be added.
     */
    function declaredValueOf(entry) {
        if (entry.editTextPreference) {
            return entry.editTextPreference.value;
        }
        if (entry.listPreference) {
            // A list declares its default as an index into entryValues, not as a value — except
            // when it also carries an explicit `value`, which wins.
            var list = entry.listPreference;
            if (list.value !== undefined) {
                return list.value;
            }
            var values = list.entryValues || [];
            return values[typeof list.valueIndex === 'number' ? list.valueIndex : 0];
        }
        if (entry.multiSelectListPreference) {
            return entry.multiSelectListPreference.values || [];
        }
        var toggle = entry.switchPreferenceCompat || entry.checkBoxPreference;
        return toggle ? toggle.value : undefined;
    }

    function loadDeclaredDefaults() {
        var out = {};
        try {
            var declared = currentProvider && typeof currentProvider.getSourcePreferences === 'function'
                ? currentProvider.getSourcePreferences()
                : [];
            for (var i = 0; i < declared.length; i++) {
                var entry = declared[i];
                if (!entry || !entry.key) { continue; }
                var value = declaredValueOf(entry);
                if (value !== undefined) {
                    out[entry.key] = value;
                }
            }
        } catch (e) {
            // A source whose preference declaration throws still has to be usable for everything
            // that does not depend on a preference. Discard the partial map rather than keeping
            // it: a half-read declaration would answer some keys and silently not others.
            return {};
        }
        return out;
    }

    function defaultFor(key) {
        if (declaredDefaults === null) {
            declaredDefaults = loadDeclaredDefaults();
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
    /**
     * Coerce a preference value to the type the caller's default implies.
     *
     * Applied to declared defaults as well as stored ones, and that is the point. The host stores
     * strings, and an extension declares an `editTextPreference` default as a string too — so
     * `getInt(key, 0)` against a declared "5" used to return the *string* "5" where the identical
     * value, once the user had touched the setting, returned the number 5. A source doing
     * arithmetic on it got "5" + 1 = "51", and only for users who had never opened the settings
     * screen: the exact population the declared-default fallback exists to serve.
     *
     * Values that already carry a type are passed through. A `multiSelectListPreference` declares
     * an array and a switch declares a boolean; re-parsing those would be a second guess at
     * something already known.
     */
    function coerce(value, defaultValue) {
        if (typeof value !== 'string') {
            return value;
        }
        if (defaultValue !== null && typeof defaultValue === 'object') {
            try {
                return JSON.parse(value);
            } catch (e) {
                return defaultValue;
            }
        }
        if (typeof defaultValue === 'boolean') {
            return value === 'true';
        }
        if (typeof defaultValue === 'number') {
            var parsed = Number(value);
            return isNaN(parsed) ? defaultValue : parsed;
        }
        return value;
    }

    MSharedPreferences.prototype.get = function (key, defaultValue) {
        var stored = hostPreferences.get(key);
        if (stored === null || stored === undefined) {
            // Nothing stored: fall back to what the extension declared, then to what the caller
            // asked for. The declared value comes first because it is the source's own statement
            // of what the setting means when untouched, whereas the caller's argument is usually
            // just a type hint written at the call site.
            var declared = defaultFor(key);
            if (declared !== undefined) {
                return coerce(declared, defaultValue);
            }
            return defaultValue === undefined ? null : defaultValue;
        }
        return coerce(stored, defaultValue);
    };

    MSharedPreferences.prototype.set = function (key, value) {
        // A nullish write is dropped rather than stored.
        //
        // The host has no `remove`, so every write is a write of *something*: `String(null)`
        // persists the four characters "null", and the empty string the Kotlin binding's
        // `?.toString().orEmpty()` produces is no better — both read back as a value that is set,
        // which permanently shadows the extension's declared default with no way for a user to
        // undo it short of reinstalling the source. Sources reach this path by accident, writing a
        // value they have not computed yet, not by deciding the preference should be blank; a
        // source that means blank passes `''` and that still stores.
        //
        // This is a deliberate divergence from the host binding, and the only one in this file.
        // The binding keeps its own coercion as a floor for any caller that is not the prelude.
        if (value === null || value === undefined) {
            return;
        }
        hostPreferences.set(key, typeof value === 'object' ? JSON.stringify(value) : String(value));
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
