package com.reco1l.verktex.ui

/**
 * The state of a component as the user interacts with it, see [UIComponent.interactionState].
 */
enum class InteractionState {

    /**
     * Neither hovered, pressed nor disabled.
     */
    Idle,

    /**
     * A pointer is over it, see [UIComponent.isHovered].
     */
    Hovered,

    /**
     * It's being pressed, e.g. [com.reco1l.verktex.ui.container.UIClickableContainer.isPressed].
     */
    Pressed,

    /**
     * It doesn't take input, e.g. [UIButton.isEnabled] is false.
     */
    Disabled
}

/**
 * The transforms a component runs when its [UIComponent.interactionState] changes, e.g.
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

    private val blocks = arrayOfNulls<UIComponent.() -> Unit>(InteractionState.entries.size)

    fun idle(block: UIComponent.() -> Unit) = set(InteractionState.Idle, block)

    fun hovered(block: UIComponent.() -> Unit) = set(InteractionState.Hovered, block)

    fun pressed(block: UIComponent.() -> Unit) = set(InteractionState.Pressed, block)

    fun disabled(block: UIComponent.() -> Unit) = set(InteractionState.Disabled, block)

    /**
     * Sets the transforms to run when the component enters [state].
     */
    operator fun set(state: InteractionState, block: UIComponent.() -> Unit) {
        blocks[state.ordinal] = block
    }

    internal fun blockFor(state: InteractionState): (UIComponent.() -> Unit)? {
        return blocks[state.ordinal] ?: when (state) {
            InteractionState.Pressed -> blocks[InteractionState.Hovered.ordinal] ?: blocks[InteractionState.Idle.ordinal]
            else -> blocks[InteractionState.Idle.ordinal]
        }
    }
}

/**
 * Sets the [UIComponent.transitions] of this component.
 */
inline fun UIComponent.transitions(block: StateTransitions.() -> Unit) {
    transitions = StateTransitions().apply(block)
}
