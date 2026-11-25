package com.reco1l.verktex.android

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.fonts.Font
import com.reco1l.verktex.fonts.GlyphCoverage
import com.reco1l.verktex.graphics.Texture
import androidx.core.graphics.createBitmap
import java.nio.ByteBuffer

class AndroidFont(
    private val typeface: Typeface,
    override val descriptor: Descriptor,
    override val metrics: Metrics,
    /**
     * The width and height of every atlas page of this font, in pixels.
     */
    private val pageSize: Float,
    /**
     * The font variation settings the glyphs are drawn with, e.g. `'wght' 550` for a variable font,
     * or null for a static one.
     */
    private val variationSettings: String? = null,
) : Font() {

    override val isKerningSupported = false


    private val paint = Paint().also { paint ->
        paint.typeface = typeface
        paint.textSize = descriptor.size.toPixels()
        paint.isAntiAlias = true
        paint.color = android.graphics.Color.WHITE

        // Oblique style is not directly supported by Android's Typeface, so we apply a skew to
        // simulate it.
        if (descriptor.style == Style.Oblique) {
            paint.textSkewX = AndroidFontSource.OBLIQUE_SKEW
        }

        if (variationSettings != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            paint.fontVariationSettings = variationSettings
        }
    }

    private val canvas = android.graphics.Canvas()
    private val rect = Rect()


    override fun onCreatePage(): Texture {
        return Verktex.graphics.createTexture(pageSize, pageSize)
    }

    override fun onCreateGlyph(codePoint: Int): Glyph {
        val characters = Character.toChars(codePoint)

        paint.getTextBounds(characters, 0, characters.size, rect)

        val advance = paint.measureText(characters, 0, characters.size)

        val width = rect.width().toFloat()
        val height = rect.height().toFloat()
        val bearingX = rect.left.toFloat()
        val bearingY = rect.top.toFloat()

        if (rect.isEmpty) {
            // If the character has no visible representation (like a space), we still need to create a glyph
            // with the correct advance width, but we won't create a bitmap for it.
            return Glyph(
                codePoint = codePoint,
                advance = advance,
            )
        }

        val bitmap = createBitmap(rect.width(), rect.height())

        canvas.setBitmap(bitmap)
        canvas.drawText(String(characters), -bearingX, -bearingY, paint)

        return Glyph(
            codePoint = codePoint,
            advance = advance,
            width = width,
            height = height,
            bearingX = bearingX,
            bearingY = bearingY,
            region = addGlyphSource(AndroidTextureSource(bitmap)),
        )
    }

    override fun createCoverage(codePoint: Int): GlyphCoverage? {
        val characters = Character.toChars(codePoint)

        paint.getTextBounds(characters, 0, characters.size, rect)
        if (rect.isEmpty) return null

        val width = rect.width()
        val height = rect.height()

        // The glyph as onCreateGlyph draws it, its alpha alone.
        val bitmap = createBitmap(width, height, Bitmap.Config.ALPHA_8)
        canvas.setBitmap(bitmap)
        canvas.drawText(String(characters), -rect.left.toFloat(), -rect.top.toFloat(), paint)
        canvas.setBitmap(null)

        // Rows can be padded past the width.
        val rowBytes = bitmap.rowBytes
        val buffer = ByteBuffer.allocate(rowBytes * height)
        bitmap.copyPixelsToBuffer(buffer)
        bitmap.recycle()

        val alpha = ByteArray(width * height)
        for (y in 0 until height) {
            buffer.position(y * rowBytes)
            buffer.get(alpha, y * width, width)
        }

        return GlyphCoverage(width, height, rect.left, rect.top, alpha)
    }

    override fun getKerning(first: Int, second: Int): Float {
        // Android's Paint does not provide kerning information, so we return 0.
        return 0f
    }
}