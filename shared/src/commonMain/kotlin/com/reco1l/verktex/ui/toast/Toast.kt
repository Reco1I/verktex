package com.reco1l.verktex.ui.toast

import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.ui.Button
import com.reco1l.verktex.ui.FAIcon
import com.reco1l.verktex.ui.IconButton
import com.reco1l.verktex.ui.container.setHierarchyScrollPrevention
import com.reco1l.verktex.ui.fadeTo
import com.reco1l.verktex.ui.moveToX
import kotlin.math.abs
import com.reco1l.verktex.ui.container.LinearContainer
import com.reco1l.verktex.ui.text.Text
import com.reco1l.verktex.ui.themed

/**
 * A message shown above the scene, see [Toasts]: a [text] with an optional [title] above it.
 *
 * A [dismissible][isDismissible] one has a close button and can be swiped sideways, both of which
 * call [onDismiss]. Subclasses can add to it: below the text in [body], and before or after it
 * with [add].
 */
open class Toast : LinearContainer() {

    private val titleText = Text().apply {
        width = fill()
        fontSettings = themed { typography.caption }
        color = themed { palette.accent }
        isVisible = false
    }

    private val messageText = Text().apply {
        width = fill()
        fontSettings = themed { typography.smallBody }
    }

    /**
     * The column of the title and the text.
     */
    protected val body = LinearContainer().apply {
        orientation = Orientation.Vertical
        width = fill()
        spacing = themed { gap.xs }

        +titleText
        +messageText
    }

    private val closeButton = IconButton().apply {
        anchor = Anchor.CenterRight
        origin = Anchor.CenterRight
        icon = FAIcon.Xmark
        variant = Button.Variant.Ghost
        density = Button.Density.Small
        isVisible = false
        onActionUp = { onDismiss() }
    }

    // The pointer swiping this toast, -1 if none, where it went down and whether it's a swipe yet.
    private var swipePointerId = -1
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var isSwiping = false
    private var swipeDistance = 0f

    /**
     * Whether it has a close button and can be swiped away, see [onDismiss].
     */
    var isDismissible = false
        set(value) {
            field = value
            closeButton.isVisible = value
        }

    /**
     * Called when the close button is pressed or the toast is swiped away. A swiped toast is
     * already out of sight then: the owner removes it.
     */
    var onDismiss: () -> Unit = {}

    /**
     * The title above the text, none if empty.
     */
    var title: CharSequence = ""
        set(value) {
            field = value
            titleText.text = value
            titleText.isVisible = value.isNotEmpty()
        }

    /**
     * The message of the toast.
     */
    var text by messageText::text


    init {
        orientation = Orientation.Horizontal
        anchor = Anchor.TopCenter
        origin = Anchor.TopCenter
        width = 360f.dip
        maxWidth = fill(0.9f)
        spacing = themed { gap.md }
        padding = themed { Padding(gap.md) }
        background = Background.Color(themed { palette.surface })
        radius = themed { CornerRadius(radius.lg) }

        +body
        +closeButton
    }


    override fun onInputEvent(event: InputEvent): Boolean {
        if (!isDismissible || event !is PointerEvent) {
            return super.onInputEvent(event)
        }

        if (event.action == PointerEvent.Action.Down) {
            if (swipePointerId == -1) {
                swipePointerId = event.pointerId
                swipeStartX = event.x
                swipeStartY = event.y
                isSwiping = false
            }
            // Buttons get the press, an empty part of the toast takes it to follow the swipe.
            super.onInputEvent(event)
            return true
        }

        if (event.pointerId != swipePointerId) {
            return super.onInputEvent(event)
        }

        val deltaX = event.x - swipeStartX
        val deltaY = event.y - swipeStartY

        when (event.action) {
            PointerEvent.Action.Move -> {
                if (!isSwiping) {
                    // Sideways past the threshold it's a swipe, otherwise a press or a scroll of the parents.
                    if (abs(deltaX) < SWIPE_THRESHOLD.toPixels() || abs(deltaX) < abs(deltaY)) {
                        return super.onInputEvent(event)
                    }
                    isSwiping = true
                    setHierarchyScrollPrevention(true)
                    // The buttons' press is canceled.
                    super.onInputEvent(event.copy(action = PointerEvent.Action.Unknown))
                }

                val distance = deltaX - SWIPE_THRESHOLD.toPixels() * if (deltaX > 0) 1f else -1f
                swipeDistance = distance
                translationX = distance.px
                alpha = 1f - (abs(distance) / bounds.width.coerceAtLeast(1f)).coerceIn(0f, 0.8f)
                return true
            }

            PointerEvent.Action.Up, PointerEvent.Action.Cancel, PointerEvent.Action.Unknown -> {
                swipePointerId = -1

                if (!isSwiping) {
                    return super.onInputEvent(event)
                }
                isSwiping = false
                setHierarchyScrollPrevention(false)

                val distance = swipeDistance
                swipeDistance = 0f
                if (event.action == PointerEvent.Action.Up && abs(distance) >= bounds.width * DISMISS_FRACTION) {
                    moveToX(bounds.width * if (distance > 0) 1f else -1f, 0.15f, Easing.OutQuad)
                    fadeTo(0f, 0.15f, Easing.OutQuad).after { onDismiss() }
                } else {
                    moveToX(0f, 0.25f, Easing.OutQuint)
                    fadeTo(1f, 0.25f, Easing.OutQuint)
                }
                return true
            }

            else -> return super.onInputEvent(event)
        }
    }
}


private val SWIPE_THRESHOLD = 12f.dip

// How far a toast is swiped, out of its width, to be dismissed.
private const val DISMISS_FRACTION = 0.35f
