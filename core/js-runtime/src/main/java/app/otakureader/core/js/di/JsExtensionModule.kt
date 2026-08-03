package app.otakureader.core.js.di

import app.otakureader.core.extension.domain.backend.JsExtensionBackend
import app.otakureader.core.js.remote.JsExtensionBackendImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the JavaScript extension backend to its implementation.
 *
 * Unlike `CloudflareModule` — which lives in `:app` because neither `:core:network` nor
 * `:core:webview` can see the other — this binding lives with the implementation. The dependency
 * here only runs one way: `:core:js-runtime` depends on `:core:extension`, so it can see both the
 * interface and the class that satisfies it, and `:app` does not need to know either exists.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class JsExtensionModule {

    @Binds
    @Singleton
    abstract fun bindJsExtensionBackend(impl: JsExtensionBackendImpl): JsExtensionBackend
}
