package app.otakureader.core.extension.domain.backend

import app.otakureader.core.extension.domain.model.Extension

/**
 * The JavaScript backend, as the APK-oriented extension layer needs to see it.
 *
 * Declared here and implemented in `:core:js-runtime` so the dependency runs one way: the JS
 * module knows about [Extension] and the extension-management layer knows only this interface.
 * Without the seam the two modules would have to depend on each other, since install has to be
 * dispatched from the APK installer while the actual work lives in the JS module.
 *
 * The same pattern as `CloudflareChallengeSolver` — interface in the module that calls it,
 * implementation in the module that can do the work, bound in `:app`, which is the only place
 * that knows about both.
 *
 * ### Why the JS sources ride the existing `Extension` model
 *
 * Everything in `feature/browse` — the list, the install button, the language filter, the search
 * — reads `Extension` rows out of the `extensions` table. Modelling a JavaScript source as one of
 * those rows means the entire extension-management UI works for the new backend without a single
 * edit. The alternative, a parallel model with a parallel screen, would have duplicated all of it
 * and then drifted.
 *
 * The one thing the model needed was an honest discriminator: [Extension.isJavaScript]. It is not
 * inferred from a name prefix or a URL suffix, because install and uninstall route on it and a
 * guess that is right most of the time would send an occasional `.js` file to the APK installer.
 */
/**
 * The outcome of a JavaScript index fetch.
 *
 * [servedAnyIndex] is carried rather than inferred from `extensions.isEmpty()`, because those two
 * facts are not the same and the gap between them is user-visible. A repository that serves a
 * valid but *empty* `js/index.json` produced no extensions and yet worked perfectly. Treating an
 * empty list as failure would report "all repositories failed" at a user whose setup is fine —
 * and using emptiness as a proxy for success is exactly the kind of almost-right comparison that
 * has needed correcting repeatedly in this rebuild.
 */
data class JsExtensionFetch(
    val extensions: List<Extension>,
    /**
     * True when at least one repository served an index that could actually be **read**.
     *
     * A body that arrives but cannot be parsed counts as false, and that is the honest reading:
     * an unparseable index yields no sources, exactly like an absent one, so it cannot be
     * treated as evidence that anything worked. [firstFailure] is what keeps that from being
     * lossy — the reason is carried rather than flattened into a boolean.
     */
    val servedAnyIndex: Boolean,
    /**
     * The first per-repository failure, kept for diagnosis.
     *
     * Without it a JavaScript-only repository with a malformed index reports whatever its
     * *APK* endpoint said — a 404 on `index.min.json`, for a user who has no APK sources and
     * cannot act on that. The real problem never reaches them.
     */
    val firstFailure: Throwable? = null,
)

interface JsExtensionBackend {

    /**
     * Available JavaScript sources across [repoUrls].
     *
     * Takes the URLs rather than reading them itself so there is one reader of the configured
     * repository list. Two readers would be two chances to normalise a URL differently, and a
     * repo that resolved one way for APKs and another way for scripts would be a confusing
     * partial failure rather than a clean one.
     *
     * Reports no extensions rather than throwing when a repo has no JavaScript index — most
     * repositories serve only APKs, and that is a normal answer, not an error. Whether any
     * index was actually served is reported separately; see [JsExtensionFetch].
     */
    suspend fun fetchAvailable(repoUrls: List<String>): JsExtensionFetch

    /**
     * Download [extension]'s script and register it, making the source immediately usable.
     *
     * [Extension.apkUrl] carries the `.js` URL for a JavaScript extension. The field is reused
     * rather than duplicated because it means exactly the same thing — where to fetch the
     * installable artifact from — and a parallel column that was null for every APK row would be
     * schema with no information in it.
     */
    suspend fun install(extension: Extension): Result<Unit>

    /** Remove the script, its registration, and anything the source stored — including logins. */
    suspend fun uninstall(sourceId: String): Result<Unit>
}
