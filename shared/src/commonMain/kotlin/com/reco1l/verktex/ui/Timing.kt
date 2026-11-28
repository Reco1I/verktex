package com.reco1l.verktex.ui

import com.reco1l.verktex.time.Clock

/**
 * The time of a [Component] ([Component.timing]): the clock pacing its transforms and its choreography,
 * and those of its children.
 */
class Timing internal constructor(private val owner: Component) {

    /**
     * The clock pacing the component and its children. When null it's inherited from the parent, falling
     * back to the clock of the engine.
     */
    var source: Clock? = null

    /**
     * The clock actually pacing the component, null if it's the one of the engine.
     */
    val active: Clock?
        get() = source ?: owner.parent?.timing?.active

    /**
     * The current time of the clock pacing the component, in seconds.
     */
    val time: Float
        get() = active?.time ?: engineTime

    /**
     * The time of the engine's clock at the last update of the component.
     */
    internal var engineTime = 0f
}
