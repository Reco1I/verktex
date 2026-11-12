package com.reco1l.verktex.data

import com.reco1l.verktex.Verktex
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.ui.Theme
import com.reco1l.verktex.ui.ThemeDependent
import com.reco1l.verktex.ui.themed

/**
 * What a component is filled with behind its content, see [com.reco1l.verktex.ui.UIComponent.background].
 * It follows the shape of the component, rounded by its radius, and lies under its border.
 *
 * It can be taken from the theme with `themed { Background.Color(palette.surface) }`, see [Themed].
 */
sealed class Background : ThemeDependent<Background> {

    /**
     * Whether the background draws anything.
     */
    abstract val isVisible: Boolean

    override val isThemed: Boolean
        get() = false

    override fun resolve(theme: Theme): Background = this


    /**
     * No background.
     */
    data object None : Background() {
        override val isVisible get() = false
    }

    /**
     * A background of a single [color], which can be [themed].
     */
    class Color(val color: Color4) : Background() {

        override val isVisible get() = color.alpha > 0f

        override val isThemed get() = color.isThemed

        override fun resolve(theme: Theme): Background = if (isThemed) Color(color.resolve(theme)) else this

        override fun equals(other: Any?) = this === other || other is Color && color == other.color

        override fun hashCode() = color.hashCode()

        override fun toString() = "Color($color)"
    }

    /**
     * A background of a texture [region], scaled to the component by [scaleType] and aligned by
     * [gravity] where it doesn't fill it, then tinted by [tint].
     */
    class Texture(
        val region: GraphicsTexture.Region,
        val scaleType: ScaleType = ScaleType.Crop,
        val gravity: Vec2 = Anchor.Center,
        val tint: Color4 = Color4.White,
    ) : Background() {

        override val isVisible get() = tint.alpha > 0f

        override val isThemed get() = tint.isThemed

        override fun resolve(theme: Theme): Background =
            if (isThemed) Texture(region, scaleType, gravity, tint.resolve(theme)) else this

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Texture
                && region == other.region
                && scaleType == other.scaleType
                && gravity == other.gravity
                && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = region.hashCode()
            result = 31 * result + scaleType.hashCode()
            result = 31 * result + gravity.hashCode()
            result = 31 * result + tint.hashCode()
            return result
        }

        override fun toString() = "Texture($region, $scaleType, $gravity, $tint)"
    }

    /**
     * A background taken from the theme, see [themed]. Themeable properties resolve it with the
     * theme of their component, elsewhere it's the one of [Verktex.theme].
     */
    class Themed(val block: Theme.() -> Background) : Background() {

        override val isVisible get() = Verktex.theme.block().isVisible

        override val isThemed get() = true

        override fun resolve(theme: Theme): Background = theme.block().resolve(theme)

        override fun equals(other: Any?) = this === other || other is Themed && block == other.block

        override fun hashCode() = block.hashCode()

        override fun toString() = "Themed(${Verktex.theme.block()})"
    }
}
