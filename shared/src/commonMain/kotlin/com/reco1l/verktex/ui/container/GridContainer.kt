package com.reco1l.verktex.ui.container

import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.ui.Component
import kotlin.math.floor
import kotlin.math.max

inline fun Container.gridContainer(builder: GridContainer.() -> Unit): GridContainer {
    return GridContainer().apply(builder).also(::plusAssign)
}

/**
 * The number of columns of a grid [availableWidth] wide: [columns], or as many columns of at least
 * [minColumnWidth] as fit when it's given, at least one.
 */
internal fun gridColumnCount(availableWidth: Float, columns: Int, minColumnWidth: Dimension.Fixed?, columnSpacing: Float): Int {
    if (minColumnWidth == null || !availableWidth.isFinite()) {
        return if (minColumnWidth == null) max(1, columns) else 1
    }
    return max(1, floor((availableWidth + columnSpacing) / (minColumnWidth.toPixels() + columnSpacing)).toInt())
}

/**
 * The width of each of the [columnCount] cells of a grid [availableWidth] wide.
 */
internal fun gridCellWidth(availableWidth: Float, columnCount: Int, columnSpacing: Float) =
    max(0f, (availableWidth - columnSpacing * (columnCount - 1)) / columnCount)

/**
 * A container placing its children in a grid, left to right and then top to bottom.
 *
 * Columns share the width equally: there are [columns] of them, or as many as fit when
 * [minColumnWidth] is given, which makes the count follow the width. A row is as tall as its
 * tallest child. Each child is given its cell, so its anchor and origin place it inside of it, a
 * child filling its width takes the cell's and one filling its height takes the row's.
 */
open class GridContainer : Container() {

    /**
     * The number of columns, used while [minColumnWidth] isn't given.
     */
    var columns = 1
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
        }

    /**
     * The minimum width of a column. When given, the grid has as many columns as fit in its width.
     */
    var minColumnWidth: Dimension.Fixed? = null
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
        }

    /**
     * The spacing between columns.
     */
    var columnSpacing: Dimension.Fixed by themeable(Dimension.Zero) { invalidateMeasure() }

    /**
     * The spacing between rows.
     */
    var rowSpacing: Dimension.Fixed by themeable(Dimension.Zero) { invalidateMeasure() }

    /**
     * The number of columns of the last measure pass.
     */
    var columnCount = 1
        private set

    private var cellWidth = 0f
    private var rowHeights = FloatArray(0)


    override fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints): Vec2 {
        val visibleChildren = filter { it.isVisible }
        val columnSpacingPixels = columnSpacing.toPixels()
        val rowSpacingPixels = rowSpacing.toPixels()

        columnCount = gridColumnCount(contentConstraints.availableWidth, columns, minColumnWidth, columnSpacingPixels)

        val childConstraints = contentConstraints.loosen().copy(maxHeight = Float.POSITIVE_INFINITY)

        cellWidth = if (contentConstraints.availableWidth.isFinite()) {
            gridCellWidth(contentConstraints.availableWidth, columnCount, columnSpacingPixels)
        } else {
            // Wrapping without a limit: the cells are as wide as the widest child.
            visibleChildren.maxOfOrNull { it.measure(childConstraints.withoutFillLimits(it)).x } ?: 0f
        }

        val rowCount = (visibleChildren.size + columnCount - 1) / columnCount
        rowHeights = FloatArray(rowCount)

        // Children filling their height take the row's, so they're measured once the others are.
        val cellConstraints = childConstraints.copy(maxWidth = cellWidth)

        visibleChildren.forEachIndexed { index, child ->
            val size = child.measure(cellConstraints)
            if (child.height !is Dimension.AvailableSizeFraction) {
                val row = index / columnCount
                rowHeights[row] = max(rowHeights[row], size.y)
            }
        }

        visibleChildren.forEachIndexed { index, child ->
            val height = child.height
            if (height is Dimension.AvailableSizeFraction) {
                val row = index / columnCount

                // A row of filling children alone is as tall as their content.
                if (rowHeights[row] == 0f) {
                    rowHeights[row] = child.measuredSize.y
                }

                val rowHeight = rowHeights[row] * height.factor.coerceIn(0f, 1f)
                child.measure(cellConstraints.copy(minHeight = rowHeight, maxHeight = rowHeight))
            }
        }

        val contentWidth = if (rowCount == 0) 0f else cellWidth * columnCount + columnSpacingPixels * (columnCount - 1)
        val contentHeight = rowHeights.sum() + rowSpacingPixels * max(0, rowCount - 1)

        return contentConstraints.constrain(Vec2(contentWidth, contentHeight)).expand(measurePadding)
    }

    // Cells give the sizes to filling children, there's nothing left to stretch them to.
    override fun fillsWrappedWidth(child: Component) = false

    override fun fillsWrappedHeight(child: Component) = false


    override fun onLayout(bounds: Vec4) {
        val left = layoutPadding.x
        val columnSpacingPixels = columnSpacing.toPixels()
        val rowSpacingPixels = rowSpacing.toPixels()

        var y = layoutPadding.y
        var index = 0

        forEach { child ->
            if (!child.isVisible) {
                return@forEach
            }

            val row = index / columnCount
            val column = index % columnCount

            if (column == 0 && row > 0) {
                y += rowHeights[row - 1] + rowSpacingPixels
            }

            val x = left + column * (cellWidth + columnSpacingPixels)
            child.layout(Vec4(x, y, x + cellWidth, y + rowHeights.getOrElse(row) { 0f }))
            index++
        }
    }
}
