package com.reco1l.verktex.ui.container

import com.reco1l.verktex.Verktex
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.ui.InteractionState
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.FrameClock


inline fun UIContainer.clickableContainer(builder: UIClickableContainer.() -> Unit) = UIClickableContainer().apply(builder).also { +it }

open class UIClickableContainer : UIContainer() {

    /**
     * Whether the button is being pressed or not.
     */
    var isPressed = false
        private set

    override val isHoverable
        get() = true

    override val interactionState
        get() = if (isPressed) InteractionState.Pressed else super.interactionState

    //region Events

    /**
     * The action to perform when the button is pressed.
     */
    var onActionDown: (() -> Unit)? = null

    /**
     * The action to perform when the button is released.
     */
    var onActionUp: (() -> Unit)? = null

    /**
     * The action to perform when the button is cancelled.
     */
    var onActionCancel: (() -> Unit)? = null

    /**
     * The action to perform when the button is long pressed, or right clicked while
     * [com.reco1l.verktex.VerktexOptions.rightClickAsLongPress] is on.
     */
    var onActionLongPress: (() -> Unit)? = null

    //endregion

    // Seconds the current press lasted so far, counted with the game clock.
    private var pressedTime = 0f

    // The finger pressing this container, others must not release or cancel it.
    private var pressPointerId = -1

    // Whether the current press is a right click standing for a long press, see
    // VerktexOptions.rightClickAsLongPress. It fires on release instead of after a delay.
    private var isRightClickPress = false


    override fun onInputEvent(event: InputEvent): Boolean {

        if (super.onInputEvent(event)) {
            return true
        }

        // Other events (e.g. keys) are not handled by clickable containers.
        if (event !is PointerEvent) {
            return false
        }

        if (event.action != PointerEvent.Action.Down && (!isPressed || event.pointerId != pressPointerId)) {
            return false
        }

        return when (event.action) {
            PointerEvent.Action.Down -> {
                // Already held by another finger.
                if (isPressed) return true

                val isRightClick = event.button == PointerEvent.Button.Right && Verktex.options.rightClickAsLongPress

                // A right click isn't a click, only the components with a long press take it.
                if (isRightClick && onActionLongPress == null) return false

                isRightClickPress = isRightClick
                isPressed = true
                pressPointerId = event.pointerId
                onActionDown?.invoke()
                pressedTime = 0f
                true
            }

            PointerEvent.Action.Up -> {
                if (!containsScenePoint(event.x, event.y)) {
                    onActionCancel?.invoke()
                } else if (isRightClickPress) {
                    onActionLongPress?.invoke()
                } else {
                    onActionUp?.invoke()
                }
                isPressed = false
                true
            }

            PointerEvent.Action.Move -> true

            // Cancel events are dispatched to every component, so they must not be consumed.
            PointerEvent.Action.Unknown, PointerEvent.Action.Cancel -> {
                onActionCancel?.invoke()
                isPressed = false
                false
            }
        }
    }

    override fun onUpdate(clock: FrameClock) {
        if (isPressed) {
            pressedTime += clock.elapsedFrameTime

            if (!isRightClickPress && onActionLongPress != null && pressedTime >= LONG_PRESS_DURATION) {
                isPressed = false
                onActionLongPress?.invoke()
            }
        }
        super.onUpdate(clock)
    }


    companion object {
        private const val LONG_PRESS_DURATION = 0.5f
    }

}