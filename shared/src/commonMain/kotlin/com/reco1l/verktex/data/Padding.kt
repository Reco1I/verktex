package com.reco1l.verktex.data

import com.reco1l.verktex.ui.Theme
import com.reco1l.verktex.ui.ThemeDependent

/**
 * Represents the padding of a UI component, which defines the space between the component's
 * content and its boundaries.
 *
 * All padding values are represented as [Dimension] objects. Besides its sides, a padding can make
 * the component grow past its parent's padding ([coverParentPadding]) or into the safe area of its
 * scene ([extendIntoSafeArea]), its content staying where it would be without growing. Both can be
 * combined, e.g. `padding = coverParentPadding().extendIntoSafeArea()`.
 */
data class Padding(
    val left: Dimension.Fixed = Dimension.Zero,
    val top: Dimension.Fixed = Dimension.Zero,
    val right: Dimension.Fixed = Dimension.Zero,
    val bottom: Dimension.Fixed = Dimension.Zero,

    /**
     * Whether the component covers its parent's padding, see [coverParentPadding].
     */
    val coversParentPadding: Boolean = false,

    /**
     * Whether the component grows into the safe area of its scene, see [extendIntoSafeArea].
     */
    val extendsIntoSafeArea: Boolean = false
) : ThemeDependent<Padding> {

    constructor(all: Dimension.Fixed) : this(all, all, all, all)
    constructor(horizontal: Dimension.Fixed, vertical: Dimension.Fixed) : this(horizontal, vertical, horizontal, vertical)


    override val isThemed: Boolean
        get() = left.isThemed || top.isThemed || right.isThemed || bottom.isThemed

    override fun resolve(theme: Theme): Padding {
        if (!isThemed) return this
        return copy(left = left.resolve(theme), top = top.resolve(theme), right = right.resolve(theme), bottom = bottom.resolve(theme))
    }


    /**
     * Returns this padding making the component cover its parent including the parent's padding, and
     * so does its content: the parent's padding is ignored. E.g. a component covering a scene reaches
     * the edges of the window, over its safe area; with [extendIntoSafeArea] too its content keeps out
     * of the safe area again, as a modal's.
     */
    fun coverParentPadding(): Padding {
        return copy(coversParentPadding = true)
    }

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
    fun extendIntoSafeArea(): Padding {
        return copy(extendsIntoSafeArea = true)
    }


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