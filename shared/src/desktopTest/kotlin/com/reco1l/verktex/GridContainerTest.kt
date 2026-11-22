package com.reco1l.verktex

import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.container.GridContainer
import com.reco1l.verktex.ui.shape.Box
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A grid shares its width between its columns and makes each row as tall as its tallest child.
 */
class GridContainerTest {

    private fun box(width: Dimension = 10f.px, height: Dimension = 10f.px) = Box().apply {
        this.width = width
        this.height = height
    }

    private fun GridContainer.frame(width: Float, height: Float = 1000f) {
        measure(LayoutConstraints(0f, width, 0f, height))
        layout(Vec4(0f, 0f, width, height))
    }

    private fun assertBounds(expected: Vec4, actual: Vec4) {
        assertEquals("left", expected.x, actual.x, 0.01f)
        assertEquals("top", expected.y, actual.y, 0.01f)
        assertEquals("right", expected.z, actual.z, 0.01f)
        assertEquals("bottom", expected.w, actual.w, 0.01f)
    }

    @Test
    fun fixedColumnsShareTheWidth() {
        val children = List(5) { box(width = fill(), height = 20f.px) }
        val grid = GridContainer().apply {
            width = fill()
            columns = 2
            children.forEach { this += it }
        }

        grid.frame(200f)

        assertEquals(2, grid.columnCount)
        assertBounds(Vec4(0f, 0f, 100f, 20f), children[0].bounds)
        assertBounds(Vec4(100f, 0f, 200f, 20f), children[1].bounds)
        assertBounds(Vec4(0f, 20f, 100f, 40f), children[2].bounds)
        assertBounds(Vec4(0f, 40f, 100f, 60f), children[4].bounds)
        assertEquals(60f, grid.measuredSize.y, 0.01f)
    }

    @Test
    fun minColumnWidthFitsAsManyColumnsAsPossible() {
        val grid = GridContainer().apply {
            width = fill()
            minColumnWidth = 100f.px
            columnSpacing = 10f.px
            repeat(4) { this += box(width = fill()) }
        }

        // 3 columns need 320 pixels, 2 need 210.
        grid.frame(319f)
        assertEquals(2, grid.columnCount)

        grid.frame(320f)
        assertEquals(3, grid.columnCount)

        grid.frame(50f)
        assertEquals(1, grid.columnCount)
    }

    @Test
    fun spacingSeparatesCells() {
        val children = List(4) { box(width = fill(), height = 10f.px) }
        val grid = GridContainer().apply {
            width = fill()
            columns = 2
            columnSpacing = 20f.px
            rowSpacing = 5f.px
            children.forEach { this += it }
        }

        grid.frame(220f)

        assertBounds(Vec4(0f, 0f, 100f, 10f), children[0].bounds)
        assertBounds(Vec4(120f, 0f, 220f, 10f), children[1].bounds)
        assertBounds(Vec4(0f, 15f, 100f, 25f), children[2].bounds)
        assertEquals(25f, grid.measuredSize.y, 0.01f)
    }

    @Test
    fun hiddenChildrenTakeNoCell() {
        val a = box()
        val hidden = box().apply { isVisible = false }
        val b = box()
        val grid = GridContainer().apply {
            width = fill()
            columns = 2
            this += a
            this += hidden
            this += b
        }

        grid.frame(100f)

        assertBounds(Vec4(50f, 0f, 60f, 10f), b.bounds)
        assertEquals(10f, grid.measuredSize.y, 0.01f)
    }

    @Test
    fun rowsAreAsTallAsTheirTallestChildAndFillingChildrenTakeIt() {
        val short = box(height = 10f.px)
        val tall = box(height = 40f.px)
        val filling = box(height = fill())
        val nextRow = box(height = 10f.px)
        val grid = GridContainer().apply {
            width = fill()
            columns = 3
            this += short
            this += tall
            this += filling
            this += nextRow
        }

        grid.frame(300f)

        assertEquals(40f, filling.measuredSize.y, 0.01f)
        assertEquals(40f, nextRow.bounds.y, 0.01f)
        assertEquals(50f, grid.measuredSize.y, 0.01f)
    }

    @Test
    fun wrappingWithoutALimitUsesTheWidestChild() {
        val grid = GridContainer().apply {
            columns = 2
            this += box(width = 30f.px)
            this += box(width = 50f.px)
            this += box(width = 10f.px)
        }

        grid.measure(LayoutConstraints(0f, Float.POSITIVE_INFINITY, 0f, Float.POSITIVE_INFINITY))

        assertEquals(100f, grid.measuredSize.x, 0.01f)
        assertEquals(20f, grid.measuredSize.y, 0.01f)
    }
}
