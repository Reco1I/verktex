package com.reco1l.verktex.android

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import com.reco1l.verktex.Logger
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.fonts.Font
import com.reco1l.verktex.fonts.FontFamily
import kotlin.math.min

/**
 * An implementation of [FontFamily.Source] that creates [Font] instances using Android's [Typeface].
 *
 * A variable font with a `wght` axis (API 26 and up) draws every [Font.Weight] on that axis. Other
 * fonts pick the closest weight of their family, which Android fakes when the family has no
 * bold enough one.
 */
class AndroidFontSource(
    private val baseTypeface: Typeface,
) : FontFamily.Source {

    private val paint = Paint().apply {
        typeface = baseTypeface
        isAntiAlias = true
    }

    /**
     * Whether the typeface has a `wght` axis. Paint applies variation settings only when one of the
     * typeface's fonts has their axes, and says whether it did.
     */
    private val isVariable =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && paint.setFontVariationSettings(weightVariation(Font.Weight.Normal))


    override fun onCreateFont(descriptor: Font.Descriptor): Font {

        val isItalic = descriptor.style == Font.Style.Italic

        val typeface = when {
            // The axis gives the weight: asking Typeface for one would fake a bold over it.
            isVariable -> if (isItalic) Typeface.create(baseTypeface, Typeface.ITALIC) else baseTypeface

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                Typeface.create(baseTypeface, descriptor.weight.toAndroidTypefaceWeight(), isItalic)

            else -> {
                Logger.w(
                    "AndroidFontSource",
                    "Typeface weight is not supported on this Android version. Using default weight."
                )
                Typeface.create(baseTypeface, descriptor.style.toAndroidTypefaceStyle(descriptor.weight))
            }
        }

        val variationSettings = if (isVariable) weightVariation(descriptor.weight) else null

        paint.typeface = typeface
        paint.textSize = descriptor.size.toPixels()
        // Oblique is a skew, as Typeface has no oblique style.
        paint.textSkewX = if (descriptor.style == Font.Style.Oblique) OBLIQUE_SKEW else 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            paint.fontVariationSettings = variationSettings
        }

        val androidMetrics = paint.fontMetrics
        val metrics = Font.Metrics(
            ascent = androidMetrics.ascent,
            descent = androidMetrics.descent,
            leading = androidMetrics.leading,
        )

        // Pages are created as the font needs them, every page of this size.
        val pageSize = min(PAGE_SIZE, Verktex.graphics.device.capabilities.maxTextureSize)

        return AndroidFont(typeface, descriptor, metrics, pageSize, variationSettings)
    }


    companion object {

        /**
         * The width and height of a glyph atlas page. Each font variant (size, weight, style) has its
         * own pages and a page is only allocated once a glyph needs it, so this is kept small.
         */
        private const val PAGE_SIZE = 1024f

        internal const val OBLIQUE_SKEW = -0.25f

        private fun weightVariation(weight: Font.Weight) = "'wght' ${weight.value}"
    }

}
