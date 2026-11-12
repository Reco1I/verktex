package com.reco1l.verktex.data

import com.reco1l.verktex.Verktex
import com.reco1l.verktex.ui.Theme
import com.reco1l.verktex.ui.ThemeDependent
import com.reco1l.verktex.ui.themed

/**
 * The radius of each corner of a component, see [com.reco1l.verktex.ui.UIComponent.radius]. A
 * radius larger than half the smaller side of the component is reduced to it.
 *
 * A corner can be a [themed] dimension, and the whole value can be taken from the theme with
 * `themed { CornerRadius(radius.md) }`, see [Themed].
 */
open class CornerRadius(
    open val topLeft: Dimension.Fixed,
    open val topRight: Dimension.Fixed,
    open val bottomRight: Dimension.Fixed,
    open val bottomLeft: Dimension.Fixed,
) : ThemeDependent<CornerRadius> {

    /**
     * The same radius on every corner.
     */
    constructor(all: Dimension.Fixed) : this(all, all, all, all)


    override val isThemed: Boolean
        get() = topLeft.isThemed || topRight.isThemed || bottomRight.isThemed || bottomLeft.isThemed

    override fun resolve(theme: Theme): CornerRadius {
        if (!isThemed) return this
        return CornerRadius(topLeft.resolve(theme), topRight.resolve(theme), bottomRight.resolve(theme), bottomLeft.resolve(theme))
    }


    /**
     * Whether no corner is rounded.
     */
    val isZero: Boolean
        get() = topLeft.value <= 0f && topRight.value <= 0f && bottomRight.value <= 0f && bottomLeft.value <= 0f


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is CornerRadius && other !is Themed && this !is Themed
            && topLeft == other.topLeft
            && topRight == other.topRight
            && bottomRight == other.bottomRight
            && bottomLeft == other.bottomLeft
    }

    override fun hashCode(): Int {
        var result = topLeft.hashCode()
        result = 31 * result + topRight.hashCode()
        result = 31 * result + bottomRight.hashCode()
        result = 31 * result + bottomLeft.hashCode()
        return result
    }

    override fun toString() = "CornerRadius(topLeft=$topLeft, topRight=$topRight, bottomRight=$bottomRight, bottomLeft=$bottomLeft)"


    /**
     * A corner radius taken from the theme, see [themed]. Themeable properties resolve it with the
     * theme of their component, elsewhere its corners are the ones in [Verktex.theme].
     */
    class Themed(val block: Theme.() -> CornerRadius) : CornerRadius(Dimension.Zero) {

        override val topLeft get() = Verktex.theme.block().topLeft
        override val topRight get() = Verktex.theme.block().topRight
        override val bottomRight get() = Verktex.theme.block().bottomRight
        override val bottomLeft get() = Verktex.theme.block().bottomLeft

        override val isThemed get() = true

        override fun resolve(theme: Theme): CornerRadius = theme.block().resolve(theme)

        override fun equals(other: Any?) = this === other || other is Themed && block == other.block

        override fun hashCode() = block.hashCode()

        override fun toString() = "Themed(${Verktex.theme.block()})"
    }


    companion object {

        /**
         * No rounded corner.
         */
        val Zero = CornerRadius(Dimension.Zero)

        /**
         * Rounds the top corners only.
         */
        fun top(radius: Dimension.Fixed) = CornerRadius(radius, radius, Dimension.Zero, Dimension.Zero)

        /**
         * Rounds the bottom corners only.
         */
        fun bottom(radius: Dimension.Fixed) = CornerRadius(Dimension.Zero, Dimension.Zero, radius, radius)

        /**
         * Rounds the left corners only.
         */
        fun left(radius: Dimension.Fixed) = CornerRadius(radius, Dimension.Zero, Dimension.Zero, radius)

        /**
         * Rounds the right corners only.
         */
        fun right(radius: Dimension.Fixed) = CornerRadius(Dimension.Zero, radius, radius, Dimension.Zero)
    }
}
