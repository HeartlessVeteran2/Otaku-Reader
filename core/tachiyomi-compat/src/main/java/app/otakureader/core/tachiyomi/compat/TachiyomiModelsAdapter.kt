package app.otakureader.core.tachiyomi.compat

import app.otakureader.sourceapi.MangaPage
import app.otakureader.sourceapi.SChapter
import app.otakureader.sourceapi.SManga
import app.otakureader.sourceapi.SourceChapter
import app.otakureader.sourceapi.SourceManga

/**
 * Converts Tachiyomi model classes to Otaku Reader model classes.
 * This adapter handles all model transformations between the two systems.
 */
object TachiyomiModelsAdapter {

    /**
     * Convert Tachiyomi SManga to Otaku Reader SourceManga
     */
    fun toSourceManga(sManga: SManga): SourceManga {
        return SourceManga(
            url = sManga.url,
            title = sManga.title,
            thumbnailUrl = sManga.thumbnailUrl,
            description = sManga.description,
            author = sManga.author,
            artist = sManga.artist,
            genre = sManga.genres,
            status = sManga.status,
            initialized = sManga.initialized
        )
    }

    /**
     * Convert list of SManga to list of SourceManga
     */
    fun toSourceMangaList(sMangas: List<SManga>): List<SourceManga> {
        return sMangas.map { toSourceManga(it) }
    }

    /**
     * Convert Tachiyomi MangasPage to Otaku Reader MangaPage
     */
    fun toMangaPage(mangasPage: eu.kanade.tachiyomi.source.model.MangasPage): MangaPage {
        return MangaPage(
            mangas = mangasPage.mangas.map { toSourceManga(fromTachiyomiSManga(it)) },
            hasNextPage = mangasPage.hasNextPage
        )
    }

    /**
     * Convert Tachiyomi SChapter to Otaku Reader SourceChapter
     */
    fun toSourceChapter(sChapter: eu.kanade.tachiyomi.source.model.SChapter): SourceChapter {
        return SourceChapter(
            url = sChapter.url,
            name = sChapter.name,
            dateUpload = sChapter.date_upload,
            chapterNumber = sChapter.chapter_number,
            scanlator = sChapter.scanlator
        )
    }

    /**
     * Convert list of SChapter to list of SourceChapter
     */
    fun toSourceChapterList(sChapters: List<eu.kanade.tachiyomi.source.model.SChapter>): List<SourceChapter> {
        return sChapters.map { toSourceChapter(it) }
    }

    /**
     * Convert Tachiyomi SPage to Otaku Reader Page
     */
    fun toPage(sPage: eu.kanade.tachiyomi.source.model.Page, chapterId: Long, index: Int): app.otakureader.sourceapi.Page {
        return app.otakureader.sourceapi.Page(
            index = index,
            url = sPage.imageUrl ?: sPage.url,
            imageUrl = sPage.imageUrl,
        )
    }

    /**
     * Convert list of SPage to list of Page
     */
    fun toPageList(sPages: List<eu.kanade.tachiyomi.source.model.Page>, chapterId: Long): List<app.otakureader.sourceapi.Page> {
        return sPages.mapIndexed { index, sPage ->
            toPage(sPage, chapterId, index)
        }
    }

    /**
     * Convert Otaku Reader SourceManga to Tachiyomi SManga
     * Used when we need to pass data back to Tachiyomi source
     */
    fun toTachiyomiSManga(sourceManga: SourceManga): eu.kanade.tachiyomi.source.model.SManga {
        return eu.kanade.tachiyomi.source.model.SManga.create().apply {
            url = sourceManga.url
            title = sourceManga.title
            thumbnail_url = sourceManga.thumbnailUrl
            description = sourceManga.description ?: ""
            author = sourceManga.author ?: ""
            artist = sourceManga.artist ?: ""
            status = sourceManga.status
            genre = sourceManga.genre ?: ""
            initialized = sourceManga.initialized
        }
    }

    /**
     * Convert Otaku Reader SourceChapter to Tachiyomi SChapter
     */
    fun toTachiyomiSChapter(sourceChapter: SourceChapter): eu.kanade.tachiyomi.source.model.SChapter {
        return eu.kanade.tachiyomi.source.model.SChapter.create().apply {
            url = sourceChapter.url
            name = sourceChapter.name
            date_upload = sourceChapter.dateUpload
            chapter_number = sourceChapter.chapterNumber
            scanlator = sourceChapter.scanlator ?: ""
        }
    }

    /**
     * Convert Tachiyomi SManga to Otaku Reader SManga
     */
    fun fromTachiyomiSManga(tachiyomiSManga: eu.kanade.tachiyomi.source.model.SManga): SManga {
        return SManga(
            url = tachiyomiSManga.url,
            title = tachiyomiSManga.title,
            description = tachiyomiSManga.description ?: "",
            thumbnailUrl = tachiyomiSManga.thumbnail_url,
            author = tachiyomiSManga.author ?: "",
            artist = tachiyomiSManga.artist ?: "",
            genres = tachiyomiSManga.genre ?: "",
            status = tachiyomiSManga.status,
            initialized = tachiyomiSManga.initialized,
        )
    }

    /**
     * Parse status integer from Tachiyomi to Status enum
     */
    fun parseStatus(status: Int): app.otakureader.domain.model.MangaStatus {
        return when (status) {
            eu.kanade.tachiyomi.source.model.SManga.ONGOING -> app.otakureader.domain.model.MangaStatus.ONGOING
            eu.kanade.tachiyomi.source.model.SManga.COMPLETED -> app.otakureader.domain.model.MangaStatus.COMPLETED
            eu.kanade.tachiyomi.source.model.SManga.LICENSED -> app.otakureader.domain.model.MangaStatus.LICENSED
            eu.kanade.tachiyomi.source.model.SManga.PUBLISHING_FINISHED -> app.otakureader.domain.model.MangaStatus.PUBLISHING_FINISHED
            eu.kanade.tachiyomi.source.model.SManga.CANCELLED -> app.otakureader.domain.model.MangaStatus.CANCELLED
            eu.kanade.tachiyomi.source.model.SManga.ON_HIATUS -> app.otakureader.domain.model.MangaStatus.ON_HIATUS
            else -> app.otakureader.domain.model.MangaStatus.UNKNOWN
        }
    }
}
