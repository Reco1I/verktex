package com.reco1l.verktex

import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.dip

/**
 * Options of the engine, given by the game host ([GameHost.options]) and read once when the engine
 * is initialized, see [Verktex.options].
 */
data class VerktexOptions(

    /**
     * Whether a right click works as a long press: it fires the
     * [com.reco1l.verktex.ui.container.UIClickableContainer.onActionLongPress] of the component when
     * released inside it, without waiting, and is ignored by components without one. Touches never
     * press the right button, so this only affects mice.
     */
    val rightClickAsLongPress: Boolean = true,

    /**
     * The distance a notch of a mouse wheel scrolls a
     * [com.reco1l.verktex.ui.container.UIScrollableContainer].
     */
    val wheelScrollStep: Dimension.Fixed = 64f.dip,
)
