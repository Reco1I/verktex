package com.reco1l.verktex.ui.container

import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4

inline fun UIContainer.clipContainer(builder: UIClipContainer.() -> Unit): UIClipContainer {
    return UIClipContainer().apply(builder).also(::plusAssign)
}

/**
 * A container that shows only a fraction of itself, e.g. to reveal a texture bit by bit. It's sized and
 * lays out its children as any container, only what's drawn is cut: [clipFactorX] and [clipFactorY] are
 * the fractions of its width and height that are shown, kept at [clipAnchor].
 *
 * The clipping is a rectangle aligned to the screen, see [ClipMode.Bounds].
 */
open class UIClipContainer : UIContainer() {

    /**
     * The fraction of the width that is shown, from 0 to 1.
     */
    var clipFactorX = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    /**
     * The fraction of the height that is shown, from 0 to 1.
     */
    var clipFactorY = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    /**
     * The point of this container the shown area stays at as it shrinks, e.g. [Anchor.BottomLeft] to reveal
     * the content from the bottom.
     */
    var clipAnchor: Vec2 = Anchor.TopLeft


    init {
        clipping = ClipMode.Bounds
    }


    override fun clippingArea(): Vec4 {
        val area = super.clippingArea()
        val width = area.z - area.x
        val height = area.w - area.y

        val shownWidth = width * clipFactorX
        val shownHeight = height * clipFactorY

        val left = area.x + (width - shownWidth) * clipAnchor.x
        val top = area.y + (height - shownHeight) * clipAnchor.y

        return Vec4(left, top, left + shownWidth, top + shownHeight)
    }
}
