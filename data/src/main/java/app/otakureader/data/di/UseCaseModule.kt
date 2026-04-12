package app.otakureader.data.di

import app.otakureader.domain.repository.AiRepository
import app.otakureader.domain.ai.AiFeatureGate
import app.otakureader.domain.repository.ChapterRepository
import app.otakureader.domain.repository.ChapterSummaryRepository
import app.otakureader.domain.repository.DownloadRepository
import app.otakureader.domain.repository.MangaRepository
import app.otakureader.domain.repository.OpdsRepository
import app.otakureader.domain.repository.SfxTranslationRepository
import app.otakureader.domain.repository.SourceIntelligenceRepository
import app.otakureader.domain.usecase.DeleteChapterUseCase
import app.otakureader.domain.usecase.GetChaptersUseCase
import app.otakureader.domain.usecase.GetHistoryUseCase
import app.otakureader.domain.usecase.GetLibraryUseCase
import app.otakureader.domain.usecase.ai.GenerateAiContentUseCase
import app.otakureader.domain.usecase.ai.ScoreSourcesForMangaUseCase
import app.otakureader.domain.usecase.ai.SummarizeChapterUseCase
import app.otakureader.domain.usecase.ai.TranslateSfxUseCase
import app.otakureader.domain.usecase.opds.BrowseOpdsCatalogUseCase
import app.otakureader.domain.usecase.opds.DeleteOpdsServerUseCase
import app.otakureader.domain.usecase.opds.GetOpdsServersUseCase
import app.otakureader.domain.usecase.opds.SaveOpdsServerUseCase
import app.otakureader.domain.usecase.opds.SearchOpdsCatalogUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module that provides use case instances.
 * Use cases are provided (not bound) because they are not interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideGetLibraryUseCase(mangaRepository: MangaRepository): GetLibraryUseCase =
        GetLibraryUseCase(mangaRepository)

    @Provides
    fun provideGetChaptersUseCase(chapterRepository: ChapterRepository): GetChaptersUseCase =
        GetChaptersUseCase(chapterRepository)

    @Provides
    fun provideGetHistoryUseCase(chapterRepository: ChapterRepository): GetHistoryUseCase =
        GetHistoryUseCase(chapterRepository)

    @Provides
    fun provideDeleteChapterUseCase(downloadRepository: DownloadRepository): DeleteChapterUseCase =
        DeleteChapterUseCase(downloadRepository)

    @Provides
    fun provideGetOpdsServersUseCase(opdsRepository: OpdsRepository): GetOpdsServersUseCase =
        GetOpdsServersUseCase(opdsRepository)

    @Provides
    fun provideSaveOpdsServerUseCase(opdsRepository: OpdsRepository): SaveOpdsServerUseCase =
        SaveOpdsServerUseCase(opdsRepository)

    @Provides
    fun provideDeleteOpdsServerUseCase(opdsRepository: OpdsRepository): DeleteOpdsServerUseCase =
        DeleteOpdsServerUseCase(opdsRepository)

    @Provides
    fun provideBrowseOpdsCatalogUseCase(opdsRepository: OpdsRepository): BrowseOpdsCatalogUseCase =
        BrowseOpdsCatalogUseCase(opdsRepository)

    @Provides
    fun provideSearchOpdsCatalogUseCase(opdsRepository: OpdsRepository): SearchOpdsCatalogUseCase =
        SearchOpdsCatalogUseCase(opdsRepository)

    @Provides
    fun provideGenerateAiContentUseCase(
        aiRepository: AiRepository,
        aiFeatureGate: AiFeatureGate,
    ): GenerateAiContentUseCase =
        GenerateAiContentUseCase(aiRepository, aiFeatureGate)

    @Provides
    fun provideTranslateSfxUseCase(
        aiRepository: AiRepository,
        aiFeatureGate: AiFeatureGate,
        sfxTranslationRepository: SfxTranslationRepository,
    ): TranslateSfxUseCase =
        TranslateSfxUseCase(aiRepository, aiFeatureGate, sfxTranslationRepository)

    @Provides
    fun provideSummarizeChapterUseCase(
        aiRepository: AiRepository,
        aiFeatureGate: AiFeatureGate,
        chapterSummaryRepository: ChapterSummaryRepository,
    ): SummarizeChapterUseCase =
        SummarizeChapterUseCase(aiRepository, aiFeatureGate, chapterSummaryRepository)

    @Provides
    fun provideScoreSourcesForMangaUseCase(
        aiRepository: AiRepository,
        aiFeatureGate: AiFeatureGate,
        sourceIntelligenceRepository: SourceIntelligenceRepository,
    ): ScoreSourcesForMangaUseCase =
        ScoreSourcesForMangaUseCase(aiRepository, aiFeatureGate, sourceIntelligenceRepository)
}
