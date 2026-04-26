package app.otakureader.feature.reader.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SubsamplingWebtoonDecoder.computeSampleSize].
 *
 * The method must return a power-of-two `inSampleSize` such that the resulting bitmap
 * fits within both the height and the total-pixel budgets — preventing OOM on long
 * single-strip webtoon panels.
 */
class SubsamplingWebtoonDecoderTest {

    @Test
    fun `normal sized image returns 1 (no subsampling)`() {
        // 1080x1920 = ~2M pixels, height < 4096 — fits in default budget.
        val sample = SubsamplingWebtoonDecoder.computeSampleSize(width = 1080, height = 1920)
        assertEquals(1, sample)
    }

    @Test
    fun `image at the height limit returns 1`() {
        val sample = SubsamplingWebtoonDecoder.computeSampleSize(width = 1080, height = 4096)
        assertEquals(1, sample)
    }

    @Test
    fun `tall webtoon strip is downsampled to fit height budget`() {
        // 1080x10000 — the canonical OOM case from the issue (43 MB at ARGB_8888).
        // 10000/4096 = 2.44 → smallest power-of-two divisor giving height <= 4096 is 4
        // (sample=2 gives 5000, still > 4096) → 1080/4 x 10000/4 = 270x2500.
        val sample = SubsamplingWebtoonDecoder.computeSampleSize(width = 1080, height = 10000)
        assertEquals(4, sample)
        val sampledWidth = 1080 / sample
        val sampledHeight = 10000 / sample
        assertEquals(2500, sampledHeight)
        // Memory budget assertion: decoded bitmap must fit within the 32 MB ARGB_8888 budget
        // (270 * 2500 * 4 bytes ≈ 2.6 MB — well below) so we never OOM on this image.
        val bytesArgb8888 = sampledWidth.toLong() * sampledHeight.toLong() * 4L
        assertTrue(
            "decoded size $bytesArgb8888 bytes exceeds 32 MB budget",
            bytesArgb8888 <= SubsamplingWebtoonDecoder.DEFAULT_MAX_BITMAP_PIXELS * 4L
        )
    }

    @Test
    fun `extremely tall strip is downsampled aggressively`() {
        // 1080x40000 — pathological single-strip chapter (~165 MB undecoded).
        // 40000 needs sample where 40000/sample <= 4096 → sample >= 9.77 → 16.
        val sample = SubsamplingWebtoonDecoder.computeSampleSize(width = 1080, height = 40000)
        assertEquals(16, sample)
        assertTrue(40000 / sample <= SubsamplingWebtoonDecoder.DEFAULT_MAX_BITMAP_HEIGHT)
    }

    @Test
    fun `wide image with high pixel count is downsampled by pixel budget`() {
        // 8000x4000 = 32 M pixels (~128 MB at ARGB_8888) — under the height limit but
        // far over the 8 M-pixel budget. 32M / 8M = 4 → sample = 2 → 4000x2000 = 8M pixels.
        val sample = SubsamplingWebtoonDecoder.computeSampleSize(width = 8000, height = 4000)
        assertEquals(2, sample)
        val w = 8000 / sample
        val h = 4000 / sample
        assertTrue(w.toLong() * h.toLong() <= SubsamplingWebtoonDecoder.DEFAULT_MAX_BITMAP_PIXELS)
    }

    @Test
    fun `result is always a power of two`() {
        val cases = listOf(
            1080 to 5000,
            1080 to 10000,
            1080 to 25000,
            2000 to 8000,
            8000 to 4000,
        )
        for ((w, h) in cases) {
            val s = SubsamplingWebtoonDecoder.computeSampleSize(w, h)
            // A power of two has exactly one bit set.
            assertEquals("sampleSize must be power of two for $w x $h", 1, Integer.bitCount(s))
        }
    }

    @Test
    fun `zero or negative dimensions return 1`() {
        assertEquals(1, SubsamplingWebtoonDecoder.computeSampleSize(0, 1000))
        assertEquals(1, SubsamplingWebtoonDecoder.computeSampleSize(1000, 0))
        assertEquals(1, SubsamplingWebtoonDecoder.computeSampleSize(-1, -1))
    }

    @Test
    fun `custom budgets are respected`() {
        // Tighter height budget (2048) on a 1080x5000 image:
        // 5000/sample must be <= 2048 → sample >= 2.44 → 4 → height becomes 1250.
        val sample = SubsamplingWebtoonDecoder.computeSampleSize(
            width = 1080,
            height = 5000,
            maxHeight = 2048,
            maxPixels = SubsamplingWebtoonDecoder.DEFAULT_MAX_BITMAP_PIXELS,
        )
        assertEquals(4, sample)
    }
}
