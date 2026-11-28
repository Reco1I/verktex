package com.reco1l.verktex

import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.input.PointerEvent.Action
import com.reco1l.verktex.input.PointerEvent.Button as PointerButton
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.HoverTracker
import com.reco1l.verktex.ui.InteractionState
import com.reco1l.verktex.ui.Button
import com.reco1l.verktex.ui.Component
import com.reco1l.verktex.ui.container.ClickableContainer
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.fadeTo
import com.reco1l.verktex.ui.scaleTo
import com.reco1l.verktex.ui.shape.Box
import com.reco1l.verktex.ui.transitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hover of a mouse: the topmost hoverable component under it and its interactive ancestors, and the
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

    private fun frames(root: Component, count: Int = 1) = repeat(count) {
        root.update(clock)
        root.measure(LayoutConstraints(0f, 800f, 0f, 600f))
        root.layout(Vec4(0f, 0f, 800f, 600f))
    }

    private fun hover(root: Component, x: Float, y: Float) = tracker.resolve(x, y, listOf(root))

    private class HoverCard : ClickableContainer() {
        var enters = 0
        var exits = 0

        init {
            width = 100f.px
            height = 100f.px
            interaction.onHoverEnter = { enters++ }
            interaction.onHoverExit = { exits++ }
        }
    }

    @Test
    fun topmostHoverableAndItsInteractiveAncestorsAreHovered() {
        val below = HoverCard()
        val above = HoverCard()
        val group = ClickableContainer().apply { this += above }
        val root = Container().apply { this += below; this += group }
        frames(root)

        hover(root, 50f, 50f)
        assertTrue(above.interaction.isHovered)
        assertTrue(group.interaction.isHovered)
        assertFalse(below.interaction.isHovered)

        // Moving within it doesn't enter again.
        hover(root, 60f, 60f)
        assertEquals(1, above.enters)
        assertEquals(0, below.enters)

        hover(root, 500f, 500f)
        assertFalse(above.interaction.isHovered)
        assertFalse(group.interaction.isHovered)
        assertEquals(1, above.exits)
    }

    @Test
    fun nonHoverableComponentsDoNotBlockTheHover() {
        val card = HoverCard()
        // A decoration over the card, drawn above it, doesn't take the hover.
        val decoration = Box().apply { width = 100f.px; height = 100f.px }
        val root = Container().apply { this += card; this += decoration }
        frames(root)

        hover(root, 50f, 50f)
        assertTrue(card.interaction.isHovered)
    }

    @Test
    fun clearingThePositionExits() {
        val card = HoverCard()
        val root = Container().apply { this += card }
        frames(root)

        hover(root, 50f, 50f)
        tracker.clear()
        assertFalse(card.interaction.isHovered)
        assertEquals(1, card.exits)
    }

    @Test
    fun detachedComponentsExit() {
        val card = HoverCard()
        val root = Container().apply { this += card }
        frames(root)

        hover(root, 50f, 50f)
        root -= card
        hover(root, 50f, 50f)
        assertFalse(card.interaction.isHovered)
    }

    @Test
    fun interceptingContainerStopsTheHoverBelow() {
        val card = HoverCard()
        val cover = Container().apply { width = fill(); height = fill(); isInterceptingInput = true }
        val root = Container().apply { this += card; this += cover }
        frames(root)

        hover(root, 50f, 50f)
        assertFalse(card.interaction.isHovered)
    }

    @Test
    fun disabledButtonsAreHoveredButDisabled() {
        val button = Button().apply { width = 100f.px; height = 100f.px; padding = Padding.Zero; isEnabled = false }
        val root = Container().apply { this += button }
        frames(root)

        hover(root, 50f, 50f)
        assertTrue(button.interaction.isHovered)
        assertEquals(InteractionState.Disabled, button.interactionState)
    }

    @Test
    fun transitionsFollowTheState() {
        val states = mutableListOf<InteractionState>()
        val card = HoverCard().apply {
            transitions {
                idle { scaleTo(1f) }
                hovered { scaleTo(1.1f) }
            }
            interaction.onStateChange = { states += it }
        }
        val root = Container().apply { this += card }
        frames(root)
        assertEquals(1f, card.scaleX, 0f)

        hover(root, 50f, 50f)
        frames(root)
        assertEquals(1.1f, card.scaleX, 1e-4f)

        // Without a pressed block, pressing runs the hovered one.
        card.onInputEvent(PointerEvent(Action.Down, PointerButton.Left, 0, 50f, 50f))
        frames(root)
        assertEquals(InteractionState.Pressed, card.interactionState)
        assertEquals(1.1f, card.scaleX, 1e-4f)

        card.onInputEvent(PointerEvent(Action.Up, PointerButton.Left, 0, 50f, 50f))
        hover(root, 500f, 500f)
        frames(root)
        assertEquals(1f, card.scaleX, 1e-4f)

        assertEquals(listOf(InteractionState.Idle, InteractionState.Hovered, InteractionState.Pressed, InteractionState.Idle), states)
    }

    @Test
    fun aStateChangeCancelsOnlyThePreviousTransition() {
        val card = HoverCard().apply {
            transitions {
                idle { scaleTo(1f, 1f) }
                hovered { scaleTo(2f, 10f) }
            }
        }
        val root = Container().apply { this += card }
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
        val button = Button().apply {
            width = 100f.px
            height = 100f.px
            // Its themed padding is in density independent pixels, which need the platform.
            padding = Padding.Zero
            transitions { pressed { scaleTo(0.5f) } }
        }
        val root = Container().apply { this += button }
        frames(root)

        button.onInputEvent(PointerEvent(Action.Down, PointerButton.Left, 0, 50f, 50f))
        frames(root, 30)
        assertEquals(0.5f, button.scaleX, 1e-4f)
    }

    @Test
    fun buttonsHaveBuiltInFeedbackWithoutTransitions() {
        val button = Button().apply {
            width = 100f.px
            height = 100f.px
            padding = Padding.Zero
        }
        val root = Container().apply { this += button }
        frames(root)

        button.onInputEvent(PointerEvent(Action.Down, PointerButton.Left, 0, 50f, 50f))
        frames(root, 30)
        // The scale ignores changes smaller than 1e-3, the end of an eased transform can be one.
        assertEquals(0.95f, button.scaleX, 1e-3f)

        button.onInputEvent(PointerEvent(Action.Up, PointerButton.Left, 0, 50f, 50f))
        button.isEnabled = false
        frames(root, 30)
        assertEquals(1f, button.scaleX, 1e-3f)
        assertEquals(0.5f, button.alpha, 1e-3f)
    }

    @Test
    fun controlsHighlightWhileHoveredAndPressed() {
        val button = Button().apply { width = 100f.px; height = 100f.px; padding = Padding.Zero }
        val root = Container().apply { this += button }
        frames(root)
        val style = button.theme.resolved.highlightStyle
        assertEquals(0f, button.highlight.opacity, 0f)

        hover(root, 50f, 50f)
        frames(root, 60)
        assertEquals(style.hoveredAlpha, button.highlight.opacity, 1e-3f)

        button.onInputEvent(PointerEvent(Action.Down, PointerButton.Left, 0, 50f, 50f))
        frames(root, 60)
        assertEquals(style.pressedAlpha, button.highlight.opacity, 1e-3f)

        button.onInputEvent(PointerEvent(Action.Up, PointerButton.Left, 0, 50f, 50f))
        button.isEnabled = false
        frames(root, 60)
        assertEquals(0f, button.highlight.opacity, 1e-3f)
    }

    @Test
    fun highlightCanBeTurnedOff() {
        val button = Button().apply { width = 100f.px; height = 100f.px; padding = Padding.Zero }
        button.highlight.isEnabled = false
        val root = Container().apply { this += button }
        frames(root)

        hover(root, 50f, 50f)
        frames(root, 60)
        assertEquals(0f, button.highlight.opacity, 0f)
    }
}
