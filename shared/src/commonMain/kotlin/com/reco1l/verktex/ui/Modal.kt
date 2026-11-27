package com.reco1l.verktex.ui

import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.coverParentPadding
import com.reco1l.verktex.data.extendIntoSafeArea
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.ui.container.Container

/**
 * A container shown above everything else, with a [card] holding its content on top of a dimmed
 * backdrop. It is attached as an overlay when shown, unless it was added somewhere else before.
 *
 * The modal itself is the backdrop, covering the whole window: its padding covers the parent's
 * ([com.reco1l.verktex.data.Padding.coverParentPadding]), e.g. the scene's safe area. The card is laid
 * out over it in the [cardArea], which keeps it in the safe area unless [isCardInSafeArea] is disabled,
 * e.g. for a card placed at a position on the screen.
 */
@Suppress("LeakingThis")
open class Modal(

    /**
     * The content of the modal. This is where you should add your UI elements.
     */
    val card: Container = Container().apply {
        anchor = Anchor.Center
        origin = Anchor.Center
        clipping = ClipMode.Bounds
        background = Background.Color(themed { palette.surface })
        radius = themed { CornerRadius(radius.lg) }
        border = themed { cardStyle.elevation.border }
        shadow = themed { cardStyle.elevation.shadow }
    }

) : Container() {

    /**
     * Whether the modal stays open when the user presses outside of the [card].
     */
    var staticBackdrop = false

    /**
     * Whether the modal is detached from its parent once hidden.
     */
    var detachOnHide = false

    /**
     * Whether the modal is shown or being shown.
     */
    var isShown = false
        private set


    /**
     * The container the [card] is laid out in, over the whole modal. It keeps the card out of the safe
     * area ([com.reco1l.verktex.data.Padding.extendIntoSafeArea]) while [isCardInSafeArea].
     */
    protected val cardArea = Container().apply {
        width = fill()
        height = fill()
        padding = extendIntoSafeArea()
    }

    /**
     * Whether the [card] is laid out in the safe area, e.g. centered in it. Without it the card is laid
     * out over the whole window, as the backdrop, so a card placed below another component can be
     * positioned in the modal's space directly.
     */
    var isCardInSafeArea = true
        set(value) {
            field = value
            cardArea.padding = if (value) extendIntoSafeArea() else Padding.Zero
        }

    /**
     * The safe area of the scene the modal is in, as left, top, right and bottom, e.g. to keep a card
     * laid out outside of it ([isCardInSafeArea] disabled) away from the edges.
     */
    protected val safeArea: Vec4
        get() = findScene()?.safeArea ?: Vec4.Zero


    // The finger that pressed the backdrop, releasing it outside of the card hides the modal.
    private var backdropPointerId = -1

    // The fingers that pressed the modal, only their events are handled.
    private val pressedPointers = mutableSetOf<Int>()

    // Whether onShown was called for the current show.
    private var isFullyShown = false

    // The show or hide animation, either cancels the other with its callback.
    private val visibility = transitionSlot()


    init {
        width = fill()
        height = fill()
        padding = coverParentPadding()

        background = Background.Color(BACKDROP_COLOR)
        isVisible = false
        alpha = 0f

        card.scale = HIDDEN_CARD_SCALE
        cardArea += card
        +cardArea
    }


    //region Visibility

    /**
     * Shows the modal.
     */
    fun show() {
        if (isShown) return

        if (parent == null) {
            Verktex.addOverlay(this)
        }

        isShown = true
        isVisible = true
        isFullyShown = false
        pressedPointers.clear()
        startShowAnimation()
        onShow()
    }

    /**
     * Hides the modal.
     */
    fun hide() {
        if (!isShown) return

        isShown = false
        backdropPointerId = -1
        startHideAnimation()
        onHide()
    }

    /**
     * Called when [show] is called.
     */
    protected open fun onShow() = Unit

    /**
     * Called once the show animation finished.
     */
    protected open fun onShown() = Unit

    /**
     * Called when [hide] is called.
     */
    protected open fun onHide() = Unit

    /**
     * Called once the modal finished hiding.
     */
    protected open fun onHidden() {
        if (detachOnHide) {
            removeSelf()
        }
    }

    /**
     * Starts the transforms showing the modal and returns the one ending last, once which it's
     * shown. Override it to change how the modal appears.
     */
    protected open fun animateShow(): Transform {
        card.scaleTo(1f, ANIMATION_DURATION, Easing.OutQuint)
        return fadeTo(1f, ANIMATION_DURATION, Easing.OutQuint)
    }

    /**
     * Starts the transforms hiding the modal and returns the one ending last, once which it's
     * hidden. Override it to change how the modal disappears.
     */
    protected open fun animateHide(): Transform {
        card.scaleTo(HIDDEN_CARD_SCALE, ANIMATION_DURATION, Easing.OutQuint)
        return fadeTo(0f, ANIMATION_DURATION, Easing.OutQuint)
    }

    private fun startShowAnimation() = visibility.run {
        animateShow().after {
            isFullyShown = true
            onShown()
        }
    }

    private fun startHideAnimation() = visibility.run {
        animateHide().after {
            isVisible = false
            onHidden()
        }
    }

    //endregion

    //region Input

    override fun onInputEvent(event: InputEvent): Boolean {
        // While hiding the scene below is usable again.
        if (!isShown) {
            return false
        }

        // Only pointers pressed on the modal are handled, so components outside of it still get
        // the moves and releases of their own presses (the modal doesn't always cover the window).
        if (event is PointerEvent) {
            when (event.action) {
                PointerEvent.Action.Down -> if (containsScenePoint(event.x, event.y)) {
                    pressedPointers += event.pointerId
                } else {
                    return false
                }

                else -> if (event.pointerId !in pressedPointers) {
                    return false
                } else if (event.action != PointerEvent.Action.Move) {
                    pressedPointers -= event.pointerId
                }
            }
        }

        if (super.onInputEvent(event)) {
            return true
        }

        if (event is PointerEvent && !staticBackdrop) {
            when (event.action) {
                PointerEvent.Action.Down -> backdropPointerId = event.pointerId

                PointerEvent.Action.Up -> if (event.pointerId == backdropPointerId) {
                    backdropPointerId = -1
                    if (!card.containsScenePoint(event.x, event.y)) {
                        hide()
                    }
                }

                PointerEvent.Action.Unknown, PointerEvent.Action.Cancel -> backdropPointerId = -1
                PointerEvent.Action.Move -> Unit
            }
        }

        // The modal blocks everything below it.
        return true
    }

    //endregion


    companion object {
        private val BACKDROP_COLOR = Color4.Black.alpha(0.5f)
        private const val HIDDEN_CARD_SCALE = 0.9f
        private const val ANIMATION_DURATION = 0.2f
    }
}
