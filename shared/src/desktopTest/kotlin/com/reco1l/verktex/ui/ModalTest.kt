package com.reco1l.verktex.ui

import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.container.Container
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModalTest {

    private class FixedClock : FrameClock() {
        override val time = 0f
        override val isRunning = true
        override val elapsedFrameTime = 1f / 60f
        override val lastFrameTime = 0f
    }

    private class TestModal : Modal() {
        var shown = 0
        var hidden = 0

        override fun onShown() {
            shown++
        }

        override fun onHidden() {
            hidden++
            super.onHidden()
        }
    }

    private val clock = FixedClock()

    private fun frames(root: Component, count: Int) = repeat(count) { root.update(clock) }

    @Test
    fun `Showing and hiding call back once their animation ends`() {
        val modal = TestModal()
        val root = Container().apply { this += modal }

        modal.show()
        frames(root, 30)
        assertEquals(1, modal.shown)
        assertEquals(1f, modal.alpha, 1e-3f)

        modal.hide()
        frames(root, 30)
        assertEquals(1, modal.hidden)
        assertFalse(modal.isVisible)
    }

    @Test
    fun `Showing again while hiding doesn't hide it`() {
        val modal = TestModal()
        val root = Container().apply { this += modal }

        modal.show()
        frames(root, 30)
        modal.hide()
        frames(root, 3)
        modal.show()
        frames(root, 30)

        assertEquals(0, modal.hidden)
        assertEquals(2, modal.shown)
        assertTrue(modal.isVisible)
        assertEquals(1f, modal.alpha, 1e-3f)
    }
}
