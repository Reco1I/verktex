package com.reco1l.verktex.ui

import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.time.FramedClock
import com.reco1l.verktex.time.ManualClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransformsTest {

    private class TestComponent : Component() {
        override fun onDraw(renderer: Renderer) = Unit
    }

    private lateinit var component: Component
    private lateinit var time: ManualClock
    private lateinit var frames: FramedClock

    @Before
    fun setUp() {
        component = TestComponent()
        time = ManualClock()
        frames = FramedClock(time, false)
        component.timing.source = time
    }

    private fun advanceTo(to: Float) {
        time.time = to
        frames.processFrame()
        component.update(frames)
    }

    @Test
    fun `A transform starting later replaces the one running on the same property`() {
        component.alpha = 0f
        component.fadeTo(1f, 10f)

        advanceTo(1f)
        component.fadeTo(0f, 1f)

        // The first one would keep applying after the second ended.
        advanceTo(3f)
        assertEquals(0f, component.alpha, 1e-4f)
        advanceTo(5f)
        assertEquals(0f, component.alpha, 1e-4f)
    }

    @Test
    fun `A replacing transform starts from the current value`() {
        component.alpha = 0f
        component.fadeTo(1f, 2f)

        advanceTo(1f)
        assertEquals(0.5f, component.alpha, 1e-4f)

        component.fadeTo(0f, 1f)
        advanceTo(1.5f)
        assertEquals(0.25f, component.alpha, 1e-4f)
    }

    @Test
    fun `A replaced transform doesn't call its callback`() {
        var called = false
        component.fadeTo(0f, 1f).after { called = true }

        advanceTo(0.5f)
        component.fadeTo(1f, 1f)
        advanceTo(5f)

        assertFalse(called)
        assertEquals(1f, component.alpha, 1e-4f)
    }

    @Test
    fun `Transforms on other properties aren't replaced`() {
        component.alpha = 0f
        component.scale = 0f
        component.fadeTo(1f, 2f)

        advanceTo(1f)
        component.scaleTo(1f, 1f)

        advanceTo(2f)
        assertEquals(1f, component.alpha, 1e-4f)
        assertEquals(1f, component.scaleX, 1e-4f)
    }

    @Test
    fun `A transform waiting to start doesn't replace the running one yet`() {
        component.alpha = 0f
        component.transformSequence { fadeIn(1f).then().fadeOut(1f) }

        advanceTo(0.5f)
        assertEquals(0.5f, component.alpha, 1e-4f)
        advanceTo(1.5f)
        assertEquals(0.5f, component.alpha, 1e-4f)
    }

    @Test
    fun `A transform on one axis replaces only that axis`() {
        component.scale = 0f
        component.scaleTo(1f, 2f)

        advanceTo(1f)
        component.scaleXTo(0f, 1f)

        advanceTo(2f)
        assertEquals(0f, component.scaleX, 1e-4f)
        assertEquals(1f, component.scaleY, 1e-4f)
    }

    @Test
    fun `Custom properties are animated and replaced by instance`() {
        var opacity = 0f
        val property = animatableFloat({ opacity }, { opacity = it })
        var other = 0f
        val otherProperty = animatableFloat({ other }, { other = it })

        component.animate(property, 1f, 2f)
        component.animate(otherProperty, 1f, 2f)

        advanceTo(1f)
        assertEquals(0.5f, opacity, 1e-4f)

        component.animate(property, 0f, 1f)
        advanceTo(2f)
        assertEquals(0f, opacity, 1e-4f)
        assertEquals(1f, other, 1e-4f)
    }

    @Test
    fun `Custom colors animate every channel`() {
        var color = Color4(0f, 0f, 0f, 0f)
        val property = animatableColor({ color }, { color = it })

        component.animateColor(property, Color4(1f, 1f, 1f, 1f), 2f)
        advanceTo(1f)

        // The channels are mixed in linear light, the alpha linearly.
        assertTrue(color.red > 0f && color.red < 1f)
        assertEquals(color.red, color.blue, 1e-4f)
        assertEquals(0.5f, color.alpha, 1e-3f)

        advanceTo(2f)
        assertEquals(1f, color.red, 1e-4f)
    }

    @Test
    fun `A callback adding a transform has it applied in the same frame`() {
        component.alpha = 1f
        component.delay(1f).after { it.fadeTo(0f) }

        advanceTo(1f)
        assertEquals(0f, component.alpha, 1e-4f)
    }

    @Test
    fun `Running a slot again cancels only what it started`() {
        val slot = component.transitionSlot()
        var callbacks = 0

        component.alpha = 0f
        component.rotateTo(90f, 2f)
        slot.run { fadeTo(1f, 2f).after { callbacks++ } }

        advanceTo(1f)
        slot.run { scaleTo(2f, 1f) }

        advanceTo(3f)
        // The fade stopped where it was, its callback never called, the rotation went on.
        assertEquals(0.5f, component.alpha, 1e-4f)
        assertEquals(0, callbacks)
        assertEquals(90f, component.rotationZ, 1e-4f)
        assertEquals(2f, component.scaleX, 1e-4f)
    }

    @Test
    fun `Cancelling a slot cancels its waiting transforms too`() {
        val slot = component.transitionSlot()

        component.alpha = 1f
        slot.run { transformSequence(delay = 1f) { fadeOut() } }
        slot.cancel()

        advanceTo(2f)
        assertEquals(1f, component.alpha, 1e-4f)
        assertTrue(!component.isAnimating)
    }
}
