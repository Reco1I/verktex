package com.reco1l.verktex.data

import com.reco1l.verktex.ui.Theme
import com.reco1l.verktex.ui.PaddingLayout
import com.reco1l.verktex.ui.ThemeDependent

/**
 * Represents the padding of a UI component, which defines the space between the component's
 * content and its boundaries.
 *
 * All padding values are represented as [Dimension] objects. Besides its sides, a padding can make
 * the component grow past its parent's padding ([coverParentPadding], a [CoverParent]) or into the
 * safe area of its scene ([extendIntoSafeArea], a [SafeArea]), its content staying where it would be
 * without growing. Both can be combined, e.g. `padding = coverParentPadding().extendIntoSafeArea()`.
 *
 * How a padding places its component is its [PaddingLayout], one per component, since a padding is a
 * value components share.
 */
open class Padding(
    val left: Dimension.Fixed = Dimension.Zero,
    val top: Dimension.Fixed = Dimension.Zero,
    val right: Dimension.Fixed = Dimension.Zero,
    val bottom: Dimension.Fixed = Dimension.Zero,
) : ThemeDependent<Padding> {

    constructor(all: Dimension.Fixed) : this(all, all, all, all)
    constructor(horizontal: Dimension.Fixed, vertical: Dimension.Fixed) : this(horizontal, vertical, horizontal, vertical)


    override val isThemed: Boolean
        get() = left.isThemed || top.isThemed || right.isThemed || bottom.isThemed

    override fun resolve(theme: Theme): Padding {
        if (!isThemed) return this
        return Padding(left.resolve(theme), top.resolve(theme), right.resolve(theme), bottom.resolve(theme))
    }


    /**
     * Returns this padding making the component cover its parent including the parent's padding, and
     * so does its content: the parent's padding is ignored. E.g. a component covering a scene reaches
     * the edges of the window, over its safe area; with [extendIntoSafeArea] too its content keeps out
     * of the safe area again, as a modal's.
     */
    open fun coverParentPadding(): Padding = CoverParent(this)

    /**
     * Returns this padding keeping the component's content out of the safe area of its scene: on the
     * sides it is placed against the safe area (or within it), the component grows to the edges of the
     * scene (the window, unless the scene is embedded), without moving its siblings, and its content is
     * inset by the safe area there, added to this padding. Its background covers the whole component.
     *
     * A bar laid out inside a scene's padding reaches the edges while its content stays where it was,
     * and a component already at the edges, e.g. a [com.reco1l.verktex.ui.Scene], which has it by
     * default, or one covering the scene's padding ([coverParentPadding]), is padded by the safe area.
     */
    open fun extendIntoSafeArea(): Padding = SafeArea(this)


    /**
     * Converts this [Padding] instance to a [Vec4] representation, where each side is converted to pixels.
     */
    fun toVec4(): Vec4 {
        return Vec4(
            x = left.toPixels(),
            y = top.toPixels(),
            z = right.toPixels(),
            w = bottom.toPixels()
        )
    }

    /**
     * The kind of [PaddingLayout] this padding places its component with: components keep theirs while the
     * kind stays the same.
     */
    internal open val layoutKind: Int
        get() = PaddingLayout.PLAIN

    /**
     * Creates the [PaddingLayout] placing a component with this padding.
     */
    internal open fun createLayout(): PaddingLayout = PaddingLayout.Plain


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Padding
            && layoutKind == other.layoutKind
            && left == other.left
            && top == other.top
            && right == other.right
            && bottom == other.bottom
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + top.hashCode()
        result = 31 * result + right.hashCode()
        result = 31 * result + bottom.hashCode()
        result = 31 * result + layoutKind
        return result
    }

    override fun toString() = "Padding($left, $top, $right, $bottom)"


    /**
     * The padding [base] covering the parent's padding, see [coverParentPadding].
     */
    class CoverParent internal constructor(val base: Padding) : Padding(base.left, base.top, base.right, base.bottom) {

        override fun resolve(theme: Theme): Padding = if (isThemed) CoverParent(base.resolve(theme)) else this

        override fun coverParentPadding(): Padding = this

        override val layoutKind get() = base.layoutKind or PaddingLayout.COVER_PARENT

        override fun createLayout(): PaddingLayout = PaddingLayout.CoverParent(base.createLayout())

        override fun toString() = "CoverParent($base)"
    }

    /**
     * The padding [base] growing into the safe area, see [extendIntoSafeArea]. It's always the outermost
     * behaviour, so the safe area is found from where the others place the component.
     */
    class SafeArea internal constructor(val base: Padding) : Padding(base.left, base.top, base.right, base.bottom) {

        override fun resolve(theme: Theme): Padding = if (isThemed) SafeArea(base.resolve(theme)) else this

        override fun coverParentPadding(): Padding = SafeArea(base.coverParentPadding())

        override fun extendIntoSafeArea(): Padding = this

        override val layoutKind get() = base.layoutKind or PaddingLayout.SAFE_AREA

        override fun createLayout(): PaddingLayout = PaddingLayout.SafeArea(base.createLayout())

        override fun toString() = "SafeArea($base)"
    }


    companion object {
        /**
         * A [Padding] instance with all sides set to zero.
         */
        val Zero = Padding()
    }
}


fun Vec4.toPadding(): Padding {
    return Padding(
        left = x.px,
        top = y.px,
        right = z.px,
        bottom = w.px
    )
}

/**
 * A padding without sides covering the parent's padding, see [Padding.coverParentPadding].
 */
fun coverParentPadding(): Padding {
    return Padding.Zero.coverParentPadding()
}

/**
 * A padding without sides growing into the safe area, see [Padding.extendIntoSafeArea].
 */
fun extendIntoSafeArea(): Padding {
    return Padding.Zero.extendIntoSafeArea()
}

fun horizontalPadding(horizontal: Dimension.Fixed): Padding {
    return Padding(horizontal, Dimension.Zero)
}

fun verticalPadding(vertical: Dimension.Fixed): Padding {
    return Padding(Dimension.Zero, vertical)
}