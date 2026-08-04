package app.otakureader.data.tracking.di

import app.otakureader.data.metadata.AniListMetadataService
import app.otakureader.data.tracking.api.AniListApi
import app.otakureader.data.tracking.api.AniListRateLimitInterceptor
import app.otakureader.data.tracking.api.KitsuApi
import app.otakureader.data.tracking.api.KitsuOAuthApi
import app.otakureader.data.tracking.api.MangaUpdatesApi
import app.otakureader.data.tracking.api.MyAnimeListApi
import app.otakureader.data.tracking.api.MyAnimeListOAuthApi
import app.otakureader.data.tracking.api.ShikimoriApi
import app.otakureader.data.tracking.api.ShikimoriOAuthApi
import app.otakureader.data.tracking.repository.TrackRepositoryImpl
import app.otakureader.data.tracking.repository.TrackerSyncRepositoryImpl
import app.otakureader.data.tracking.tracker.AniListTracker
import app.otakureader.data.tracking.tracker.KitsuTracker
import app.otakureader.data.tracking.tracker.MangaUpdatesTracker
import app.otakureader.data.tracking.tracker.MyAnimeListTracker
import app.otakureader.data.tracking.tracker.ShikimoriTracker
import app.otakureader.domain.tracking.Tracker
import app.otakureader.domain.tracking.TrackRepository
import app.otakureader.domain.repository.TrackerSyncRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.Json
import app.otakureader.core.network.di.TrackerOkHttp
import app.otakureader.core.preferences.TrackerTokenStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KitsuOAuth

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KitsuEdge

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MalOAuth

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MalApi

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ShikimoriOAuth

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ShikimoriApiQ

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AniListApiQ

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MangaUpdatesApiQ

@Module
@InstallIn(SingletonComponent::class)
object TrackingNetworkModule {

    // ── Retrofit instances ────────────────────────────────────────────────

    @Provides
    @Singleton
    @KitsuOAuth
    fun provideKitsuOAuthRetrofit(@TrackerOkHttp okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://kitsu.app/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @KitsuEdge
    fun provideKitsuEdgeRetrofit(@TrackerOkHttp okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://kitsu.app/api/edge/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @MalOAuth
    fun provideMalOAuthRetrofit(@TrackerOkHttp okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://myanimelist.net/v1/oauth2/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @MalApi
    fun provideMalApiRetrofit(@TrackerOkHttp okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://api.myanimelist.net/v2/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @ShikimoriOAuth
    fun provideShikimoriOAuthRetrofit(@TrackerOkHttp okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://shikimori.one/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @ShikimoriApiQ
    fun provideShikimoriApiRetrofit(@TrackerOkHttp okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://shikimori.one/api/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    /**
     * AniList gets its own client, derived from the shared tracker one.
     *
     * `newBuilder()` keeps everything `@TrackerOkHttp` configures — certificate pinning above all
     * — and adds one interceptor on top. Registering the rate-limit interceptor on
     * `@TrackerOkHttp` itself would apply AniList's 429 semantics to MAL, Kitsu, Shikimori and
     * MangaUpdates, whose limits and headers differ; a retry policy read from the wrong service's
     * headers is worse than none.
     *
     * An application interceptor, not a network one: it retries the whole call, and a network
     * interceptor runs per hop, so it would not see the retry as the same logical request.
     */
    @Provides
    @Singleton
    @AniListApiQ
    fun provideAniListRetrofit(@TrackerOkHttp okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .client(
                okHttpClient.newBuilder()
                    .addInterceptor(AniListRateLimitInterceptor())
                    .build()
            )
            .baseUrl("https://graphql.anilist.co/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @MangaUpdatesApiQ
    fun provideMangaUpdatesRetrofit(@TrackerOkHttp okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://api.mangaupdates.com/v1/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    // ── API interfaces ────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideKitsuOAuthApi(@KitsuOAuth retrofit: Retrofit): KitsuOAuthApi =
        retrofit.create(KitsuOAuthApi::class.java)

    @Provides
    @Singleton
    fun provideKitsuApi(@KitsuEdge retrofit: Retrofit): KitsuApi =
        retrofit.create(KitsuApi::class.java)

    @Provides
    @Singleton
    fun provideMalOAuthApi(@MalOAuth retrofit: Retrofit): MyAnimeListOAuthApi =
        retrofit.create(MyAnimeListOAuthApi::class.java)

    @Provides
    @Singleton
    fun provideMalApi(@MalApi retrofit: Retrofit): MyAnimeListApi =
        retrofit.create(MyAnimeListApi::class.java)

    @Provides
    @Singleton
    fun provideShikimoriOAuthApi(@ShikimoriOAuth retrofit: Retrofit): ShikimoriOAuthApi =
        retrofit.create(ShikimoriOAuthApi::class.java)

    @Provides
    @Singleton
    fun provideShikimoriApi(@ShikimoriApiQ retrofit: Retrofit): ShikimoriApi =
        retrofit.create(ShikimoriApi::class.java)

    /**
     * Built on the same `@AniListApiQ` Retrofit as [AniListApi], so it inherits the rate-limit
     * interceptor and certificate pinning without restating either. A separate service interface
     * because the response shapes share no fields — see [AniListMetadataService].
     */
    @Provides
    @Singleton
    fun provideAniListMetadataService(@AniListApiQ retrofit: Retrofit): AniListMetadataService =
        retrofit.create(AniListMetadataService::class.java)

    @Provides
    @Singleton
    fun provideAniListApi(@AniListApiQ retrofit: Retrofit): AniListApi =
        retrofit.create(AniListApi::class.java)

    @Provides
    @Singleton
    fun provideMangaUpdatesApi(@MangaUpdatesApiQ retrofit: Retrofit): MangaUpdatesApi =
        retrofit.create(MangaUpdatesApi::class.java)

    // ── Trackers ──────────────────────────────────────────────────────────

    @Provides
    @Singleton
    @IntoSet
    fun provideKitsuTracker(
        oauthApi: KitsuOAuthApi,
        api: KitsuApi,
        tokenStore: TrackerTokenStore,
    ): Tracker = KitsuTracker(
        oauthApi = oauthApi,
        api = api,
        clientId = TrackerCredentials.KITSU_CLIENT_ID,
        redirectUri = TrackerCredentials.KITSU_REDIRECT_URI,
        tokenStore = tokenStore,
    )

    @Provides
    @Singleton
    @IntoSet
    fun provideMangaUpdatesTracker(api: MangaUpdatesApi, tokenStore: TrackerTokenStore): Tracker =
        MangaUpdatesTracker(api, tokenStore)

    @Provides
    @Singleton
    @IntoSet
    fun provideShikimoriTracker(
        oauthApi: ShikimoriOAuthApi,
        api: ShikimoriApi,
        tokenStore: TrackerTokenStore,
    ): Tracker = ShikimoriTracker(
        oauthApi = oauthApi,
        api = api,
        clientId = TrackerCredentials.SHIKIMORI_CLIENT_ID,
        clientSecret = TrackerCredentials.SHIKIMORI_CLIENT_SECRET,
        redirectUri = TrackerCredentials.SHIKIMORI_REDIRECT_URI,
        tokenStore = tokenStore,
    )

    @Provides
    @Singleton
    @IntoSet
    fun provideMyAnimeListTracker(
        oauthApi: MyAnimeListOAuthApi,
        api: MyAnimeListApi,
        tokenStore: TrackerTokenStore,
    ): Tracker = MyAnimeListTracker(
        oauthApi = oauthApi,
        api = api,
        clientId = TrackerCredentials.MAL_CLIENT_ID,
        redirectUri = TrackerCredentials.MAL_REDIRECT_URI,
        tokenStore = tokenStore,
    )

    @Provides
    @Singleton
    @IntoSet
    fun provideAniListTracker(
        api: AniListApi,
        tokenStore: TrackerTokenStore,
    ): Tracker = AniListTracker(api = api, tokenStore = tokenStore)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingBindingsModule {

    @Binds
    @Singleton
    abstract fun bindTrackRepository(impl: TrackRepositoryImpl): TrackRepository

    @Binds
    @Singleton
    abstract fun bindTrackerSyncRepository(impl: TrackerSyncRepositoryImpl): TrackerSyncRepository
}
