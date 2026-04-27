package app.otakureader.core.tachiyomi.di

import app.otakureader.core.tachiyomi.health.SourceHealthMonitor
import app.otakureader.domain.usecase.source.GetLatestUpdatesUseCase
import app.otakureader.domain.usecase.source.GetMangaDetailsUseCase
import app.otakureader.domain.usecase.source.GetPopularMangaUseCase
import app.otakureader.domain.usecase.source.GetSourceFiltersUseCase
import app.otakureader.domain.usecase.source.GetSourcesUseCase
import app.otakureader.domain.usecase.source.GlobalSearchUseCase
import app.otakureader.domain.usecase.source.SearchMangaUseCase
import app.otakureader.domain.usecase.library.AddMangaToLibraryUseCase
import app.otakureader.domain.repository.SourceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Tachiyomi compatibility dependencies.
 *
 * ## OkHttp client isolation model
 *
 * The app uses a **two-tier** HTTP client architecture to keep extension network
 * traffic isolated from core app traffic:
 *
 * 1. **Global client** (`OkHttpClient` — no qualifier, provided by `NetworkModule`):
 *    Used only for infrastructure requests that originate inside the app itself
 *    (extension APK downloads, update checks, app-level API calls).  It carries
 *    the shared interceptor chain (logging, Brotli, IgnoreGzip) but no
 *    extension-specific cookies, headers, or TLS settings.
 *
 * 2. **Per-extension client** (managed by each Tachiyomi `HttpSource`):
 *    Every loaded extension creates its own `OkHttpClient` inside its
 *    `HttpSource.network` property.  These clients may carry extension-specific
 *    cookie jars, custom headers (e.g. Referer, CF bypass), and per-source
 *    TLS / certificate-pinning configuration.  They are **not** shared across
 *    extensions and are created lazily when the extension is first accessed.
 *
 * Consequence: adding a global interceptor in `NetworkModule` does **not**
 * affect extension network calls — changes to extension behaviour must be made
 * inside the extension's own `HttpSource` or via the extension's network client
 * configuration.
 */
@Module
@InstallIn(SingletonComponent::class)
object TachiyomiModule {

    // SourceHealthMonitor is provided via @Inject constructor (@Singleton).

    @Provides
    @Singleton
    fun provideSourceHealthMonitor(): SourceHealthMonitor {
        return SourceHealthMonitor()
    }

    @Provides
    fun provideGetSourcesUseCase(
        sourceRepository: SourceRepository
    ): GetSourcesUseCase {
        return GetSourcesUseCase(sourceRepository)
    }

    @Provides
    fun provideGetPopularMangaUseCase(
        sourceRepository: SourceRepository
    ): GetPopularMangaUseCase {
        return GetPopularMangaUseCase(sourceRepository)
    }

    @Provides
    fun provideGetLatestUpdatesUseCase(
        sourceRepository: SourceRepository
    ): GetLatestUpdatesUseCase {
        return GetLatestUpdatesUseCase(sourceRepository)
    }

    @Provides
    fun provideSearchMangaUseCase(
        sourceRepository: SourceRepository
    ): SearchMangaUseCase {
        return SearchMangaUseCase(sourceRepository)
    }

    @Provides
    fun provideGetMangaDetailsUseCase(
        sourceRepository: SourceRepository
    ): GetMangaDetailsUseCase {
        return GetMangaDetailsUseCase(sourceRepository)
    }

    @Provides
    fun provideGlobalSearchUseCase(
        sourceRepository: SourceRepository
    ): GlobalSearchUseCase {
        return GlobalSearchUseCase(sourceRepository)
    }

    @Provides
    fun provideGetSourceFiltersUseCase(
        sourceRepository: SourceRepository
    ): GetSourceFiltersUseCase {
        return GetSourceFiltersUseCase(sourceRepository)
    }
    
    @Provides
    fun provideAddMangaToLibraryUseCase(
        mangaRepository: app.otakureader.domain.repository.MangaRepository
    ): AddMangaToLibraryUseCase {
        return AddMangaToLibraryUseCase(mangaRepository)
    }
}
