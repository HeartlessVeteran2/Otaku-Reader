package app.otakureader.feature.reader.components

import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options

/**
 * Coil [Decoder.Factory] that subsamples oversized webtoon strip images at decode time
 * to prevent [OutOfMemoryError] on long vertical panels (e.g. 1080×10000+ Korean/Chinese
 * webtoon chapters delivered as a single image).
 *
 * The factory peeks the encoded source for image bounds (via [BitmapFactory.Options]'s
 * `inJustDecodeBounds = true`) without consuming the stream, then computes a power-of-two
 * `inSampleSize` that keeps the decoded bitmap below both:
 *  - [maxBitmapHeight]  — guards against very tall single-strip panels (default 4096 px)
 *  - [maxBitmapPixels]  — guards total pixel count (default 8 M pixels ≈ 32 MB ARGB_8888)
 *
 * For images that already fit within the budget, [create] returns `null` so Coil falls
 * through to its default [coil3.decode.BitmapFactoryDecoder] — no behavior change for
 * normally-sized pages.
 *
 * This is the lightweight "Option A" approach from the upstream issue: a custom decoder
 * that uses Android's `BitmapFactory` subsampling to keep memory bounded. True viewport
 * tiling (only decoding visible regions) requires replacing `AsyncImage` with a dedicated
 * subsampling Compose component and is out of scope here.
 */
class SubsamplingWebtoonDecoder(
    private val source: coil3.decode.ImageSource,
    private val sampleSize: Int,
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            // Coil's default decoder honors hardware bitmaps and other niceties; for the
            // subsample path we keep it simple — software ARGB_8888 is broadly compatible
            // with the existing `ZoomableImage` zoom/pan/transform pipeline.
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bitmap = source.use { src ->
            BitmapFactory.decodeStream(src.source().inputStream(), null, opts)
        } ?: return null

        return DecodeResult(image = bitmap.asImage(), isSampled = sampleSize > 1)
    }

    class Factory(
        private val maxBitmapHeight: Int = DEFAULT_MAX_BITMAP_HEIGHT,
        private val maxBitmapPixels: Long = DEFAULT_MAX_BITMAP_PIXELS,
    ) : Decoder.Factory {

        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            // Peek bounds without consuming the source so Coil's default decoder can still
            // read it if we fall through. `peek()` returns a buffered view over the same
            // upstream — bounds-only decode reads only the image header (a few KB).
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                BitmapFactory.decodeStream(
                    result.source.source().peek().inputStream(),
                    null,
                    boundsOpts,
                )
            } catch (_: Exception) {
                // Narrowed from Throwable so we don't swallow OutOfMemoryError/ThreadDeath.
                // For any IO/decode failure, fall through to Coil's default decoder.
                return null
            }

            val width = boundsOpts.outWidth
            val height = boundsOpts.outHeight
            if (width <= 0 || height <= 0) return null

            val sampleSize = computeSampleSize(width, height, maxBitmapHeight, maxBitmapPixels)
            if (sampleSize <= 1) return null // fall through to default decoder

            return SubsamplingWebtoonDecoder(result.source, sampleSize)
        }
    }

    companion object {
        /**
         * Maximum allowed decoded bitmap height (px). Webtoon strips taller than this are
         * subsampled. 4096 px matches the GL_MAX_TEXTURE_SIZE floor on most modern GPUs and
         * the threshold suggested in the upstream issue.
         */
        const val DEFAULT_MAX_BITMAP_HEIGHT = 4096

        /**
         * Maximum total decoded pixel count. 8 M pixels ≈ 32 MB at ARGB_8888, comfortably
         * within heap budgets even when several pages are held in Coil's LRU. `Long` so
         * callers can pass values larger than [Int.MAX_VALUE] without overflow.
         */
        const val DEFAULT_MAX_BITMAP_PIXELS: Long = 8L * 1024L * 1024L

        /**
         * Computes the smallest power-of-two `inSampleSize` such that the decoded bitmap
         * has `height ≤ maxHeight` AND `width × height ≤ maxPixels`.
         *
         * Returns `1` when the source already fits (caller should fall through to the
         * default decoder). Always returns a power of two as required by
         * [BitmapFactory.Options.inSampleSize].
         */
        fun computeSampleSize(
            width: Int,
            height: Int,
            maxHeight: Int = DEFAULT_MAX_BITMAP_HEIGHT,
            maxPixels: Long = DEFAULT_MAX_BITMAP_PIXELS,
        ): Int {
            if (width <= 0 || height <= 0) return 1
            var sample = 1
            while (true) {
                val sampledWidth = width / sample
                val sampledHeight = height / sample
                val withinHeight = sampledHeight <= maxHeight
                val withinPixels = sampledWidth.toLong() * sampledHeight.toLong() <= maxPixels
                if (withinHeight && withinPixels) return sample
                sample *= 2
                // Safety guard — sample sizes beyond this collapse the image to nothing useful.
                if (sample >= 1024) return sample
            }
        }
    }
}
