package com.reco1l.verktex.ui

import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.time.FramedClock
import com.reco1l.verktex.time.ManualClock
import com.reco1l.verktex.ui.container.Container
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoreographyStatesTest {

    private class TestComponent : Component() {
        override fun onDraw(renderer: Renderer) = Unit
    }

    private enum class State { Hit, Miss, Idle }

    // A target animating two components, as a hit object its pieces.
    private class Target {
        val circle = TestComponent()
        val ring = TestComponent()
        val root = Container().apply {
            this += circle
            this += ring
        }
    }

    private val time = ManualClock()
    private val frames = FramedClock(time, false)
    private val target = Target().also { it.root.clock = time }

    private val states = choreographyStates<State, Target> {
        this[State.Hit] = { at ->
            circle.choreographAt(at) { it.scaleTo(2f, 0.5f) }
            ring.choreographAt(at) { it.fadeOut(1f) }
        }
        this[State.Miss] = { at -> circle.choreographAt(at) { it.fadeOut(0.2f) } }
    }

    private fun seek(to: Float) {
        time.time = to
        frames.processFrame()
        target.root.update(frames)
    }

    @Test
    fun `A state animates every component of its target from the given time`() {
        assertTrue(states.play(target, State.Hit, 10f))

        seek(10.5f)
        assertEquals(2f, target.circle.scaleX, 1e-4f)
        assertEquals(0.5f, target.ring.alpha, 1e-4f)

        // Going back before the state gives the earlier state back.
        seek(9f)
        assertEquals(1f, target.circle.scaleX, 1e-4f)
        assertEquals(1f, target.ring.alpha, 1e-4f)
    }

    @Test
    fun `A state without a block plays the fallback or nothing`() {
        assertFalse(states.play(target, State.Idle, 10f))
        assertEquals(Float.NEGATIVE_INFINITY, target.root.latestChoreographyEndTime(), 0f)

        val withFallback = choreographyStates<State, Target> {
            otherwise { at -> ring.choreographAt(at) { it.fadeOut(0.3f) } }
        }
        assertTrue(withFallback.play(target, State.Idle, 10f))
        assertEquals(10.3f, target.root.latestChoreographyEndTime(), 1e-4f)
    }

    @Test
    fun `The latest end time is the one of the last choreography of the subtree`() {
        states.play(target, State.Hit, 10f)
        assertEquals(11f, target.root.latestChoreographyEndTime(), 1e-4f)

        // A cleared choreography no longer counts.
        target.ring.clearAnimations()
        assertEquals(10.5f, target.root.latestChoreographyEndTime(), 1e-4f)
    }

    @Test
    fun `Resetting the animated state clears the animations and their values`() {
        val component = TestComponent().also { it.clock = time }
        component.choreographAt(0f) { it.fadeTo(0.3f).scaleTo(2f).rotateTo(45f).moveTo(10f, 20f).colorTo(Color4(1f, 0f, 0f)) }
        component.update(frames)

        component.resetAnimatedState()

        assertEquals(Float.NEGATIVE_INFINITY, component.latestChoreographyEndTime(), 0f)
        assertEquals(Color4.White, component.color)
        assertEquals(1f, component.scaleX, 0f)
        assertEquals(1f, component.scaleY, 0f)
        assertEquals(0f, component.rotationZ, 0f)
        assertEquals(0f.px, component.translationX)
        assertEquals(0f.px, component.translationY)
    }
}
