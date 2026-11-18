package com.reco1l.verktex

import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.ui.shape.Box
import com.reco1l.verktex.ui.container.Container
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Children can be inserted at an index, so later attached objects can sit behind earlier ones.
 */
class ContainerInsertTest {

    private class CountingBox : Box() {
        var attached = 0
        override fun onAttached() {
            super.onAttached()
            attached++
        }
    }

    @Test
    fun addAtIndexPlacesTheChildThere() {
        val container = Container()
        val a = Box()
        val b = Box()
        val c = Box()
        val d = Box()
        container += a
        container += b

        container.add(0, c)
        container.add(1, d)

        assertEquals(listOf(c, d, a, b), container.toList())
    }

    @Test
    fun addToBackPutsTheChildFirst() {
        val container = Container()
        val a = Box()
        val b = Box()
        container += a

        container.addToBack(b)

        assertSame(b, container[0])
        assertSame(a, container[1])
    }

    @Test
    fun addAtIndexAttachesAndInvalidates() {
        val container = Container()
        val child = CountingBox()

        container.add(0, child)

        assertSame(container, child.parent)
        assertEquals(1, child.attached)
    }

    @Test
    fun moveToBackInvalidatesAStaticContainer() {
        val container = Container().apply { staticLayout = true }
        val a = Box()
        val b = Box()
        container += a
        container += b
        container.layout(Vec4(0f, 0f, 100f, 100f))
        assertFalse(container.layoutInvalid)

        with(container) { b.moveToBack() }

        assertTrue(container.layoutInvalid)
    }
}
