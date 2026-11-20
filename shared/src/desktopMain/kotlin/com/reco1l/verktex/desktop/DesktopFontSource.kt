package com.reco1l.verktex.desktop

import com.reco1l.verktex.Verktex
import com.reco1l.verktex.fonts.Font
import com.reco1l.verktex.fonts.FontFamily
import com.reco1l.verktex.graphics.Texture
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FT_MM_Var
import org.lwjgl.util.freetype.TT_OS2
import org.lwjgl.util.freetype.FreeType.*

/**
 * A [FontFamily.Source] over a TrueType or OpenType (CFF and CFF2 included) file, rasterised with
 * FreeType. Of a collection (.ttc), the first font is used.
 *
 * A variable font with a `wght` axis draws every [Font.Weight] on it, its other axes at their
 * defaults, as on Android. Other fonts synthesise what they don't have as Android does (minikin's
 * font fakery): a bold when the weight is at least semibold and two grades above the font's own, and
 * an italic, a skew as [Font.Style.Oblique] always is, when the font is upright.
 */
class DesktopFontSource(data: ByteArray) : FontFamily.Source {

    // FreeType reads the file in place, it must live as long as the face.
    private val fontData: ByteBuffer = ByteBuffer.allocateDirect(data.size).put(data).flip()

    // A library of its own: FreeType's objects aren't thread safe, and a source is used under its lock.
    private val library: Long

    internal val face: FT_Face

    // In font units, scaled per font.
    private val unitsPerEm: Int
    private val ascent: Int
    private val descent: Int
    private val lineGap: Int

    /**
     * The default design coordinates of every axis of a variable font, in 16.16 fixed point, or
     * null for a font without a `wght` axis.
     */
    private val defaultCoordinates: LongArray?

    // The index of the wght axis among them, -1 without one, and its range.
    private val weightAxis: Int
    private val weightRange: ClosedFloatingPointRange<Float>

    // The font's own weight (OS/2's usWeightClass) and slant, which the synthesis starts from.
    private val ownWeight: Int
    private val isOwnItalic: Boolean


    init {
        MemoryStack.stackPush().use { stack ->
            val pointer = stack.mallocPointer(1)
            check(FT_Init_FreeType(pointer) == FT_Err_Ok) { "FreeType couldn't start." }
            library = pointer[0]

            if (FT_New_Memory_Face(library, fontData, 0, pointer) != FT_Err_Ok) {
                FT_Done_FreeType(library)
                throw IllegalArgumentException("Not a TrueType or OpenType font.")
            }
            face = FT_Face.create(pointer[0])

            var coordinates: LongArray? = null
            var axis = -1
            var range = 0f..0f

            val hasVariations = face.face_flags() and FT_FACE_FLAG_MULTIPLE_MASTERS.toLong() != 0L
            if (hasVariations && FT_Get_MM_Var(face, pointer) == FT_Err_Ok) {
                val variations = FT_MM_Var.create(pointer[0])
                val axes = variations.axis()
                coordinates = LongArray(variations.num_axis()) { axes[it].def() }
                for (i in coordinates.indices) {
                    if (axes[i].tag() == WGHT_TAG) {
                        axis = i
                        range = axes[i].minimum() / FIXED_ONE..axes[i].maximum() / FIXED_ONE
                    }
                }
                FT_Done_MM_Var(library, variations)
            }

            // Only the weight is applied: a font with other axes alone draws as a static one.
            defaultCoordinates = coordinates.takeIf { axis >= 0 }
            weightAxis = axis
            weightRange = range
        }

        // The hhea metrics, unscaled and unrounded, as Android's.
        unitsPerEm = face.units_per_EM().toInt()
        ascent = face.ascender().toInt()
        descent = face.descender().toInt()
        lineGap = face.height() - (ascent - descent)

        val os2 = FT_Get_Sfnt_Table(face, FT_SFNT_OS2)
        ownWeight = if (os2 != 0L) TT_OS2.create(os2).usWeightClass().toInt() and 0xFFFF else Font.Weight.Normal.value
        isOwnItalic = face.style_flags() and FT_STYLE_FLAG_ITALIC.toLong() != 0L
    }


    /**
     * Whether the font has a `wght` axis, every weight drawn on it.
     */
    val isVariable
        get() = weightAxis >= 0


    /**
     * Whether [descriptor] is drawn emboldened: as minikin, when its weight is at least semibold and
     * two grades (200) above the font's own. A variable font draws its weight on its axis instead.
     */
    internal fun isFakeBold(descriptor: Font.Descriptor) =
        !isVariable && descriptor.weight >= Font.Weight.SemiBold && descriptor.weight.value - ownWeight >= 200

    /**
     * Whether [descriptor] is drawn slanted: oblique always is, italic when the font is upright.
     */
    internal fun isSlanted(descriptor: Font.Descriptor) =
        descriptor.style == Font.Style.Oblique || (descriptor.style == Font.Style.Italic && !isOwnItalic)


    override fun onCreateFont(descriptor: Font.Descriptor): Font {
        // Like Paint.textSize on Android, the size is the em, not the line height.
        val scale = descriptor.size.toPixels() / unitsPerEm

        val metrics = Font.Metrics(
            // Above the baseline is negative, as on Android.
            ascent = -ascent * scale,
            descent = -descent * scale,
            leading = lineGap * scale,
        )

        val pageSize = min(PAGE_SIZE, Verktex.graphics.device.capabilities.maxTextureSize)

        return DesktopFont(this, descriptor, metrics, pageSize)
    }


    /**
     * Sets the face up for the glyphs of [descriptor]: its size and, of a variable font, its weight.
     * Called under the source's lock, as every font of the source shares the face.
     */
    internal fun prepare(descriptor: Font.Descriptor, stack: MemoryStack) {
        // In 26.6 fixed point at 72 dpi, so that a point is a pixel.
        FT_Set_Char_Size(face, 0, (descriptor.size.toPixels() * 64).roundToLong(), 72, 72)

        val defaults = defaultCoordinates ?: return
        val coordinates = stack.mallocCLong(defaults.size)
        for (i in defaults.indices) {
            coordinates.put(i, defaults[i])
        }
        val weight = descriptor.weight.value.toFloat().coerceIn(weightRange)
        coordinates.put(weightAxis, (weight * FIXED_ONE).roundToLong())
        FT_Set_Var_Design_Coordinates(face, coordinates)
    }


    private companion object {
        // See AndroidFontSource.PAGE_SIZE.
        const val PAGE_SIZE = 1024f

        // 1.0 in FreeType's 16.16 fixed point.
        const val FIXED_ONE = 65536f

        val WGHT_TAG = ('w'.code.toLong() shl 24) or ('g'.code.toLong() shl 16) or ('h'.code.toLong() shl 8) or 't'.code.toLong()
    }
}

/**
 * A font of a [DesktopFontSource] at one size, weight and style.
 */
class DesktopFont(
    private val source: DesktopFontSource,
    override val descriptor: Descriptor,
    override val metrics: Metrics,
    private val pageSize: Float,
) : Font() {

    override val isKerningSupported = false

    // The horizontal shift per pixel above the baseline, Android's textSkewX of -0.25, for oblique
    // and synthesised italics.
    private val skew = if (source.isSlanted(descriptor)) OBLIQUE_SKEW else 0f

    // How much a synthesised bold widens the outlines, in 26.6 fixed point, 0 without one. Skia's,
    // which Android uses: an em over 24.
    private val emboldenStrength = if (source.isFakeBold(descriptor)) (descriptor.size.toPixels() * 64 / EMBOLDEN_DIVISOR).roundToLong() else 0L


    override fun onCreatePage(): Texture {
        return Verktex.graphics.createTexture(pageSize, pageSize)
    }

    override fun onCreateGlyph(codePoint: Int): Glyph = synchronized(source) {
        MemoryStack.stackPush().use { stack ->
            source.prepare(descriptor, stack)

            val face = source.face
            // Light hinting, as Android's slight: snapped vertically only, advances keep their widths.
            if (FT_Load_Char(face, codePoint.toLong(), FT_LOAD_TARGET_LIGHT) != FT_Err_Ok) {
                return Glyph(codePoint = codePoint, advance = 0f)
            }

            val slot = face.glyph()!!
            // Unhinted and fractional, in 16.16 fixed point, as Paint.measureText's. A synthesised bold
            // keeps it, as on Android.
            val advance = slot.linearHoriAdvance() / 65536f

            if (slot.format() == FT_GLYPH_FORMAT_OUTLINE) {
                if (emboldenStrength > 0) {
                    FT_Outline_Embolden(slot.outline(), emboldenStrength)
                }
                if (FT_Render_Glyph(slot, FT_RENDER_MODE_LIGHT) != FT_Err_Ok) {
                    return Glyph(codePoint = codePoint, advance = advance)
                }
            }

            val bitmap = slot.bitmap()
            val width = bitmap.width()
            val height = bitmap.rows()

            if (width <= 0 || height <= 0 || bitmap.pixel_mode().toInt() != FT_PIXEL_MODE_GRAY) {
                // No visible representation (e.g. a space), only its advance.
                return Glyph(codePoint = codePoint, advance = advance)
            }

            // FreeType's rows may be padded, or go upwards with a negative pitch.
            val pitch = bitmap.pitch()
            val rows = bitmap.buffer(abs(pitch) * height)!!
            val coverage = ByteBuffer.allocateDirect(width * height)
            for (y in 0 until height) {
                val row = if (pitch >= 0) y * pitch else (height - 1 - y) * -pitch
                for (x in 0 until width) {
                    coverage.put(y * width + x, rows.get(row + x))
                }
            }

            // Above the baseline is negative, like Rect.top of Paint.getTextBounds.
            val top = -slot.bitmap_top()
            val left = slot.bitmap_left()

            val glyph = if (skew == 0f) {
                GlyphBitmap(coverage, width, height, left)
            } else {
                skewed(coverage, width, height, left, top)
            }

            // The atlas packs glyphs edge to edge, and text is drawn at fractional positions, where
            // linear filtering reads half a texel past the glyph. A transparent border keeps it from
            // reading the neighbouring glyph, the box grows by it on every side.
            Glyph(
                codePoint = codePoint,
                advance = advance,
                width = (glyph.width + 2 * BORDER).toFloat(),
                height = (height + 2 * BORDER).toFloat(),
                bearingX = (glyph.left - BORDER).toFloat(),
                bearingY = (top - BORDER).toFloat(),
                region = addGlyphSource(glyph.toTextureSource()),
            )
        }
    }

    override fun getKerning(first: Int, second: Int): Float {
        // Off as on Android, so text is laid out the same on both.
        return 0f
    }


    private class GlyphBitmap(val coverage: ByteBuffer, val width: Int, val height: Int, val left: Int) {

        // White with the coverage as alpha, premultiplied as textures are, inside a transparent border
        // of BORDER pixels.
        fun toTextureSource(): PixelsTextureSource {
            val outerWidth = width + 2 * BORDER
            val outerHeight = height + 2 * BORDER

            // Zeroed: the border is transparent.
            val pixels = ByteBuffer.allocateDirect(outerWidth * outerHeight * 4).order(ByteOrder.nativeOrder())
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val offset = ((y + BORDER) * outerWidth + x + BORDER) * 4
                    val alpha = coverage.get(y * width + x)
                    pixels.put(offset, alpha)
                    pixels.put(offset + 1, alpha)
                    pixels.put(offset + 2, alpha)
                    pixels.put(offset + 3, alpha)
                }
            }
            return PixelsTextureSource(outerWidth, outerHeight, pixels)
        }
    }

    // Shears the bitmap as Paint.textSkewX does, x' = x + skew * y with y relative to the baseline
    // (negative above it), so the top leans right.
    private fun skewed(coverage: ByteBuffer, width: Int, height: Int, left: Int, top: Int): GlyphBitmap {
        fun shift(row: Int) = skew * (top + row + 0.5f)

        val minShift = min(shift(0), shift(height - 1))
        val maxShift = max(shift(0), shift(height - 1))
        val newLeft = floor(left + minShift).toInt()
        val newWidth = ceil(left + width + maxShift).toInt() - newLeft

        val result = ByteBuffer.allocateDirect(newWidth * height)

        for (row in 0 until height) {
            // Where the source's column 0 lands in the result, split into whole and fractional parts.
            val offset = left + shift(row) - newLeft
            val whole = floor(offset).toInt()
            val fraction = offset - whole

            for (x in 0 until newWidth) {
                val source = x - whole
                // Linear interpolation between the two source pixels covering this one.
                val a = sample(coverage, width, row, source)
                val b = sample(coverage, width, row, source - 1)
                val value = a * (1f - fraction) + b * fraction
                result.put(row * newWidth + x, value.toInt().coerceIn(0, 255).toByte())
            }
        }

        return GlyphBitmap(result, newWidth, height, newLeft)
    }

    private fun sample(coverage: ByteBuffer, width: Int, row: Int, column: Int): Float {
        if (column < 0 || column >= width) return 0f
        return (coverage.get(row * width + column).toInt() and 0xFF).toFloat()
    }


    private companion object {
        const val OBLIQUE_SKEW = -0.25f

        // Skia's SK_OUTLINE_EMBOLDEN_DIVISOR: a synthesised bold widens outlines by the em over it.
        const val EMBOLDEN_DIVISOR = 24f

        // The transparent pixels around every glyph, see onCreateGlyph.
        const val BORDER = 1
    }
}
