package app.komikku.data.di

import app.komikku.data.manga.MangaRepositoryImpl
import app.komikku.domain.manga.repository.MangaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMangaRepository(impl: MangaRepositoryImpl): MangaRepository
}
