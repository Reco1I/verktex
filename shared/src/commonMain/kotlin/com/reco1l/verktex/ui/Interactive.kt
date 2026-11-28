package com.reco1l.verktex.ui

import com.reco1l.verktex.input.HoverEvent

/**
 * A component the user interacts with: it can be hovered (see [HoverEvent]) and has an [interactionState],
 * which its [InteractionController.transitions] and [InteractionController.onStateChange] react to. Only
 * interactive components are hovered: the hover goes to the topmost hoverable one under the pointer and
 * its interactive ancestors.
 */
interface Interactive {

    /**
     * The hover, transitions and state callbacks of this component.
     */
    val interaction: InteractionController

    /**
     * Whether a pointer over this component hovers it. By default when it reacts to it, with
     * [InteractionController.onHoverEnter], [InteractionController.onHoverExit],
     * [InteractionController.onStateChange] or [InteractionController.transitions].
     */
    val isHoverable: Boolean
        get() = interaction.hasReactions

    /**
     * The state of this component as the user interacts with it, which the transitions react to.
     */
    val interactionState: InteractionState
        get() = if (interaction.isHovered) InteractionState.Hovered else InteractionState.Idle

    /**
     * The built-in feedback of this component to [interactionState], run when no
     * [InteractionController.transitions] are set (e.g. [Button] scaling while pressed). Unlike those,
     * having them doesn't make the component [isHoverable].
     */
    val defaultTransitions: StateTransitions?
        get() = null

    /**
     * Whether [onInteractionStateChanged] is called even without transitions, e.g. for a [StateHighlight].
     */
    val tracksInteractionState: Boolean
        get() = false

    /**
     * Called when [interactionState] changes, after the transitions ran and before
     * [InteractionController.onStateChange].
     */
    fun onInteractionStateChanged(state: InteractionState) {}
}

/**
 * Follows the [Interactive.interactionState] of [owner], running its transitions when it changes. The
 * owner creates it, and it's updated with the component, before its transforms.
 */
class InteractionController(private val owner: Component) {

    private val interactive = owner as? Interactive
        ?: throw IllegalArgumentException("$owner isn't Interactive, it can't have an InteractionController")

    /**
     * Whether a pointer that isn't pressing (a mouse cursor) is over the component, or over a hovered
     * descendant. Touches never hover.
     */
    var isHovered = false
        internal set(value) {
            if (field == value) return
            field = value
            if (value) onHoverEnter?.invoke() else onHoverExit?.invoke()
        }

    /**
     * Called when the pointer starts hovering the component.
     */
    var onHoverEnter: (() -> Unit)? = null

    /**
     * Called when the pointer stops hovering the component.
     */
    var onHoverExit: (() -> Unit)? = null

    /**
     * The transforms to run when [Interactive.interactionState] changes, see [StateTransitions]. They
     * replace the built-in feedback of the components that have one, their
     * [Interactive.defaultTransitions].
     */
    var transitions: StateTransitions? = null
        set(value) {
            field = value
            lastState = null
        }

    /**
     * Called when [Interactive.interactionState] changes, after [transitions] ran.
     */
    var onStateChange: ((InteractionState) -> Unit)? = null
        set(value) {
            field = value
            lastState = null
        }

    /**
     * Whether something reacts to the hover or the state, see [Interactive.isHoverable].
     */
    internal val hasReactions: Boolean
        get() = onHoverEnter != null || onHoverExit != null || onStateChange != null || transitions != null

    // Null until the first update with transitions, which runs the ones of the initial state.
    private var lastState: InteractionState? = null

    // The transforms the transition of the current state started.
    private var stateTransition: TransitionSlot? = null


    internal fun update() {
        val transitions = transitions ?: interactive.defaultTransitions
        if (transitions == null && onStateChange == null && !interactive.tracksInteractionState) {
            return
        }

        val state = interactive.interactionState
        if (state == lastState) {
            return
        }
        lastState = state

        val block = transitions?.blockFor(state)
        if (block != null) {
            val slot = stateTransition ?: owner.transitionSlot().also { stateTransition = it }
            slot.run(block)
        } else {
            stateTransition?.cancel()
        }

        interactive.onInteractionStateChanged(state)
        onStateChange?.invoke(state)
    }
}
