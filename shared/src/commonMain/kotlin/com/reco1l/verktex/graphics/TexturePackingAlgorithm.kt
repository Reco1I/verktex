package com.reco1l.verktex.graphics

import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import kotlin.math.min

/**
 * An interface for packing algorithms used in texture atlas management.
 */
abstract class TexturePackingAlgorithm {

    /**
     * Allocates a rectangle of the specified width and height from the list of free rectangles.
     *
     * @return A Vec4 representing the allocated rectangle, or null if no suitable rectangle was found.
     */
    abstract fun allocate(width: Float, height: Float, freeRegions: MutableList<Vec4>): Vec4?

    /**
     * Checks if a rectangle of the specified width and height can fit within any of the free rectangles.
     *
     * @return True if the rectangle can fit, false otherwise.
     */
    abstract fun fits(width: Float, height: Float, freeRegions: List<Vec4>): Boolean


    /**
     * Packs rectangles of the given sizes (width to height) into a texture of the specified width and height, each
     * one keeping [padding] free pixels around it, see [Texture.regionPadding]. Nothing is changed: the caller
     * makes the regions at the positions returned.
     *
     * @return Where each rectangle goes (its top-left corner, inside its gutter) and the free space left, or null
     * if they don't all fit.
     */
    fun pack(sizes: List<Pair<Float, Float>>, rootWidth: Float, rootHeight: Float, padding: Int = 0): Packed? {

        val freeRegions = mutableListOf(Vec4(0f, 0f, rootWidth, rootHeight))
        val gutter = 2f * padding

        // The largest first, which packs far tighter than the order they were added in.
        val order = sizes.indices.sortedWith(compareByDescending<Int> { sizes[it].first * sizes[it].second }.thenByDescending { maxOf(sizes[it].first, sizes[it].second) })
        val positions = arrayOfNulls<Vec2>(sizes.size)

        for (i in order) {
            val (width, height) = sizes[i]
            val node = allocate(width + gutter, height + gutter, freeRegions) ?: return null
            positions[i] = Vec2(node.x + padding, node.y + padding)
        }

        return Packed(positions.map { it!! }, freeRegions)
    }

    /**
     * The result of [pack].
     */
    class Packed(val positions: List<Vec2>, val freeRegions: List<Vec4>)


    companion object {

        /**
         * An implementation of the MaxRects packing algorithm for texture atlas management.
         */
        val MaxRects = object : TexturePackingAlgorithm() {

            override fun allocate(
                width: Float,
                height: Float,
                freeRegions: MutableList<Vec4>
            ): Vec4? {
                val bestNode = findBestRegion(width, height, freeRegions) ?: return null
                splitFreeRegions(bestNode, freeRegions)
                pruneFreeList(freeRegions)
                return bestNode
            }

            override fun fits(
                width: Float,
                height: Float,
                freeRegions: List<Vec4>
            ): Boolean {
                return findBestRegion(width, height, freeRegions) != null
            }


            private fun findBestRegion(width: Float, height: Float, freeRegions: List<Vec4>): Vec4? {
                var bestNode: Vec4? = null
                var bestAreaFit = Int.MAX_VALUE.toFloat()
                var bestShortSideFit = Int.MAX_VALUE.toFloat()

                for (free in freeRegions) {

                    if (width <= free.width && height <= free.height) {

                        val areaFit = free.width * free.height - width * height

                        val leftOverHoriz = free.width - width
                        val leftOverVert = free.height - height

                        val shortSideFit = min(leftOverHoriz, leftOverVert)

                        if (areaFit < bestAreaFit || (areaFit == bestAreaFit && shortSideFit < bestShortSideFit)) {
                            bestNode = Vec4(free.x, free.y, free.x + width, free.y + height)
                            bestAreaFit = areaFit
                            bestShortSideFit = shortSideFit
                        }

                    }
                }

                return bestNode
            }

            private fun pruneFreeList(freeRegions: MutableList<Vec4>) {

                val it = freeRegions.iterator()

                while (it.hasNext()) {

                    val a = it.next()

                    var contained = false

                    for (b in freeRegions) {

                        if (a === b) continue

                        if (isContainedIn(a, b)) {
                            contained = true
                            break
                        }
                    }

                    if (contained) {
                        it.remove()
                    }
                }
            }

            private fun intersects(a: Vec4, b: Vec4): Boolean {
                return !(b.x >= a.x + a.width ||
                        b.x + b.width <= a.x ||
                        b.y >= a.y + a.height ||
                        b.y + b.height <= a.y)
            }

            private fun isContainedIn(a: Vec4, b: Vec4): Boolean {
                return a.x >= b.x &&
                        a.y >= b.y &&
                        a.x + a.width <= b.x + b.width &&
                        a.y + a.height <= b.y + b.height
            }

            private fun splitFreeRegions(used: Vec4, freeRegions: MutableList<Vec4>) {

                val newRegions = mutableListOf<Vec4>()
                val iterator = freeRegions.iterator()

                while (iterator.hasNext()) {

                    val free = iterator.next()

                    if (!intersects(free, used)) {
                        continue
                    }

                    iterator.remove()

                    // Free regions are stored as (left, top, right, bottom).

                    // Top
                    if (used.top > free.top) {
                        newRegions.add(Vec4(free.left, free.top, free.right, used.top))
                    }

                    // Bottom
                    if (used.bottom < free.bottom) {
                        newRegions.add(Vec4(free.left, used.bottom, free.right, free.bottom))
                    }

                    // Left
                    if (used.left > free.left) {
                        newRegions.add(Vec4(free.left, free.top, used.left, free.bottom))
                    }

                    // Right
                    if (used.right < free.right) {
                        newRegions.add(Vec4(used.right, free.top, free.right, free.bottom))
                    }
                }

                freeRegions.addAll(newRegions)
            }

        }
    }
}