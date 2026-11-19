package com.reco1l.verktex.ui

import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.time.FramedClock
import com.reco1l.verktex.time.ManualClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChoreographyTransitionsTest {

    private class TestComponent : Component() {
        override fun onDraw(renderer: Renderer) = Unit
    }

    private enum class Result { Hit, Miss, Other }

    private val time = ManualClock()
    private val frames = FramedClock(time, false)
    private val component = TestComponent().also { it.clock = time }

    private val transitions = choreographyTransitions<Result> {
        this[Result.Miss] = { fadeOut(1f) }
        otherwise { scaleTo(2f, 0.5f).then().fadeOut(0.5f) }
    }

    private fun seek(to: Float) {
        time.time = to
        frames.processFrame()
        component.update(frames)
    }

    @Test
    fun `Block of the state plays at the given time`() {
        val end = transitions.play(component, Result.Miss, 10f)
        assertEquals(11f, end, 1e-4f)

        seek(9f)
        assertEquals(1f, component.alpha, 1e-4f)

        seek(10.5f)
        assertEquals(0.5f, component.alpha, 1e-4f)
    }

    @Test
    fun `State without a block plays the fallback`() {
        val end = transitions.play(component, Result.Hit, 10f)
        assertEquals(11f, end, 1e-4f)

        seek(10.5f)
        assertEquals(2f, component.scaleX, 1e-4f)
        assertEquals(1f, component.alpha, 1e-4f)
    }

    @Test
    fun `Nothing plays without a block nor a fallback`() {
        val strict = choreographyTransitions<Result> { this[Result.Miss] = { fadeOut(1f) } }

        assertEquals(10f, strict.play(component, Result.Hit, 10f), 1e-4f)
        assertNull(component.choreography)
    }

    @Test
    fun `Seeking back restores the state before the transition`() {
        transitions.play(component, Result.Miss, 10f)

        seek(12f)
        assertEquals(0f, component.alpha, 1e-4f)

        seek(5f)
        assertEquals(1f, component.alpha, 1e-4f)
    }

    @Test
    fun `One instance plays on several components`() {
        val other = TestComponent().also { it.clock = time }

        transitions.play(component, Result.Miss, 10f)
        transitions.play(other, Result.Other, 20f)

        seek(10.5f)
        other.update(frames)
        assertEquals(0.5f, component.alpha, 1e-4f)
        assertEquals(1f, other.scaleX, 1e-4f)
    }
}
