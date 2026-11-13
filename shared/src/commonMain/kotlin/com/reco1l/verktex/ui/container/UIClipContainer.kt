package com.reco1l.verktex.ui.container

import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.ui.UIComponent

inline fun UIContainer.clipContainer(builder: UIClipContainer.() -> Unit): UIClipContainer {
    return UIClipContainer().apply(builder).also(::plusAssign)
}

/**
 * A container that shows only the part of its children inside its bounds, which keep their own size
 * instead of being limited to this container's: a child of a fixed size or sized by its content can be
 * larger than it, and only what's inside is drawn, e.g. to reveal a texture as the container grows.
 * Children sized relative to the available space (e.g. with `fill()`) are still sized to this container.
 *
 * The clipping is a rectangle aligned to the screen, see [ClipMode.Bounds].
 */
open class UIClipContainer : UIContainer() {

    init {
        clipping = ClipMode.Bounds
    }

    override fun constraintsForChild(child: UIComponent, constraints: LayoutConstraints): LayoutConstraints {
        val childConstraints = super.constraintsForChild(child, constraints)

        return childConstraints.copy(
            maxWidth = if (child.width is Dimension.AvailableSizeFraction) childConstraints.maxWidth else Float.POSITIVE_INFINITY,
            maxHeight = if (child.height is Dimension.AvailableSizeFraction) childConstraints.maxHeight else Float.POSITIVE_INFINITY
        )
    }
}
