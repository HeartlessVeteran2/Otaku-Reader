package app.otakureader.core.js.remote

import app.otakureader.core.extension.domain.backend.JsExtensionBackend
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.js.client.JsSourceProvider
import app.otakureader.core.js.protocol.JsSourceConfig
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The JavaScript half of extension management.
 *
 * Install is two steps that must not be reordered: download the script, *then* register it.
 * Registering first would need a placeholder script, and a failed download would leave a source
 * that appears installed and fails on every call — the exact silent-failure shape this rebuild
 * exists to remove. Downloading first means a failure leaves nothing installed at all.
 */
@Singleton
class JsExtensionBackendImpl @Inject constructor(
    private val remoteDataSource: JsExtensionRemoteDataSource,
    private val provider: JsSourceProvider,
) : JsExtensionBackend {

    override suspend fun fetchAvailable(repoUrls: List<String>): List<Extension> =
        remoteDataSource.fetchAvailable(repoUrls)

    override suspend fun install(extension: Extension): Result<Unit> = runCatchingCancellable {
        val scriptUrl = requireNotNull(extension.apkUrl) {
            "JavaScript extension ${extension.pkgName} has no script URL"
        }

        val script = remoteDataSource.downloadScript(scriptUrl)

        // The config is built from the index entry rather than from anything inside the script.
        // A source that could declare its own id could claim another source's id — and with it
        // that source's stored preferences, which routinely hold the user's login for the site.
        provider.install(
            config = JsSourceConfig(
                id = extension.pkgName,
                name = extension.name,
                baseUrl = extension.sources.firstOrNull()?.baseUrl ?: extension.repoUrl.orEmpty(),
                lang = extension.lang,
                isNsfw = extension.isNsfw,
            ),
            script = script,
        )
    }

    override suspend fun uninstall(sourceId: String): Result<Unit> = runCatchingCancellable {
        provider.uninstall(sourceId)
    }
}

/**
 * `runCatching` that lets cancellation through.
 *
 * Plain `runCatching` catches [CancellationException] along with everything else, which turns a
 * cancelled install into a reported failure and — worse — breaks structured concurrency, because
 * the coroutine carries on after its scope has been cancelled.
 */
private inline fun runCatchingCancellable(block: () -> Unit): Result<Unit> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Result.failure(e)
    }
