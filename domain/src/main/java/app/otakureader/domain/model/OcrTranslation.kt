package app.otakureader.domain.model

import androidx.compose.runtime.Immutable

/**
 * Represents a single AI-translated text region detected on a manga page.
 *
 * Produced by the OCR translation feature, which uses Gemini Vision to recognise
 * any-script text (speech bubbles, narration boxes, signage) and translate it into
 * the user's preferred target language. Distinct from [SfxTranslation], which is
 * scoped to onomatopoeia/sound effects.
 *
 * @property pageIndex Zero-based index of the page within the chapter.
 * @property originalText The original text detected on the page (any script).
 * @property translatedText The AI-generated translation in the requested target language.
 * @property confidence Confidence score from 0.0 to 1.0 reported by the AI.
 * @property positionHint An optional free-form description of where on the page the
 *   text appears (e.g. "top-right speech bubble"). May be null if the AI did not
 *   provide a position.
 */
@Immutable
data class OcrTranslation(
    val pageIndex: Int,
    val originalText: String,
    val translatedText: String,
    val confidence: Float,
    val positionHint: String? = null,
)
