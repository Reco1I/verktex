package com.reco1l.verktex.ui

/**
 * The state of a component as the user interacts with it, see [Interactive.interactionState].
 */
enum class InteractionState {

    /**
     * Neither hovered, pressed nor disabled.
     */
    Idle,

    /**
     * A pointer is over it, see [InteractionController.isHovered].
     */
    Hovered,

    /**
     * It's being pressed, e.g. [com.reco1l.verktex.ui.container.ClickableContainer.isPressed].
     */
    Pressed,

    /**
     * It doesn't take input, e.g. [Button.isEnabled] is false.
     */
    Disabled
}

/**
 * The transforms a component runs when its [Interactive.interactionState] changes, e.g.
 *
 * ```
 * transitions {
 *     idle { scaleTo(1f, 0.15f, Easing.OutQuad) }
 *     hovered { scaleTo(1.05f, 0.15f, Easing.OutQuad) }
 *     pressed { scaleTo(0.95f, 0.08f) }
 * }
 * ```
 *
 * A state without a block runs the one of a related state: pressed falls back to hovered, and
 * every state to idle. Changing the state cancels the transforms the previous block started and
 * still running, the other transforms of the component are left alone. The block of the initial
 * state runs on the first update.
 */
class StateTransitions {

    private val blocks = arrayOfNulls<Component.() -> Unit>(InteractionState.entries.size)

    fun idle(block: Component.() -> Unit) = set(InteractionState.Idle, block)

    fun hovered(block: Component.() -> Unit) = set(InteractionState.Hovered, block)

    fun pressed(block: Component.() -> Unit) = set(InteractionState.Pressed, block)

    fun disabled(block: Component.() -> Unit) = set(InteractionState.Disabled, block)

    /**
     * Sets the transforms to run when the component enters [state].
     */
    operator fun set(state: InteractionState, block: Component.() -> Unit) {
        blocks[state.ordinal] = block
    }

    internal fun blockFor(state: InteractionState): (Component.() -> Unit)? {
        return blocks[state.ordinal] ?: when (state) {
            InteractionState.Pressed -> blocks[InteractionState.Hovered.ordinal] ?: blocks[InteractionState.Idle.ordinal]
            else -> blocks[InteractionState.Idle.ordinal]
        }
    }
}

/**
 * Sets the [InteractionController.transitions] of this component.
 */
inline fun Interactive.transitions(block: StateTransitions.() -> Unit) {
    interaction.transitions = StateTransitions().apply(block)
}
