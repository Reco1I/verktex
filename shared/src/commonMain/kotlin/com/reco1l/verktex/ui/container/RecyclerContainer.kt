package com.reco1l.verktex.ui.container

import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.time.FrameClock
import kotlin.math.max
import kotlin.math.min

/**
 * A scrollable container that only keeps components for the items of its [data] that are visible,
 * reusing the ones that stop being visible.
 *
 * Items are placed one after another along the [orientation] or, vertically, in rows of [columns]
 * (or as many columns of [minColumnWidth] as fit) sharing the width, as a [GridContainer] does. A row
 * is as tall as its tallest bound item.
 *
 * The size of a row is only known once its items have been bound, until then it is estimated from the
 * first measured one.
 */
open class RecyclerContainer<T : Any, C : RecyclableComponent<T>>(private val initialPoolSize: Int = 8) : ScrollableContainer() {

    /**
     * The components currently bound to an item. Those are the ones inside of the container's visible area
     * and the ones that are not recyclable, determined by [RecyclableComponent.isRecyclable].
     */
    val boundComponents: Map<T, C>
        field = HashMap()

    /**
     * The wrapper holding the bound components, it is the only child of this container.
     */
    val componentWrapper = RecyclerComponentWrapper()

    /**
     * The component supplier.
     */
    var onCreateComponent: (() -> C)? = null
        set(value) {
            field = value
            resetComponents()
        }

    /**
     * The items to bind to the components. Setting it unbinds every component, [addData] appends items
     * keeping them.
     */
    var data: List<T>
        get() = items
        set(value) {
            items = value.toList()
            unbindAll()

            itemIndices.clear()
            items.forEachIndexed { index, item -> itemIndices[item] = index }

            rowSizes = FloatArray(rowCountOf(items.size)) { estimatedRowSize }
            componentWrapper.invalidateMeasure()
        }

    /**
     * The direction in which the items are placed. The container scrolls along it.
     */
    var orientation = LinearContainer.Orientation.Vertical
        set(value) {
            if (field == value) return
            field = value
            scrollAxes = scrollAxisOf(value)
            componentWrapper.invalidateMeasure()
        }

    /**
     * The spacing between items, or between rows when there are several columns.
     */
    var spacing: Dimension.Fixed by themeable(Dimension.Zero) { componentWrapper.invalidateMeasure() }

    /**
     * The number of columns of a vertical container, used while [minColumnWidth] isn't given.
     */
    var columns = 1
        set(value) {
            if (field == value) return
            field = value
            componentWrapper.invalidateMeasure()
        }

    /**
     * The minimum width of a column of a vertical container. When given, there are as many columns as
     * fit in its width.
     */
    var minColumnWidth: Dimension.Fixed? = null
        set(value) {
            if (field == value) return
            field = value
            componentWrapper.invalidateMeasure()
        }

    /**
     * The spacing between columns.
     */
    var columnSpacing: Dimension.Fixed by themeable(Dimension.Zero) { componentWrapper.invalidateMeasure() }

    /**
     * The number of columns of the last measure pass, always one for a horizontal container.
     */
    var columnCount = 1
        private set


    private var items: List<T> = emptyList()

    private val componentPool = ArrayDeque<C>()
    private val itemIndices = HashMap<T, Int>()

    private var rowSizes = FloatArray(0)
    private var estimatedRowSize = 0f
    private var cellWidth = 0f


    init {
        // Scrolling on both axes would measure the items with an unbounded cross axis.
        scrollAxes = scrollAxisOf(orientation)
        +componentWrapper
    }


    private fun scrollAxisOf(orientation: LinearContainer.Orientation) =
        if (orientation == LinearContainer.Orientation.Vertical) Axis.Y else Axis.X

    private fun rowCountOf(itemCount: Int) = (itemCount + columnCount - 1) / columnCount


    /**
     * Appends [newItems] to [data], keeping the bound components where they are.
     */
    fun addData(newItems: List<T>) {
        if (newItems.isEmpty()) return

        val start = items.size
        items = items + newItems
        newItems.forEachIndexed { index, item -> itemIndices[item] = start + index }

        val previousRows = rowSizes
        rowSizes = FloatArray(rowCountOf(items.size)) { previousRows.getOrElse(it) { estimatedRowSize } }
        componentWrapper.invalidateMeasure()
    }


    /**
     * Removes the first [count] items of [data], keeping the bound components of the others. The scroll
     * position moves back by the size they took, so what's shown stays where it is.
     */
    fun removeFirstData(count: Int) {
        val removed = min(count, items.size)
        if (removed <= 0) return

        for (index in 0 until removed) {
            val item = items[index]
            boundComponents[item]?.let { unbindComponent(item, it) }
        }

        // The rows that went entirely, which every following row moves back by.
        val removedRows = removed / columnCount
        val isVertical = orientation == LinearContainer.Orientation.Vertical
        var removedSize = 0f
        for (row in 0 until min(removedRows, rowSizes.size)) {
            removedSize += rowSizes[row] + spacing.toPixels()
        }

        items = items.drop(removed)
        itemIndices.clear()
        items.forEachIndexed { index, item -> itemIndices[item] = index }

        // Rows keep their size if whole rows were removed, otherwise items move between them.
        rowSizes = if (removed % columnCount == 0) {
            rowSizes.copyOfRange(min(removedRows, rowSizes.size), rowSizes.size)
        } else {
            FloatArray(rowCountOf(items.size)) { estimatedRowSize }
        }

        if (isVertical) {
            scrollY = max(0f, scrollY - removedSize)
        } else {
            scrollX = max(0f, scrollX - removedSize)
        }
        componentWrapper.invalidateMeasure()
    }


    private fun resetComponents() {
        unbindAll()
        componentPool.clear()

        repeat(initialPoolSize) {
            createComponent()?.let(componentPool::addLast)
        }
    }

    private fun createComponent() = onCreateComponent?.invoke()

    private fun bindComponent(item: T): C? {
        boundComponents[item]?.let { return it }

        val component = componentPool.removeFirstOrNull() ?: createComponent() ?: return null

        component.boundData = item
        component.onBind(item)

        boundComponents[item] = component
        componentWrapper += component
        return component
    }

    private fun unbindComponent(item: T, component: C) {
        boundComponents.remove(item)
        component.removeSelf()

        component.boundData = null
        component.onRecycle()

        componentPool.addLast(component)
    }

    private fun unbindAll() {
        boundComponents.toList().forEach { (item, component) -> unbindComponent(item, component) }
    }

    private fun startPadding() = if (orientation == LinearContainer.Orientation.Vertical) {
        componentWrapper.paddingTop.toPixels()
    } else {
        componentWrapper.paddingLeft.toPixels()
    }

    /**
     * Binds the items entering the visible area and unbinds the ones that left it.
     */
    private fun updateBoundComponents() {
        // Until an item is measured every size is zero and all of them would seem visible, so only the
        // first one is bound to learn the size.
        if (estimatedRowSize == 0f) {
            items.firstOrNull()?.let(::bindComponent)
            return
        }

        val isVertical = orientation == LinearContainer.Orientation.Vertical
        val spacingPixels = spacing.toPixels()

        val visibleStart = if (isVertical) scrollY else scrollX
        val visibleEnd = visibleStart + if (isVertical) bounds.height else bounds.width

        var position = startPadding()

        for (row in rowSizes.indices) {
            val size = rowSizes[row]
            val isInBounds = position + size > visibleStart && position < visibleEnd

            val first = row * columnCount
            val last = min(items.size, first + columnCount)

            for (index in first until last) {
                val item = items[index]

                if (isInBounds) {
                    bindComponent(item)
                } else {
                    boundComponents[item]?.let { component ->
                        if (component.isRecyclable) {
                            unbindComponent(item, component)
                        }
                    }
                }
            }

            position += size + spacingPixels
        }
    }

    /**
     * Binds a component to the given item and runs the block on it, so it isn't recycled until it goes out
     * of the visible area again.
     */
    fun applyComponent(item: T, block: C.(item: T) -> Unit): C? {
        val component = bindComponent(item) ?: return null
        component.block(item)
        return component
    }

    /**
     * The position of the given item along the scrolling axis, from the start of the content, which is
     * the one of its row, following the last measured size of every row. Null if the item isn't in [data].
     */
    fun positionOf(item: T): Float? {
        val index = itemIndices[item] ?: return null
        val row = index / columnCount

        var position = startPadding()
        for (i in 0 until min(row, rowSizes.size)) {
            position += rowSizes[i]
        }
        return position + spacing.toPixels() * row
    }

    override fun onUpdate(clock: FrameClock) {
        updateBoundComponents()
        super.onUpdate(clock)
    }


    /**
     * The only child of a [RecyclerContainer], places the bound components following the size of every row.
     */
    inner class RecyclerComponentWrapper : Container() {

        init {
            width = Dimension.FillAvailable
        }

        override fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints): Vec2 {
            val isVertical = orientation == LinearContainer.Orientation.Vertical
            val columnSpacingPixels = columnSpacing.toPixels()

            val newColumnCount = if (isVertical) {
                gridColumnCount(contentConstraints.availableWidth, columns, minColumnWidth, columnSpacingPixels)
            } else 1

            // The rows hold other items now, so their sizes are estimated again.
            if (newColumnCount != columnCount) {
                columnCount = newColumnCount
                rowSizes = FloatArray(rowCountOf(items.size)) { estimatedRowSize }
            }

            var childConstraints = contentConstraints.loosen()

            cellWidth = if (isVertical && contentConstraints.availableWidth.isFinite()) {
                gridCellWidth(contentConstraints.availableWidth, columnCount, columnSpacingPixels)
            } else 0f

            if (columnCount > 1 && cellWidth > 0f) {
                childConstraints = childConstraints.copy(maxWidth = cellWidth)
            }

            val measuredRows = HashMap<Int, Float>()

            for ((item, component) in boundComponents) {
                val index = itemIndices[item] ?: continue
                val size = component.measure(childConstraints.withoutFillLimits(component))
                val row = index / columnCount

                measuredRows[row] = max(measuredRows[row] ?: 0f, if (isVertical) size.y else size.x)
            }

            for ((row, size) in measuredRows) {
                if (row >= rowSizes.size) continue
                rowSizes[row] = size

                // Rows that were never measured take the first known size.
                if (estimatedRowSize == 0f) {
                    estimatedRowSize = size
                    rowSizes.indices.forEach { if (rowSizes[it] == 0f) rowSizes[it] = estimatedRowSize }
                }
            }

            val itemsSize = rowSizes.sum() + spacing.toPixels() * max(0, rowSizes.size - 1)

            val contentSize = if (isVertical) {
                Vec2(contentConstraints.availableWidth, itemsSize)
            } else {
                Vec2(itemsSize, contentConstraints.availableHeight)
            }

            return parentConstraints.constrain(contentSize.expand(padding.toVec4()))
        }

        override fun onLayout(bounds: Vec4) {
            val isVertical = orientation == LinearContainer.Orientation.Vertical
            val spacingPixels = spacing.toPixels()
            val columnSpacingPixels = columnSpacing.toPixels()

            val left = paddingLeft.toPixels()
            val right = bounds.width - paddingRight.toPixels()
            val cell = if (columnCount > 1) cellWidth else right - left

            var position = if (isVertical) paddingTop.toPixels() else left

            for (row in rowSizes.indices) {
                val first = row * columnCount
                val last = min(items.size, first + columnCount)

                for (index in first until last) {
                    val component = boundComponents[items[index]] ?: continue

                    component.layout(
                        if (isVertical) {
                            val x = left + (index - first) * (cell + columnSpacingPixels)
                            Vec4(x, position, x + cell, position + rowSizes[row])
                        } else {
                            Vec4(position, paddingTop.toPixels(), position + rowSizes[row], bounds.height - paddingBottom.toPixels())
                        }
                    )
                }

                position += rowSizes[row] + spacingPixels
            }
        }

        // Children sizes change while being animated, and those aren't always known from the outside.
        override fun onUpdate(clock: FrameClock) {
            invalidateMeasure()
            super.onUpdate(clock)
        }
    }
}

/**
 * A component of a [RecyclerContainer], it gets bound to an item while it is visible.
 */
abstract class RecyclableComponent<T : Any> : Container() {

    /**
     * The item this component is bound to. It is set by the [RecyclerContainer].
     */
    var boundData: T? = null
        internal set

    /**
     * Whether this component is bound to an item.
     */
    val isBound
        get() = boundData != null

    /**
     * Whether this component can be recycled.
     */
    abstract val isRecyclable: Boolean

    /**
     * Called when this component is bound to an item.
     */
    abstract fun onBind(data: T)

    /**
     * Called when this component is recycled, usually used to reset its state.
     */
    open fun onRecycle() = Unit

    override fun onInputEvent(event: InputEvent) = isBound && super.onInputEvent(event)
}
