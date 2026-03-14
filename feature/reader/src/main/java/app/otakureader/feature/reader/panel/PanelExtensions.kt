package app.otakureader.feature.reader.panel

import app.otakureader.feature.reader.model.ReaderPage
import app.otakureader.feature.reader.model.ReadingDirection

/**
 * Extension functions for panel detection on ReaderPage
 */

/**
 * Detect panels for a list of pages
 *
 * @param panelDetectionService Service to perform panel detection
 * @param readingDirection Reading direction for panel ordering
 * @return List of pages with detected panels
 */
suspend fun List<ReaderPage>.withPanelDetection(
    panelDetectionService: PanelDetectionService,
    readingDirection: ReadingDirection
): List<ReaderPage> {
    return map { page ->
        page.withPanels(panelDetectionService, readingDirection)
    }
}

/**
 * Detect panels for a single page
 *
 * @param panelDetectionService Service to perform panel detection
 * @param readingDirection Reading direction for panel ordering
 * @return Page with detected panels
 */
suspend fun ReaderPage.withPanels(
    panelDetectionService: PanelDetectionService,
    readingDirection: ReadingDirection
): ReaderPage {
    val panels = panelDetectionService.detectPanelsFromUrl(imageUrl, readingDirection)
    return copy(panels = panels)
}
