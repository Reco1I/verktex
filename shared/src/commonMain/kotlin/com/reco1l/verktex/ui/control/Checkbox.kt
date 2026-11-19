package com.reco1l.verktex.ui.control

import com.reco1l.verktex.ui.StateHighlight
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.*
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.ui.InteractionState
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.ui.FAIcon
import com.reco1l.verktex.ui.Icon
import com.reco1l.verktex.ui.animatableColor
import com.reco1l.verktex.ui.animateColor
import com.reco1l.verktex.ui.fadeTo
import com.reco1l.verktex.ui.scaleTo
import com.reco1l.verktex.ui.themed
import com.reco1l.verktex.ui.transitionSlot

class Checkbox(initialValue: Boolean = false) : Control<Boolean>(initialValue) {

    private val checkIcon = Icon(FAIcon.Check).apply {
        anchor = Anchor.Center
        origin = Anchor.Center
        color = themed { palette.accent }

        if (!initialValue) {
            scale = 0f
            alpha = 0f
        }
    }

    private val checkTransition = checkIcon.transitionSlot()

    private val backgroundTransition = transitionSlot()

    private val backgroundColor = animatableColor(
        get = { (background as? Background.Color)?.color ?: Color4.Transparent },
        set = { background = Background.Color(it) }
    )


    private var isPressed = false
    private var pressPointerId = -1

    override val interactionState
        get() = if (isPressed) InteractionState.Pressed else super.interactionState

    /**
     * The layer highlighting the checkbox while hovered or pressed.
     */
    val highlight = StateHighlight(this)

    override fun drawOverBackground(renderer: Renderer) {
        highlight.draw(renderer, resolvedTheme.palette.accent)
    }

    override val tracksInteractionState
        get() = true

    override fun onInteractionStateChanged(state: InteractionState) {
        highlight.onStateChanged(state)
    }


    init {
        width = themed { controlStyle.minimumHeight }
        height = themed { controlStyle.minimumHeight }
        background = themedBackground()
        radius = themed { CornerRadius(radius.lg) }
        +checkIcon
    }


    private fun themedBackground() =
        Background.Color(themed { if (value) palette.controlActiveBackground else palette.controlInactiveBackground })

    override fun onValueChanged() {
        val target = if (value) 1f else 0f

        checkTransition.run {
            scaleTo(target, 0.4f, Easing.OutBounce)
            fadeTo(target, 0.2f, Easing.OutQuint)
        }

        val palette = resolvedTheme.palette
        val targetBackground = if (value) palette.controlActiveBackground else palette.controlInactiveBackground

        backgroundTransition.run {
            animateColor(backgroundColor, targetBackground, 0.2f, Easing.OutQuint)
                // Themed again once there, so it follows theme changes.
                .after { background = themedBackground() }
        }

        super.onValueChanged()
    }


    override fun onInputEvent(event: InputEvent): Boolean {
        if (event !is PointerEvent) {
            return super.onInputEvent(event)
        }

        // Up and cancel events are dispatched to every component, only the finger that pressed
        // this checkbox can release it.
        if (event.action != PointerEvent.Action.Down && (!isPressed || event.pointerId != pressPointerId)) {
            return false
        }

        return when (event.action) {
            PointerEvent.Action.Down -> {
                if (!isPressed) {
                    isPressed = true
                    pressPointerId = event.pointerId
                }
                true
            }
            PointerEvent.Action.Up -> {
                isPressed = false
                if (containsScenePoint(event.x, event.y)) {
                    value = !value
                }
                true
            }
            PointerEvent.Action.Unknown, PointerEvent.Action.Cancel -> {
                isPressed = false
                false
            }
            PointerEvent.Action.Move -> true
        }
    }
}
