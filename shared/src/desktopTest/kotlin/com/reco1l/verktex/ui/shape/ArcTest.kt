package com.reco1l.verktex.ui.shape

import org.junit.Assert.assertEquals
import org.junit.Test

class ArcTest {

    @Test
    fun `Test the end follows the progress from the start`() {
        val arc = Arc().apply {
            startAngle = 90f
            progress = 0.25f
        }

        assertEquals(180f, arc.endAngle, 0f)
    }

    @Test
    fun `Test a negative progress goes counter-clockwise`() {
        val arc = Arc().apply { progress = -0.5f }

        assertEquals(-180f, arc.endAngle, 0f)
    }

    @Test
    fun `Test the progress is at most a turn either way`() {
        val arc = Arc()

        arc.progress = 2f
        assertEquals(1f, arc.progress, 0f)

        arc.progress = -3f
        assertEquals(-1f, arc.progress, 0f)
    }
}
