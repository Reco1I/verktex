package com.reco1l.verktex.ui.text

import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.px

/**
 * An effect drawn under the glyphs of a [Text], see [Text.effects].
 *
 * Effects are copies of the glyphs drawn offset in the effect's color: they need no offscreen pass,
 * and cost a draw of the text per copy, so the soft ones ([Glow], a [Shadow] with a softness) are
 * meant for short text such as headings, letters or numbers.
 */
sealed class TextEffect {

    /**
     * A copy of the text in [color] moved by [offsetX] and [offsetY], blurred by [softness] when it
     * isn't zero.
     */
    data class Shadow(
        val color: Color4 = Color4(0f, 0f, 0f, 0.5f),
        val offsetX: Dimension.Fixed = 0f.px,
        val offsetY: Dimension.Fixed = 2f.px,
        val softness: Dimension.Fixed = 0f.px,
    ) : TextEffect()

    /**
     * A line in [color] around the glyphs, [width] wide.
     */
    data class Outline(
        val color: Color4,
        val width: Dimension.Fixed,
    ) : TextEffect()

    /**
     * A halo in [color] fading out over [radius] around the glyphs, [intensity] times as strong.
     */
    data class Glow(
        val color: Color4,
        val radius: Dimension.Fixed,
        val intensity: Float = 1f,
    ) : TextEffect()
}

/**
 * A vertical gradient filling the glyphs from [top] at the top of each line to [bottom] at its bottom,
 * see [Text.fillGradient].
 */
data class TextGradient(
    val top: Color4,
    val bottom: Color4,
)
