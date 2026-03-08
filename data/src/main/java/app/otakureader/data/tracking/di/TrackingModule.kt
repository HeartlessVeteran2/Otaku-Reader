package app.otakureader.data.tracking.di

import app.otakureader.data.tracking.api.AniListApi
import app.otakureader.data.tracking.api.MyAnimeListApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MalRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnilistRetrofit

@Module
@InstallIn(SingletonComponent::class)
object TrackingModule {

    @Provides
    @Singleton
    @MalRetrofit
    fun provideMyAnimeListRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.myanimelist.net/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideMyAnimeListApi(@MalRetrofit retrofit: Retrofit): MyAnimeListApi {
        return retrofit.create(MyAnimeListApi::class.java)
    }

    @Provides
    @Singleton
    @AnilistRetrofit
    fun provideAniListRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://graphql.anilist.co/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideAniListApi(@AnilistRetrofit retrofit: Retrofit): AniListApi {
        return retrofit.create(AniListApi::class.java)
    }
}
