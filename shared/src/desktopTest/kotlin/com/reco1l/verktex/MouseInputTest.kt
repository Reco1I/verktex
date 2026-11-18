package com.reco1l.verktex

import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.input.PointerEvent.Action
import com.reco1l.verktex.input.PointerEvent.Button
import com.reco1l.verktex.input.ScrollEvent
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.container.ClickableContainer
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.container.ScrollableContainer
import com.reco1l.verktex.ui.shape.Box
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mouse on desktop: the wheel scrolls the container under the cursor, and a right click is a
 * long press while [VerktexOptions.rightClickAsLongPress] is on.
 */
class MouseInputTest {

    private class FixedClock : FrameClock() {
        override val time = 0f
        override val isRunning = true
        override val elapsedFrameTime = 1f / 60f
        override val lastFrameTime = 0f
    }

    private val clock = FixedClock()

    // In pixels, the platform (which density independent pixels need) isn't attached in tests.
    private val step = 40f

    @Before
    fun setOptions() {
        Verktex.options = VerktexOptions(wheelScrollStep = step.px)
    }

    @After
    fun resetOptions() {
        Verktex.options = VerktexOptions()
    }

    private fun frames(root: Container, count: Int = 120) = repeat(count) {
        root.update(clock)
        root.measure(LayoutConstraints(0f, 800f, 0f, 600f))
        root.layout(Vec4(0f, 0f, 800f, 600f))
    }

    private fun list(atX: Float, contentHeight: Float = 1000f) = ScrollableContainer().apply {
        x = atX.px
        width = 200f.px
        height = 100f.px
        scrollAxes = Axis.Y
        this += Box().apply {
            width = 200f.px
            height = contentHeight.px
        }
    }

    private fun wheel(root: Container, x: Float, y: Float, deltaY: Float, deltaX: Float = 0f) =
        root.onInputEvent(ScrollEvent(pointerId = 0, x = x, y = y, deltaX = deltaX, deltaY = deltaY))

    @Test
    fun wheelScrollsTheContainerUnderTheCursor() {
        val first = list(0f)
        val second = list(300f)
        val root = Container().apply { this += first; this += second }
        frames(root, 1)

        // Down two notches, over the first list.
        assertTrue(wheel(root, 50f, 50f, deltaY = -2f))
        frames(root)

        assertEquals(2 * step, first.scrollY, 1f)
        assertEquals(0f, second.scrollY, 0f)
        assertFalse(first.isUserScrolling)
    }

    @Test
    fun wheelStopsAtTheEdges() {
        val list = list(0f, contentHeight = 150f)
        val root = Container().apply { this += list }
        frames(root, 1)

        // Up at the top: nothing to scroll, not consumed.
        assertFalse(wheel(root, 50f, 50f, deltaY = 1f))

        wheel(root, 50f, 50f, deltaY = -100f)
        frames(root)
        assertEquals(list.maxScrollY, list.scrollY, 0f)
    }

    @Test
    fun wheelChainsToTheParentAtTheChildEdge() {
        val inner = list(0f, contentHeight = 150f)
        val outer = ScrollableContainer().apply {
            width = 400f.px
            height = 300f.px
            scrollAxes = Axis.Y
            this += Container().apply {
                width = 400f.px
                height = 2000f.px
                this += inner
            }
        }
        val root = Container().apply { this += outer }
        frames(root, 1)

        // The inner list takes the wheel until its end.
        wheel(root, 50f, 50f, deltaY = -100f)
        frames(root)
        assertEquals(inner.maxScrollY, inner.scrollY, 0f)
        assertEquals(0f, outer.scrollY, 0f)

        assertTrue(wheel(root, 50f, 50f, deltaY = -1f))
        frames(root)
        assertEquals(step, outer.scrollY, 1f)
    }

    @Test
    fun verticalWheelScrollsHorizontalContainers() {
        val row = ScrollableContainer().apply {
            width = 200f.px
            height = 100f.px
            scrollAxes = Axis.X
            this += Box().apply { width = 1000f.px; height = 100f.px }
        }
        val root = Container().apply { this += row }
        frames(root, 1)

        wheel(root, 50f, 50f, deltaY = -1f)
        frames(root)
        assertEquals(step, row.scrollX, 1f)
    }

    private class Clicks : ClickableContainer() {
        var ups = 0
        var longPresses = 0
        var cancels = 0

        init {
            width = 100f.px
            height = 100f.px
            onActionUp = { ups++ }
            onActionCancel = { cancels++ }
            onActionLongPress = { longPresses++ }
        }
    }

    @Test
    fun rightClickIsALongPressOnRelease() {
        val clicks = Clicks()
        frames(clicks, 1)

        assertTrue(clicks.onInputEvent(PointerEvent(Action.Down, Button.Right, 0, 10f, 10f)))
        assertEquals(0, clicks.longPresses)
        // Held past the long press delay, it still waits for the release.
        frames(clicks, 60)
        assertEquals(0, clicks.longPresses)

        clicks.onInputEvent(PointerEvent(Action.Up, Button.Right, 0, 10f, 10f))
        assertEquals(1, clicks.longPresses)
        assertEquals(0, clicks.ups)

        // Released outside, it's cancelled.
        clicks.onInputEvent(PointerEvent(Action.Down, Button.Right, 0, 10f, 10f))
        clicks.onInputEvent(PointerEvent(Action.Up, Button.Right, 0, 500f, 500f))
        assertEquals(1, clicks.longPresses)
        assertEquals(1, clicks.cancels)
    }

    @Test
    fun rightClickIsIgnoredWithoutALongPress() {
        val clicks = Clicks().apply { onActionLongPress = null }
        frames(clicks, 1)

        assertFalse(clicks.onInputEvent(PointerEvent(Action.Down, Button.Right, 0, 10f, 10f)))
        assertFalse(clicks.isPressed)
    }

    @Test
    fun rightClickIsAClickWithTheOptionOff() {
        Verktex.options = VerktexOptions(rightClickAsLongPress = false, wheelScrollStep = step.px)
        val clicks = Clicks()
        frames(clicks, 1)

        clicks.onInputEvent(PointerEvent(Action.Down, Button.Right, 0, 10f, 10f))
        clicks.onInputEvent(PointerEvent(Action.Up, Button.Right, 0, 10f, 10f))
        assertEquals(1, clicks.ups)
        assertEquals(0, clicks.longPresses)
    }

    @Test
    fun rightClickDoesNotDragScrollableContainers() {
        val list = list(0f)
        val root = Container().apply { this += list }
        frames(root, 1)

        root.onInputEvent(PointerEvent(Action.Down, Button.Right, 0, 50f, 90f))
        root.onInputEvent(PointerEvent(Action.Move, Button.Unknown, 0, 50f, 10f))
        root.onInputEvent(PointerEvent(Action.Up, Button.Right, 0, 50f, 10f))

        assertFalse(list.isUserScrolling)
        assertEquals(0f, list.scrollY, 0f)
    }
}
