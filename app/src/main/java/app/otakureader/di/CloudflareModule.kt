package app.otakureader.di

import app.otakureader.core.network.cloudflare.CloudflareChallengeSolver
import app.otakureader.core.webview.WebViewChallengeManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the Cloudflare challenge solver to its WebView implementation.
 *
 * The binding lives in the app rather than in either module because solving a challenge means
 * showing UI, and `:core:network` — where the interceptor runs — must not depend on a UI module.
 * `:core:webview` implements the interface and the app is the only place that knows about both.
 *
 * The same pattern as `BytesRecorder` — a thin interface declared in `:core:network` and bound
 * where the implementation lives — though that one is bound with `@Provides` in
 * `:data`'s `NetworkRecorderModule`, since its implementation is a lambda rather than a class.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudflareModule {

    @Binds
    @Singleton
    abstract fun bindCloudflareChallengeSolver(
        impl: WebViewChallengeManager,
    ): CloudflareChallengeSolver
}
