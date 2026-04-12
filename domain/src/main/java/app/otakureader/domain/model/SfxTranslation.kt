package app.otakureader.domain.model

import androidx.compose.runtime.Immutable

/**
 * Represents a single translated sound-effect (SFX) annotation on a manga page.
 *
 * @property pageIndex Zero-based index of the page within the chapter.
 * @property originalText The original SFX text detected on the page.
 * @property translatedText The AI-generated translation of the SFX.
 * @property confidence Confidence score from 0.0 to 1.0 for the detected/translated result.
 * @property positionHint An optional free-form description of where on the page the SFX appears
 *   (e.g. "top-right corner") returned by the AI.
 */
@Immutable
data class SfxTranslation(
    val pageIndex: Int,
    val originalText: String,
    val translatedText: String,
    val confidence: Float,
    val positionHint: String? = null,
)
