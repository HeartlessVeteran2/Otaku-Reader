package app.otakureader.di

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Coil [ImageLoader] singleton.
 *
 * The loader is created via [SingletonImageLoader.get], which delegates to
 * [app.otakureader.OtakuReaderApplication.newImageLoader].
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return SingletonImageLoader.get(context)
    }
}
