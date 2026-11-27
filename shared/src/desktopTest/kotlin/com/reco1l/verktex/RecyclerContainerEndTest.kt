package com.reco1l.verktex

import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.container.RecyclableComponent
import com.reco1l.verktex.ui.container.RecyclerContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A recycler anchored to its end, as a chat's: short content sits at the bottom, the scroll follows
 * appended items while at the end, and removing the first items keeps what's shown in place.
 */
class RecyclerContainerEndTest {

    private class FixedClock : FrameClock() {
        override val time = 0f
        override val isRunning = true
        override val elapsedFrameTime = 1f / 60f
        override val lastFrameTime = 0f
    }

    private class Row : RecyclableComponent<Int>() {
        override val isRecyclable = true

        init {
            width = fill()
            height = 50f.px
        }

        override fun onBind(data: Int) = Unit
    }

    private val frameClock = FixedClock()

    private fun RecyclerContainer<Int, Row>.frame() {
        update(frameClock)
        measure(LayoutConstraints(0f, 300f, 0f, 200f))
        layout(Vec4(0f, 0f, 300f, 200f))
    }

    // Where an item's component is in the recycler, its wrapper being moved by the scroll.
    private fun RecyclerContainer<Int, Row>.shownY(item: Int) =
        componentWrapper.bounds.y + boundComponents.getValue(item).bounds.y

    private fun recycler(items: Int, anchored: Boolean = true) = RecyclerContainer<Int, Row>().apply {
        width = fill()
        height = fill()
        isAnchoredToEnd = anchored
        onCreateComponent = { Row() }
        data = (0 until items).toList()

        // The first frame learns the size of a row, the next ones bind the visible rows.
        repeat(3) { frame() }
    }

    @Test
    fun shortContentIsPlacedAtTheEnd() {
        val recycler = recycler(2)

        // Two rows of 50 pixels at the bottom of 200.
        assertEquals(100f, recycler.shownY(0), 0.01f)
        assertEquals(150f, recycler.shownY(1), 0.01f)
    }

    @Test
    fun shortContentStaysAtTheStartWhenNotAnchored() {
        val recycler = recycler(2, anchored = false)

        assertEquals(0f, recycler.shownY(0), 0.01f)
    }

    @Test
    fun theScrollFollowsAppendedItemsAtTheEnd() {
        val recycler = recycler(10)
        assertEquals(recycler.maxScrollY, recycler.scrollY, 0.01f)

        recycler.addData(listOf(10, 11))
        repeat(3) { recycler.frame() }

        // 12 rows of 50 pixels in 200.
        assertEquals(400f, recycler.maxScrollY, 0.01f)
        assertEquals(400f, recycler.scrollY, 0.01f)
        assertEquals(150f, recycler.shownY(11), 0.01f)
    }

    @Test
    fun theScrollStaysWhereItIsAwayFromTheEnd() {
        val recycler = recycler(10)

        recycler.scrollY = 100f
        recycler.frame()

        recycler.addData(listOf(10, 11))
        repeat(3) { recycler.frame() }

        assertEquals(100f, recycler.scrollY, 0.01f)
    }

    @Test
    fun removeFirstDataKeepsWhatIsShownInPlace() {
        val recycler = recycler(10, anchored = false)

        recycler.scrollY = 200f
        recycler.frame()
        val shown = recycler.boundComponents.getValue(5)
        val y = recycler.shownY(5)

        recycler.removeFirstData(2)
        recycler.frame()

        assertEquals(8, recycler.data.size)
        assertEquals(100f, recycler.scrollY, 0.01f)
        assertSame(shown, recycler.boundComponents.getValue(5))
        assertEquals(y, recycler.shownY(5), 0.01f)
        assertFalse(0 in recycler.boundComponents)
        assertEquals(400f, recycler.scrollableContentHeight, 0.01f)
    }
}
