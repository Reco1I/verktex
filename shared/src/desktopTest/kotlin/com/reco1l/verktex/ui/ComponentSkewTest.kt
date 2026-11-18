package com.reco1l.verktex.ui

import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.shape.Box
import kotlin.math.PI
import kotlin.math.tan
import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentSkewTest {

    private fun box(builder: Box.() -> Unit) = Box().apply {
        width = 200f.px
        height = 100f.px
        builder()
        measure(LayoutConstraints(0f, 800f, 0f, 600f))
        layout(Vec4(0f, 0f, 800f, 600f))
        calculateTransformations()
    }

    @Test
    fun skewAroundTheTopKeepsTheTopEdge() {
        val box = box {
            skewOrigin = Anchor.TopLeft
            skewX = -45f
        }

        val topRight = box.toSceneSpace(200f, 0f)
        assertEquals(200f, topRight.x, 1e-3f)
        assertEquals(0f, topRight.y, 1e-3f)

        val bottomRight = box.toSceneSpace(200f, 100f)
        assertEquals(100f, bottomRight.x, 1e-3f)
        assertEquals(100f, bottomRight.y, 1e-3f)
    }

    @Test
    fun skewAroundTheCenterKeepsTheCenter() {
        val box = box { skewY = 30f }

        val center = box.toSceneSpace(100f, 50f)
        assertEquals(100f, center.x, 1e-3f)
        assertEquals(50f, center.y, 1e-3f)

        val right = box.toSceneSpace(200f, 50f)
        assertEquals(50f + tan(PI / 6).toFloat() * 100f, right.y, 1e-3f)
    }

    @Test
    fun scenePointsMapBackToTheSkewedLocalSpace() {
        val box = box {
            skewOrigin = Anchor.TopLeft
            skewX = -20f
        }

        val scene = box.toSceneSpace(150f, 80f)
        val local = box.toLocalSpaceFromScene(scene.x, scene.y)

        assertEquals(150f, local.x, 1e-3f)
        assertEquals(80f, local.y, 1e-3f)
    }
}
