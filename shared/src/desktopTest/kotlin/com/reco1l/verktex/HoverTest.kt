package com.reco1l.verktex

import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.input.PointerEvent.Action
import com.reco1l.verktex.input.PointerEvent.Button
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.HoverTracker
import com.reco1l.verktex.ui.InteractionState
import com.reco1l.verktex.ui.UIButton
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.container.UICaptureContainer
import com.reco1l.verktex.ui.container.UIClickableContainer
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.fadeTo
import com.reco1l.verktex.ui.scaleTo
import com.reco1l.verktex.ui.shape.UIBox
import com.reco1l.verktex.ui.transitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hover of a mouse: the topmost hoverable component under it and its ancestors, and the
 * transitions components run when their state changes.
 */
class HoverTest {

    private class FixedClock : FrameClock() {
        override val time = 0f
        override val isRunning = true
        override val elapsedFrameTime = 1f / 60f
        override val lastFrameTime = 0f
    }

    private val clock = FixedClock()
    private val tracker = HoverTracker()

    private fun frames(root: UIComponent, count: Int = 1) = repeat(count) {
        root.update(clock)
        root.measure(LayoutConstraints(0f, 800f, 0f, 600f))
        root.layout(Vec4(0f, 0f, 800f, 600f))
    }

    private fun hover(root: UIComponent, x: Float, y: Float) = tracker.resolve(x, y, listOf(root))

    private class Card : UIClickableContainer() {
        var enters = 0
        var exits = 0

        init {
            width = 100f.px
            height = 100f.px
            onHoverEnter = { enters++ }
            onHoverExit = { exits++ }
        }
    }

    @Test
    fun topmostHoverableAndItsAncestorsAreHovered() {
        val below = Card()
        val above = Card()
        val group = UIContainer().apply { this += above }
        val root = UIContainer().apply { this += below; this += group }
        frames(root)

        hover(root, 50f, 50f)
        assertTrue(above.isHovered)
        assertTrue(group.isHovered)
        assertTrue(root.isHovered)
        assertFalse(below.isHovered)

        // Moving within it doesn't enter again.
        hover(root, 60f, 60f)
        assertEquals(1, above.enters)
        assertEquals(0, below.enters)

        hover(root, 500f, 500f)
        assertFalse(above.isHovered)
        assertFalse(group.isHovered)
        assertEquals(1, above.exits)
    }

    @Test
    fun nonHoverableComponentsDoNotBlockTheHover() {
        val card = Card()
        // A decoration over the card, drawn above it, doesn't take the hover.
        val decoration = UIBox().apply { width = 100f.px; height = 100f.px }
        val root = UIContainer().apply { this += card; this += decoration }
        frames(root)

        hover(root, 50f, 50f)
        assertTrue(card.isHovered)
        assertFalse(decoration.isHovered)
    }

    @Test
    fun clearingThePositionExits() {
        val card = Card()
        val root = UIContainer().apply { this += card }
        frames(root)

        hover(root, 50f, 50f)
        tracker.clear()
        assertFalse(card.isHovered)
        assertEquals(1, card.exits)
    }

    @Test
    fun detachedComponentsExit() {
        val card = Card()
        val root = UIContainer().apply { this += card }
        frames(root)

        hover(root, 50f, 50f)
        root -= card
        hover(root, 50f, 50f)
        assertFalse(card.isHovered)
    }

    @Test
    fun blockingCaptureContainerStopsTheHoverBelow() {
        val card = Card()
        val capture = UICaptureContainer().apply { isBlockingInput = true }
        val root = UIContainer().apply { this += card; this += capture }
        frames(root)

        hover(root, 50f, 50f)
        assertFalse(card.isHovered)
    }

    @Test
    fun disabledButtonsAreHoveredButDisabled() {
        val button = UIButton().apply { width = 100f.px; height = 100f.px; padding = Padding.Zero; isEnabled = false }
        val root = UIContainer().apply { this += button }
        frames(root)

        hover(root, 50f, 50f)
        assertTrue(button.isHovered)
        assertEquals(InteractionState.Disabled, button.interactionState)
    }

    @Test
    fun transitionsFollowTheState() {
        val states = mutableListOf<InteractionState>()
        val card = Card().apply {
            transitions {
                idle { scaleTo(1f) }
                hovered { scaleTo(1.1f) }
            }
            onStateChange = { states += it }
        }
        val root = UIContainer().apply { this += card }
        frames(root)
        assertEquals(1f, card.scaleX, 0f)

        hover(root, 50f, 50f)
        frames(root)
        assertEquals(1.1f, card.scaleX, 1e-4f)

        // Without a pressed block, pressing runs the hovered one.
        card.onInputEvent(PointerEvent(Action.Down, Button.Left, 0, 50f, 50f))
        frames(root)
        assertEquals(InteractionState.Pressed, card.interactionState)
        assertEquals(1.1f, card.scaleX, 1e-4f)

        card.onInputEvent(PointerEvent(Action.Up, Button.Left, 0, 50f, 50f))
        hover(root, 500f, 500f)
        frames(root)
        assertEquals(1f, card.scaleX, 1e-4f)

        assertEquals(listOf(InteractionState.Idle, InteractionState.Hovered, InteractionState.Pressed, InteractionState.Idle), states)
    }

    @Test
    fun aStateChangeCancelsOnlyThePreviousTransition() {
        val card = Card().apply {
            transitions {
                idle { scaleTo(1f, 1f) }
                hovered { scaleTo(2f, 10f) }
            }
        }
        val root = UIContainer().apply { this += card }
        frames(root)

        // Unrelated to the transitions, it keeps running.
        card.fadeTo(0f, 1f)

        hover(root, 50f, 50f)
        frames(root, 30)
        val hoveredScale = card.scaleX
        assertTrue(hoveredScale > 1f)

        hover(root, 500f, 500f)
        frames(root, 60)
        // The hovered transition (10 s long) didn't keep growing it, the idle one brought it back.
        assertEquals(1f, card.scaleX, 1e-4f)
        assertEquals(0f, card.alpha, 1e-4f)
    }

    @Test
    fun buttonsLeaveTheirPressFeedbackToTransitions() {
        val button = UIButton().apply {
            width = 100f.px
            height = 100f.px
            // Its themed padding is in density independent pixels, which need the platform.
            padding = Padding.Zero
            transitions { pressed { scaleTo(0.5f) } }
        }
        val root = UIContainer().apply { this += button }
        frames(root)

        button.onInputEvent(PointerEvent(Action.Down, Button.Left, 0, 50f, 50f))
        frames(root, 30)
        assertEquals(0.5f, button.scaleX, 1e-4f)
    }
}
