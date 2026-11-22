package com.reco1l.verktex

import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.container.RecyclableComponent
import com.reco1l.verktex.ui.container.RecyclerContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A recycler with several columns binds and places its items by rows, and appending items keeps the
 * bound components.
 */
class RecyclerContainerGridTest {

    private class FixedClock : FrameClock() {
        override val time = 0f
        override val isRunning = true
        override val elapsedFrameTime = 1f / 60f
        override val lastFrameTime = 0f
    }

    private class Cell : RecyclableComponent<Int>() {
        override val isRecyclable = true

        init {
            width = fill()
            height = 50f.px
        }

        override fun onBind(data: Int) = Unit
    }

    private val frameClock = FixedClock()

    private fun RecyclerContainer<Int, Cell>.frame(width: Float = 300f, height: Float = 200f) {
        update(frameClock)
        measure(LayoutConstraints(0f, width, 0f, height))
        layout(Vec4(0f, 0f, width, height))
    }

    private fun recycler(items: Int, block: RecyclerContainer<Int, Cell>.() -> Unit = {}) = RecyclerContainer<Int, Cell>().apply {
        width = fill()
        height = fill()
        onCreateComponent = { Cell() }
        data = (0 until items).toList()
        block()

        // The first frame learns the size of a row, the next ones bind the visible rows.
        repeat(3) { frame() }
    }

    @Test
    fun itemsArePlacedInRowsOfCells() {
        val recycler = recycler(100) { columns = 3 }

        assertEquals(3, recycler.columnCount)

        val cell = recycler.boundComponents.getValue(4)
        assertEquals(100f, cell.bounds.x, 0.01f)
        assertEquals(50f, cell.bounds.y, 0.01f)
        assertEquals(100f, cell.bounds.width, 0.01f)

        // 34 rows of 50 pixels.
        assertEquals(1700f, recycler.scrollableContentHeight, 0.01f)
    }

    @Test
    fun onlyTheVisibleRowsAreBound() {
        val recycler = recycler(100) { columns = 3 }

        // Rows at 0, 50, 100 and 150 are inside of the 200 pixels shown.
        assertEquals((0 until 12).toSet(), recycler.boundComponents.keys)

        recycler.scrollY = 500f
        recycler.frame()

        assertEquals((30 until 42).toSet(), recycler.boundComponents.keys)
    }

    @Test
    fun minColumnWidthFollowsTheWidth() {
        val recycler = recycler(100) { minColumnWidth = 100f.px }
        assertEquals(3, recycler.columnCount)

        repeat(3) { recycler.frame(width = 450f) }
        assertEquals(4, recycler.columnCount)
        assertEquals((0 until 16).toSet(), recycler.boundComponents.keys)
        assertEquals(1250f, recycler.scrollableContentHeight, 0.01f)
    }

    @Test
    fun addDataKeepsTheBoundComponents() {
        val recycler = recycler(10) { columns = 3 }
        val first = recycler.boundComponents.getValue(0)

        recycler.addData((10 until 20).toList())
        recycler.frame()

        assertSame(first, recycler.boundComponents.getValue(0))
        assertEquals(20, recycler.data.size)
        assertEquals(350f, recycler.scrollableContentHeight, 0.01f)
    }

    @Test
    fun onScrolledToEndIsCalledOncePerContentSize() {
        var calls = 0
        val recycler = recycler(30) {
            columns = 3
            onScrolledToEnd = { calls++ }
        }

        assertEquals(0, calls)

        recycler.scrollY = recycler.maxScrollY
        recycler.frame()
        recycler.frame()
        assertEquals(1, calls)

        recycler.addData((30 until 60).toList())
        repeat(2) { recycler.frame() }
        assertEquals(1, calls)

        recycler.scrollY = recycler.maxScrollY
        recycler.frame()
        assertEquals(2, calls)
    }
}
