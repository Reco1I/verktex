package com.reco1l.verktex.data

import com.reco1l.verktex.Verktex
import com.reco1l.verktex.ui.Theme
import com.reco1l.verktex.ui.ThemeDependent
import com.reco1l.verktex.ui.isThemed
import com.reco1l.verktex.ui.themed

/**
 * A border drawn along the inner edge of a component, following its shape.
 *
 * Each side has its own color. A side whose color is null has none of its own and fades between
 * the sides next to it: with no color on the left and the right, the whole border fades from the
 * [top] color to the [bottom] one (e.g. a bezel lighter on top and darker at the bottom), and
 * likewise from [left] to [right]. Sides with colors meet on the diagonals from the corners.
 *
 * The border can be taken from the theme with `themed { cardStyle.elevation.border }`, see [Themed].
 */
open class Border(

    /**
     * The width of the border, the same on every side.
     */
    open val width: Dimension.Fixed,

    /**
     * The color of the top side, or null to fade between the left and right ones.
     */
    open val top: Color4?,

    /**
     * The color of the right side, or null to fade between the top and bottom ones.
     */
    open val right: Color4?,

    /**
     * The color of the bottom side, or null to fade between the left and right ones.
     */
    open val bottom: Color4?,

    /**
     * The color of the left side, or null to fade between the top and bottom ones.
     */
    open val left: Color4?,

) : ThemeDependent<Border> {

    /**
     * A border of a single [color], or one that fades from [color] at the top to [colorBottom] at
     * the bottom.
     */
    constructor(
        width: Dimension.Fixed,
        color: Color4 = Color4.Transparent,
        colorBottom: Color4 = color,
    ) : this(
        width,
        top = color,
        right = color.takeIf { it == colorBottom },
        bottom = colorBottom,
        left = color.takeIf { it == colorBottom },
    )


    /**
     * The color of the border, the one of the top side.
     */
    val color: Color4
        get() = top ?: left ?: right ?: bottom ?: Color4.Transparent

    /**
     * The color of the border at the bottom, which the sides without a color fade to from [color].
     */
    val colorBottom: Color4
        get() = bottom ?: color

    /**
     * Whether the border is visible at all.
     */
    val isVisible: Boolean
        get() = width.value > 0f && (isVisible(top) || isVisible(right) || isVisible(bottom) || isVisible(left))

    private fun isVisible(color: Color4?) = color != null && color.alpha > 0f


    /**
     * This border with every side of the given [color].
     */
    open fun withColor(color: Color4): Border = Border(width, color, color, color, color)

    /**
     * This border with the given [width].
     */
    open fun withWidth(width: Dimension.Fixed): Border = Border(width, top, right, bottom, left)


    override val isThemed: Boolean
        get() = width.isThemed || top.isThemed || right.isThemed || bottom.isThemed || left.isThemed

    override fun resolve(theme: Theme): Border {
        if (!isThemed) return this
        return Border(
            width.resolve(theme),
            top?.resolve(theme),
            right?.resolve(theme),
            bottom?.resolve(theme),
            left?.resolve(theme),
        )
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Border && other !is Themed && this !is Themed
            && width == other.width
            && top == other.top
            && right == other.right
            && bottom == other.bottom
            && left == other.left
    }

    override fun hashCode(): Int {
        var result = width.hashCode()
        result = 31 * result + top.hashCode()
        result = 31 * result + right.hashCode()
        result = 31 * result + bottom.hashCode()
        result = 31 * result + left.hashCode()
        return result
    }

    override fun toString() = "Border(width=$width, top=$top, right=$right, bottom=$bottom, left=$left)"


    /**
     * A border taken from the theme, see [themed]. Themeable properties resolve it with the theme of
     * their component, elsewhere its values are the ones in [Verktex.theme].
     */
    class Themed(val block: Theme.() -> Border) : Border(Dimension.Zero, null, null, null, null) {

        override val width get() = Verktex.theme.block().width
        override val top get() = Verktex.theme.block().top
        override val right get() = Verktex.theme.block().right
        override val bottom get() = Verktex.theme.block().bottom
        override val left get() = Verktex.theme.block().left

        override val isThemed get() = true

        override fun resolve(theme: Theme): Border = theme.block().resolve(theme)

        // Kept themed: the color or width replaces the one of the theme's border.
        override fun withColor(color: Color4): Border = Themed { block().withColor(color) }

        override fun withWidth(width: Dimension.Fixed): Border = Themed { block().withWidth(width) }

        override fun equals(other: Any?) = this === other || other is Themed && block == other.block

        override fun hashCode() = block.hashCode()

        override fun toString() = "Themed(${Verktex.theme.block()})"
    }


    companion object {
        val None = Border(Dimension.Zero, Color4.Transparent)
    }
}
