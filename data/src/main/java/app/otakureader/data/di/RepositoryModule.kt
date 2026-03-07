package app.otakureader.data.di

import app.otakureader.domain.repository.CategoryRepository
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.data.repository.CategoryRepositoryImpl
import app.otakureader.data.repository.ChapterRepositoryImpl
import app.otakureader.data.repository.MangaRepositoryImpl
import app.otakureader.core.tachiyomi.repository.SourceRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    abstract fun bindMangaRepository(
        impl: MangaRepositoryImpl
    ): MangaRepository
    
    @Binds
    abstract fun bindChapterRepository(
        impl: ChapterRepositoryImpl
    ): ChapterRepository
    
    @Binds
    abstract fun bindCategoryRepository(
        impl: CategoryRepositoryImpl
    ): CategoryRepository
    
    @Binds
    abstract fun bindSourceRepository(
        impl: SourceRepositoryImpl
    ): SourceRepository
}
