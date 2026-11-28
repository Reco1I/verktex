package com.reco1l.verktex.ui

import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.coverParentPadding
import com.reco1l.verktex.data.extendIntoSafeArea
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.container.Container
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The padding behaviours: covering the parent's padding ([Padding.coverParentPadding]) and growing into
 * the safe area ([Padding.extendIntoSafeArea]), which a scene has by default.
 */
class PaddingBehaviourTest {

    // An embedded scene 800x600 with a safe area of 40 on the left, 20 at the top, 30 on the right and
    // 10 at the bottom: its content area is (40, 20, 770, 590).
    private val scene = Scene().apply { embeddedSafeArea = Vec4(40f, 20f, 30f, 10f) }

    private val root = Container().apply {
        width = fill()
        height = fill()
        +scene
    }

    private fun layout() {
        root.measure(LayoutConstraints(0f, 800f, 0f, 600f))
        root.layout(Vec4(0f, 0f, 800f, 600f))
    }

    private fun Container.filler() = Container().apply {
        width = fill()
        height = fill()
    }.also { this += it }


    @Test
    fun `a scene keeps the safe area by default`() {
        val child = scene.filler()
        layout()

        assertEquals(Vec4(40f, 20f, 30f, 10f), scene.layoutPadding)
        assertEquals(Vec4(0f, 0f, 800f, 600f), scene.bounds)
        assertEquals(Vec4(40f, 20f, 770f, 590f), child.bounds)
    }

    @Test
    fun `a scene adds its own padding to the safe area or covers it without the behaviour`() {
        val child = scene.filler()

        scene.padding = Padding(5f.px).extendIntoSafeArea()
        layout()
        assertEquals(Vec4(45f, 25f, 765f, 585f), child.bounds)

        scene.padding = Padding.Zero
        layout()
        assertEquals(Vec4(0f, 0f, 800f, 600f), child.bounds)
    }

    @Test
    fun `extending into the safe area grows on the sides against it and keeps the content in place`() {
        val bar = Container().apply {
            width = fill()
            height = 50f.px
            padding = Padding(5f.px).extendIntoSafeArea()
        }
        scene += bar
        val content = bar.filler()
        layout()

        // Against the left, top and right edges, not the bottom one.
        assertEquals(Vec4(40f, 20f, 30f, 0f), bar.paddingLayout.extent)
        assertEquals(Vec4(0f, 0f, 800f, 70f), bar.bounds)
        assertEquals(Vec4(45f, 25f, 35f, 5f), bar.layoutPadding)
        assertEquals(Vec4(45f, 25f, 765f, 65f), content.bounds)
    }

    @Test
    fun `covering the parent's padding ignores it, content included`() {
        val cover = Container().apply {
            width = fill()
            height = fill()
            padding = coverParentPadding()
        }
        scene += cover
        val content = cover.filler()
        layout()

        assertEquals(Vec4(0f, 0f, 800f, 600f), cover.bounds)
        assertEquals(Vec4.Zero, cover.layoutPadding)
        assertEquals(Vec4(0f, 0f, 800f, 600f), content.bounds)
    }

    @Test
    fun `covering the parent's padding and extending into the safe area keeps the content out of it`() {
        val modal = Container().apply {
            width = fill()
            height = fill()
            padding = coverParentPadding().extendIntoSafeArea()
        }
        scene += modal
        val content = modal.filler()
        layout()

        // Already at the edges, it doesn't grow, its content is inset by the safe area.
        assertEquals(Vec4(0f, 0f, 800f, 600f), modal.bounds)
        assertEquals(Vec4.Zero, modal.paddingLayout.extent)
        assertEquals(Vec4(40f, 20f, 30f, 10f), modal.layoutPadding)
        assertEquals(Vec4(40f, 20f, 770f, 590f), content.bounds)
        assertEquals(Vec2(730f, 570f), content.measuredSize)
    }

    @Test
    fun `covering a padding that isn't the safe area drops it`() {
        val wrapper = Container().apply {
            width = fill()
            height = fill()
            padding = Padding(10f.px)
        }
        scene += wrapper

        val both = Container().apply {
            width = fill()
            height = fill()
            padding = coverParentPadding().extendIntoSafeArea()
        }
        wrapper += both
        val content = both.filler()
        layout()

        // In the wrapper's space, the wrapper being the scene's content area, 730x570 at (40, 20): over
        // the wrapper's padding, then into the safe area up to the edges of the scene.
        assertEquals(Vec4(-40f, -20f, 760f, 580f), both.bounds)
        assertEquals(Vec4(40f, 20f, 30f, 10f), both.layoutPadding)
        assertEquals(Vec4(40f, 20f, 770f, 590f), content.bounds)
    }

    @Test
    fun `covering a parent grown into the safe area covers what it grew`() {
        val extended = scene.filler().apply { padding = extendIntoSafeArea() }

        val cover = Container().apply {
            width = fill()
            height = fill()
            padding = coverParentPadding()
        }
        extended += cover
        layout()

        assertEquals(Vec4(0f, 0f, 800f, 600f), extended.bounds)
        assertEquals(Vec4(40f, 20f, 30f, 10f), extended.layoutPadding)
        assertEquals(Vec4(0f, 0f, 800f, 600f), cover.bounds)
        assertEquals(Vec4.Zero, cover.layoutPadding)
    }

    @Test
    fun `a component partly in the safe area grows to the edge and keeps its content out of it`() {
        val bar = Container().apply {
            width = fill()
            height = 50f.px
            y = (-15f).px
            padding = extendIntoSafeArea()
        }
        scene += bar
        val content = bar.filler()
        layout()

        // 5 from the top edge: it grows by those 5, its content starts below the safe area's 20.
        assertEquals(5f, bar.paddingLayout.extent.y)
        assertEquals(20f, bar.layoutPadding.y)
        assertEquals(0f, bar.bounds.y)
        assertEquals(20f, content.bounds.y)
    }

    @Test
    fun `the behaviours combine in any order and once`() {
        val sides = Padding(5f.px)

        assertEquals(sides.coverParentPadding().extendIntoSafeArea(), sides.extendIntoSafeArea().coverParentPadding())
        assertEquals(sides.extendIntoSafeArea(), sides.extendIntoSafeArea().extendIntoSafeArea())
        assertEquals(sides.coverParentPadding(), sides.coverParentPadding().coverParentPadding())
        assertNotEquals(sides, sides.extendIntoSafeArea())
        assertNotEquals(sides.coverParentPadding(), sides.extendIntoSafeArea())
    }
}
