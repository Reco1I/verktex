package com.reco1l.verktex.fonts

import com.reco1l.verktex.Verktex
import com.reco1l.verktex.graphics.PixelsSource
import com.reco1l.verktex.graphics.Texture
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The coverage of a glyph, how much of each of its pixels the glyph covers, from 0 to 255: [width] x
 * [height] bytes, rows tightly packed from the top, with its top left corner [left] pixels right of
 * the cursor and [top] below the baseline (negative above it), as [Font.Glyph.bearingX] and
 * [Font.Glyph.bearingY].
 */
class GlyphCoverage(
    val width: Int,
    val height: Int,
    val left: Int,
    val top: Int,
    val alpha: ByteArray,
) {
    init {
        require(alpha.size == width * height) { "Expected ${width * height} bytes for $width x $height pixels, got ${alpha.size}" }
    }
}

/**
 * A change of the shape of glyphs, baked once per glyph into the atlas of a font of its own, see
 * [Font.withEffect]: drawing text with it costs the same as drawing the text, whatever its radius.
 *
 * Make them with [dilate] and [blur], which round their radius so that close ones share a font.
 */
sealed class GlyphEffect {

    /**
     * How far, in pixels, the effect reaches past the glyph.
     */
    internal abstract val extent: Int

    /**
     * Applies the effect to [coverage], [width] x [height] values from 0 to 1, padded by [extent].
     */
    internal abstract fun apply(coverage: FloatArray, width: Int, height: Int): FloatArray


    /**
     * The glyph grown by [radius] pixels on every side, with round corners.
     */
    data class Dilate internal constructor(val radius: Float) : GlyphEffect() {

        override val extent = floor(radius).toInt() + 1

        override fun apply(coverage: FloatArray, width: Int, height: Int): FloatArray {
            val whole = floor(radius).toInt()
            val fraction = radius - whole

            val inner = dilate(coverage, width, height, whole)
            if (fraction == 0f) return inner

            // A fractional radius is between the two whole ones around it.
            val outer = dilate(coverage, width, height, whole + 1)
            for (i in inner.indices) {
                inner[i] += (outer[i] - inner[i]) * fraction
            }
            return inner
        }
    }

    /**
     * The glyph blurred, fading out over [radius] pixels (a Gaussian of a deviation of half of it),
     * its coverage multiplied by [gain] and clamped, so that a gain above 1 makes it stronger near
     * the glyph.
     */
    data class Blur internal constructor(val radius: Float, val gain: Float) : GlyphEffect() {

        private val boxes = gaussianBoxes(radius / 2f)

        override val extent = boxes.sum() + 1

        override fun apply(coverage: FloatArray, width: Int, height: Int): FloatArray {
            var current = coverage
            var scratch = FloatArray(coverage.size)

            for (box in boxes) {
                boxBlur(current, scratch, width, height, box, horizontal = true)
                boxBlur(scratch, current, width, height, box, horizontal = false)
            }

            if (gain != 1f) {
                for (i in current.indices) {
                    current[i] = min(1f, current[i] * gain)
                }
            }
            return current
        }
    }


    companion object {

        /**
         * The glyph grown by [radius] pixels, rounded to a quarter of a pixel.
         */
        fun dilate(radius: Float) = Dilate((radius * 4f).roundToInt().coerceAtLeast(0) / 4f)

        /**
         * The glyph blurred over [radius] pixels, rounded to half a pixel, its coverage times [gain],
         * rounded to a twentieth.
         */
        fun blur(radius: Float, gain: Float = 1f) =
            Blur((radius * 2f).roundToInt().coerceAtLeast(0) / 2f, (gain * 20f).roundToInt().coerceAtLeast(0) / 20f)
    }
}


/**
 * A font drawing the glyphs of [base] changed by [effect], see [Font.withEffect]. It has the metrics,
 * advances and kerning of [base], so text drawn with it lines up with the text drawn with [base].
 */
internal class EffectFont(private val base: Font, private val effect: GlyphEffect) : Font() {

    override val descriptor: Descriptor
        get() = base.descriptor

    override val metrics: Metrics
        get() = base.metrics

    override val isKerningSupported: Boolean
        get() = base.isKerningSupported

    override fun getKerning(first: Int, second: Int) = base.getKerning(first, second)

    override fun onCreatePage(): Texture {
        val size = min(PAGE_SIZE, Verktex.graphics.device.capabilities.maxTextureSize)
        return Verktex.graphics.createTexture(size, size)
    }

    override fun onCreateGlyph(codePoint: Int): Glyph {
        val advance = base[codePoint].advance
        val coverage = base.createCoverage(codePoint) ?: return Glyph(codePoint = codePoint, advance = advance)

        // Room for the effect, and a transparent border so that filtering at fractional positions
        // doesn't reach the neighbouring glyph.
        val padding = effect.extent + BORDER
        val width = coverage.width + 2 * padding
        val height = coverage.height + 2 * padding

        val values = FloatArray(width * height)
        for (y in 0 until coverage.height) {
            for (x in 0 until coverage.width) {
                values[(y + padding) * width + x + padding] = (coverage.alpha[y * coverage.width + x].toInt() and 0xFF) / 255f
            }
        }

        val result = effect.apply(values, width, height)

        // White with the coverage as alpha, premultiplied as textures are.
        val pixels = ByteArray(width * height * 4)
        for (i in result.indices) {
            val value = (result[i] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
            pixels[i * 4] = value
            pixels[i * 4 + 1] = value
            pixels[i * 4 + 2] = value
            pixels[i * 4 + 3] = value
        }

        val region = try {
            addGlyphSource(PixelsSource(width, height, pixels))
        } catch (_: IllegalStateException) {
            // Larger than a page: the glyph goes without its effect.
            return Glyph(codePoint = codePoint, advance = advance)
        }

        return Glyph(
            codePoint = codePoint,
            advance = advance,
            bearingX = (coverage.left - padding).toFloat(),
            bearingY = (coverage.top - padding).toFloat(),
            width = width.toFloat(),
            height = height.toFloat(),
            region = region,
        )
    }

    private companion object {
        const val PAGE_SIZE = 2048f
        const val BORDER = 1
    }
}


/**
 * [coverage] grown by [radius] whole pixels: each value is the largest one within a disk of [radius]
 * around it. A row of the disk is a sliding maximum over a row of the coverage, so it takes a pass
 * over the coverage per row of the disk.
 */
internal fun dilate(coverage: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
    if (radius <= 0) return coverage.copyOf()

    val result = FloatArray(coverage.size)
    val row = FloatArray(width)
    val queue = IntArray(width)

    for (dy in -radius..radius) {
        val halfWidth = floor(sqrt((radius * radius - dy * dy).toFloat())).toInt()

        for (y in 0 until height) {
            val sourceY = y + dy
            if (sourceY < 0 || sourceY >= height) continue

            slidingMax(coverage, sourceY * width, width, halfWidth, row, queue)

            val offset = y * width
            for (x in 0 until width) {
                result[offset + x] = max(result[offset + x], row[x])
            }
        }
    }
    return result
}

/**
 * Writes into [out] the largest of the values within [halfWidth] of each one of the row of [width]
 * values at [offset] of [source], with a monotonic queue of their indices in [queue].
 */
private fun slidingMax(source: FloatArray, offset: Int, width: Int, halfWidth: Int, out: FloatArray, queue: IntArray) {
    var head = 0
    var tail = 0
    var next = 0

    for (x in 0 until width) {
        // Every value up to halfWidth past x enters the queue, dropping the smaller ones before it.
        val last = min(width - 1, x + halfWidth)
        while (next <= last) {
            val value = source[offset + next]
            while (tail > head && source[offset + queue[tail - 1]] <= value) tail--
            queue[tail++] = next
            next++
        }
        // Those before the window leave it.
        while (queue[head] < x - halfWidth) head++

        out[x] = source[offset + queue[head]]
    }
}

/**
 * The radii of three box blurs that, one after another, approximate a Gaussian blur of a deviation
 * of [sigma] pixels.
 */
internal fun gaussianBoxes(sigma: Float): IntArray {
    if (sigma <= 0f) return IntArray(0)

    // Three boxes of widths around sqrt(12 sigma^2 / 3 + 1), some a size smaller so that their
    // variances add up to the Gaussian's.
    val ideal = sqrt(12f * sigma * sigma / 3f + 1f)
    var lower = floor(ideal).toInt()
    if (lower % 2 == 0) lower--
    val upper = lower + 2

    val smaller = ((12f * sigma * sigma - 3 * lower * lower - 12 * lower - 9) / (-4 * lower - 4)).roundToInt().coerceIn(0, 3)

    return IntArray(3) { i -> ((if (i < smaller) lower else upper) - 1) / 2 }
}

/**
 * Blurs [source] into [target] by a box of [radius] pixels along one axis, what's outside being
 * transparent.
 */
private fun boxBlur(source: FloatArray, target: FloatArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
    if (radius <= 0) {
        source.copyInto(target)
        return
    }

    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val step = if (horizontal) 1 else width
    val scale = 1f / (2 * radius + 1)

    for (line in 0 until lines) {
        val start = if (horizontal) line * width else line

        // The sum of the window around the first value.
        var sum = 0f
        for (i in 0..min(radius, length - 1)) {
            sum += source[start + i * step]
        }

        for (i in 0 until length) {
            target[start + i * step] = sum * scale

            val entering = i + radius + 1
            val leaving = i - radius
            if (entering < length) sum += source[start + entering * step]
            if (leaving >= 0) sum -= source[start + leaving * step]
        }
    }
}
