package com.reco1l.verktex.ui.container

import com.reco1l.verktex.*
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.ui.Component
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.input.ScrollEvent
import com.reco1l.verktex.input.VelocityTracker
import com.reco1l.verktex.math.almostEquals
import com.reco1l.verktex.math.almostZero
import com.reco1l.verktex.time.FrameClock
import kotlin.math.*


inline fun Container.scrollableContainer(builder: ScrollableContainer.() -> Unit): ScrollableContainer {
    return ScrollableContainer().apply(builder).also(::plusAssign)
}

open class ScrollableContainer : Container() {

    /**
     * Determines which axes can be scrolled by the user.
     */
    var scrollAxes: Axis = Axis.Both
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
        }

    /**
     * Determines which axes can be overflowed by the scroll.
     */
    var overflowAxes: Axis = Axis.Both

    /**
     * The flag to indicate if the container is being scrolled by the user. Container
     * can be scrolled by the user or by itself (e.g. animations).
     */
    var isUserScrolling = false
        private set

    /**
     * Whether the content is anchored to the end of the scrollable axes, as a chat's: content shorter
     * than the container is placed at its end, and while the scroll is at the end it stays there as the
     * content's size changes. Scrolled away from the end, the scroll position is kept.
     */
    var isAnchoredToEnd = false
        set(value) {
            if (field == value) return
            field = value
            isAtEndX = true
            isAtEndY = true
            invalidateLayout()
        }

    /**
     * Whether to prevent scrolling on this container and all its children.
     */
    var preventScrolling = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    velocityX = 0f
                    velocityY = 0f
                }
            }
        }

    /**
     * Whether the container is scrolling or not.
     */
    val isScrolling
        get() = velocityX != 0f || velocityY != 0f || isUserScrolling

    //region Scrolling properties

    /**
     * The scroll position on the x-axis.
     */
    var scrollX = 0f
        set(value) {
            val scroll = when {
                value.almostZero() -> 0f
                value.almostEquals(maxScrollX) -> maxScrollX
                else -> value.toInt().toFloat()
            }

            if (field != scroll) {
                field = scroll
                onScroll(Axis.X)
            }
        }

    /**
     * The scroll position on the y-axis.
     */
    var scrollY = 0f
        set(value) {
            val scroll = when {
                value.almostZero() -> 0f
                value.almostEquals(maxScrollY) -> maxScrollY
                else -> value.toInt().toFloat()
            }

            if (field != scroll) {
                field = scroll
                onScroll(Axis.Y)
            }
        }

    /**
     * The velocity in px/s on the x-axis.
     */
    var velocityX = 0f
        private set(value) {
            val velocity = if (value.almostZero()) 0f else {
                val max = maxVelocity.x * 1f.dip.toPixels()
                value.coerceIn(-max, max)
            }

            if (field != velocity) {
                field = velocity
                setHierarchyScrollPrevention(velocity != 0f)
            }
        }

    /**
     * The velocity in px/s on the y-axis.
     */
    var velocityY = 0f
        private set(value) {
            val velocity = if (value.almostZero()) 0f else {
                val max = maxVelocity.y * 1f.dip.toPixels()
                value.coerceIn(-max, max)
            }

            if (field != velocity) {
                field = velocity
                setHierarchyScrollPrevention(velocity != 0f)
            }
        }

    /**
     * The fraction of the velocity kept after flinging for one second, for both axes. Lower values
     * stop the content sooner.
     */
    var deceleration = Vec2(DEFAULT_DECELERATION)

    /**
     * The maximum velocity in dip/s for both axes.
     */
    var maxVelocity = Vec2(DEFAULT_MAX_VELOCITY)

    /**
     * The minimum travel distance in px for both axes.
     *
     * This is used to determine if the user has scrolled enough to start
     * scrolling the container.
     */
    var minimumTravel = Vec2(DEFAULT_MINIMUM_TRAVEL)

    /**
     * Called once the content is scrolled within [endThreshold] of its end along the scroll axis (the
     * vertical one when it scrolls on both), or when it's shorter than the container. It isn't called
     * again until the content's size changes or it's scrolled away from the end, e.g. to load more
     * content.
     */
    var onScrolledToEnd: (() -> Unit)? = null

    /**
     * How far from the end of the content [onScrolledToEnd] is called.
     */
    var endThreshold: Dimension.Fixed = 0f.dip

    //endregion

    //region Indicators

    /**
     * Whether to show the horizontal scroll indicator.
     */
    var showHorizontalIndicator = true

    /**
     * Whether to show the vertical scroll indicator.
     */
    var showVerticalIndicator = true


    //endregion

    //region Dimension properties

    /**
     * The padding for the scrollable content.
     */
    var scrollPadding = Vec2(0f, 0f)

    /**
     * The maximum scroll position on the x-axis.
     *
     * This does not take into account the overscroll.
     */
    val maxScrollX
        get() = max(0f, scrollableContentWidth - bounds.width)

    /**
     * The maximum scroll position on the y-axis.
     *
     * This does not take into account the overscroll.
     */
    val maxScrollY
        get() = max(0f, scrollableContentHeight - bounds.height)

    /**
     * The width of the content that can be scrolled.
     */
    val scrollableContentWidth
        get() = max(0f, contentSize.x) + scrollPadding.x

    /**
     * The height of the content that can be scrolled.
     */
    val scrollableContentHeight
        get() = max(0f, contentSize.y) + scrollPadding.y

    /**
     * The size of the children, measured without limits on the scrollable axes.
     */
    var contentSize = Vec2.Zero
        private set


    override fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints): Vec2 {

        // Children may grow beyond this container along the scrollable axes.
        val childConstraints = contentConstraints.loosen().copy(
            maxWidth = if (scrollAxes.isHorizontal) Float.POSITIVE_INFINITY else contentConstraints.maxWidth,
            maxHeight = if (scrollAxes.isVertical) Float.POSITIVE_INFINITY else contentConstraints.maxHeight
        )

        var contentWidth = 0f
        var contentHeight = 0f

        forEach { child ->
            val childSize = child.measure(childConstraints.withoutFillLimits(child))
            contentWidth = max(contentWidth, childSize.x)
            contentHeight = max(contentHeight, childSize.y)
        }

        contentSize = Vec2(contentWidth, contentHeight)

        // The own constraints, so the limits of this container (e.g. minWidth) are what filling
        // children are stretched to.
        val measuredSize = contentConstraints.constrain(contentSize).expand(measurePadding)
        stretchFillingChildren(measuredSize) { childConstraints }
        return measuredSize
    }

    // Scrollable axes are unbounded, so filling them is the same as wrapping.
    override fun fillsWrappedWidth(child: Component) = !scrollAxes.isHorizontal && super.fillsWrappedWidth(child)

    override fun fillsWrappedHeight(child: Component) = !scrollAxes.isVertical && super.fillsWrappedHeight(child)

    override fun onLayout(bounds: Vec4) {
        var offsetX = 0f
        var offsetY = 0f

        if (isAnchoredToEnd) {
            // Kept at the end as the content's size changes, unless the user is moving it.
            if (!isScrolling) {
                if (scrollAxes.isHorizontal && isAtEndX) scrollX = maxScrollX
                if (scrollAxes.isVertical && isAtEndY) scrollY = maxScrollY
            }

            // Content shorter than the container is placed at its end.
            val innerWidth = bounds.width - layoutPadding.x - layoutPadding.z
            val innerHeight = bounds.height - layoutPadding.y - layoutPadding.w
            if (scrollAxes.isHorizontal) offsetX = max(0f, innerWidth - scrollableContentWidth)
            if (scrollAxes.isVertical) offsetY = max(0f, innerHeight - scrollableContentHeight)
        }

        // Children are offset by the current scroll position.
        val contentBounds = Vec4(
            x = layoutPadding.x - scrollX + offsetX,
            y = layoutPadding.y - scrollY + offsetY,
            z = bounds.width - layoutPadding.z - scrollX + offsetX,
            w = bounds.height - layoutPadding.w - scrollY + offsetY
        )

        forEach { child -> child.layout(contentBounds) }
    }

    // Whether the scroll was at the end at the last update, before the content's size changes in the
    // next layout, see isAnchoredToEnd.
    private var isAtEndX = true
    private var isAtEndY = true

    //endregion

    private var indicatorYAlpha = 0f
    private var indicatorXAlpha = 0f

    // Where the mouse wheel is taking the content to, NaN when it isn't, see scrollByWheel.
    private var wheelTargetX = Float.NaN
    private var wheelTargetY = Float.NaN

    private val velocityTracker = VelocityTracker()

    private var lastPointerX = 0f
    private var lastPointerY = 0f
    private var pointerTravel = 0f
    private var dragX = 0f
    private var dragY = 0f

    // The finger dragging this container, other fingers are left to the children.
    private var dragPointerId = -1


    /**
     * Stops the scrolling of the container by setting the velocity to 0.
     */
    fun stopScroll() {
        velocityX = 0f
        velocityY = 0f
        wheelTargetX = Float.NaN
        wheelTargetY = Float.NaN
    }


    //region Callbacks

    /**
     * Called when the content scrolls on the given axes. Overrides must call `super`, it shows the
     * scroll indicators.
     */
    open fun onScroll(axes: Axis) {

        if (axes.isVertical) {
            indicatorYAlpha = 0.5f
        }

        if (axes.isHorizontal) {
            indicatorXAlpha = 0.5f
        }

        invalidateTransformations()
    }

    //endregion

    //region Scrolling

    private fun decelerateProgressively(deltaTimeSec: Float) {
        scrollX -= velocityX * deltaTimeSec
        scrollY -= velocityY * deltaTimeSec

        // Time based, so the content glides the same distance at any frame rate.
        velocityX *= deceleration.x.pow(deltaTimeSec)
        velocityY *= deceleration.y.pow(deltaTimeSec)

        // The decay never reaches zero, stop once the movement isn't noticeable anymore.
        val minimumVelocity = MINIMUM_FLING_VELOCITY.toPixels()
        if (abs(velocityX) < minimumVelocity) velocityX = 0f
        if (abs(velocityY) < minimumVelocity) velocityY = 0f
    }

    // Eases the content to where the wheel takes it, the same way at any frame rate.
    private fun approachWheelTarget(deltaTimeSec: Float) {
        val progress = 1f - WHEEL_EASING.pow(deltaTimeSec)

        if (!wheelTargetX.isNaN()) {
            val distance = wheelTargetX - scrollX
            if (abs(distance) <= 1f) {
                scrollX = wheelTargetX
                wheelTargetX = Float.NaN
            } else {
                scrollX += easedStep(distance, progress)
            }
        }

        if (!wheelTargetY.isNaN()) {
            val distance = wheelTargetY - scrollY
            if (abs(distance) <= 1f) {
                scrollY = wheelTargetY
                wheelTargetY = Float.NaN
            } else {
                scrollY += easedStep(distance, progress)
            }
        }
    }

    // The scroll positions are whole pixels, each step moves at least one so the easing ends.
    private fun easedStep(distance: Float, progress: Float): Float {
        val step = distance * progress
        return if (abs(step) < 1f) sign(distance) else step
    }

    private fun handleOverflow() {

        if (scrollX !in 0f..maxScrollX) {
            velocityX = 0f

            if (overflowAxes.isHorizontal) {
                val bounceBack = (if (scrollX < 0f) -scrollX else -(scrollX - maxScrollX)) * 0.2f

                if (abs(bounceBack) < .5f) {
                    scrollX = if (scrollX < 0f) 0f else maxScrollX
                } else {
                    scrollX += bounceBack
                }
            } else {
                scrollX = if (scrollX < 0f) 0f else maxScrollX
            }
        }

        if (scrollY !in 0f..maxScrollY) {
            velocityY = 0f

            if (overflowAxes.isVertical) {
                val bounceBack = (if (scrollY < 0f) -scrollY else -(scrollY - maxScrollY)) * 0.2f

                if (abs(bounceBack) < .5f) {
                    scrollY = if (scrollY < 0f) 0f else maxScrollY
                } else {
                    scrollY += bounceBack
                }
            } else {
                scrollY = if (scrollY < 0f) 0f else maxScrollY
            }
        }
    }

    override fun onUpdate(clock: FrameClock) {
        super.onUpdate(clock)

        if (!isUserScrolling) {
            if (!wheelTargetX.isNaN() || !wheelTargetY.isNaN()) {
                approachWheelTarget(clock.elapsedFrameTime)
            } else if (isScrolling) {
                decelerateProgressively(clock.elapsedFrameTime)
            }
            handleOverflow()
        }

        if (isAnchoredToEnd) {
            isAtEndX = scrollX >= maxScrollX - END_TOLERANCE
            isAtEndY = scrollY >= maxScrollY - END_TOLERANCE
        }

        updateIndicators(clock.elapsedFrameTime)
        notifyScrolledToEnd()
    }

    // The content's size the last time onScrolledToEnd was called, so it's called again once it grows.
    private var scrolledToEndContentSize = -1f

    private fun notifyScrolledToEnd() {
        val callback = onScrolledToEnd ?: return

        val isVertical = scrollAxes.isVertical
        val contentLength = if (isVertical) scrollableContentHeight else scrollableContentWidth
        val remaining = if (isVertical) maxScrollY - scrollY else maxScrollX - scrollX

        if (contentLength <= 0f) {
            return
        }

        if (remaining <= endThreshold.toPixels()) {
            if (contentLength != scrolledToEndContentSize) {
                scrolledToEndContentSize = contentLength
                callback()
            }
        } else {
            scrolledToEndContentSize = -1f
        }
    }

    override fun onDraw(renderer: Renderer) {
        super.onDraw(renderer)

        val indicatorThickness = INDICATOR_THICKNESS.toPixels()

        if (showVerticalIndicator && (scrollAxes == Axis.Both || scrollAxes == Axis.Y)) {
            val indicatorHeight = bounds.height * (bounds.height / scrollableContentHeight).coerceAtMost(1f)

            renderer.drawRect(
                x = bounds.width - indicatorThickness,
                y = scrollY * (bounds.height / scrollableContentHeight),
                width = indicatorThickness,
                height = indicatorHeight,
                color = Color4.White.alpha(indicatorYAlpha)
            )
        }

        if (showHorizontalIndicator && (scrollAxes == Axis.Both || scrollAxes == Axis.X)) {
            val indicatorWidth = bounds.width * (bounds.width / scrollableContentWidth).coerceAtMost(1f)

            renderer.drawRect(
                x = scrollX * (bounds.width / scrollableContentWidth),
                y = bounds.height - indicatorThickness,
                width = indicatorWidth,
                height = indicatorThickness,
                color = Color4.White.alpha(indicatorXAlpha)
            )
        }
    }

    /**
     * Called to update the scroll indicators based on the current scroll position.
     */
    protected open fun updateIndicators(deltaTimeSec: Float) {

        if (showVerticalIndicator && (scrollAxes == Axis.Both || scrollAxes == Axis.Y)) {
            if (indicatorYAlpha > 0f && velocityY == 0f) {
                indicatorYAlpha = (indicatorYAlpha - deltaTimeSec * 0.75f).coerceAtLeast(0f)
            }
        }

        if (showHorizontalIndicator && (scrollAxes == Axis.Both || scrollAxes == Axis.X)) {
            if (indicatorXAlpha > 0f && velocityX == 0f) {
                indicatorXAlpha = (indicatorXAlpha - deltaTimeSec * 0.75f).coerceAtLeast(0f)
            }
        }
    }

    //endregion

    //region Input

    private fun handleUserScroll(deltaX: Float, deltaY: Float, velocityX: Float, velocityY: Float) {

        val length = hypot(deltaX, deltaY)

        fun decreaseInBoundary(current: Float, delta: Float, max: Float): Float {
            if (current - delta > 0f && current - delta < max) {
                return delta
            }
            return delta * if (length > 0) length.pow(0.7f) / length else 0f
        }

        if (scrollAxes.isHorizontal && !deltaX.almostZero()) {
            this.velocityX = velocityX
            scrollX -= decreaseInBoundary(scrollX, deltaX, maxScrollX)

            if (!overflowAxes.isHorizontal) {
                scrollX = scrollX.coerceIn(0f, maxScrollX)
            }
        }

        if (scrollAxes.isVertical && !deltaY.almostZero()) {
            this.velocityY = velocityY
            scrollY -= decreaseInBoundary(scrollY, deltaY, maxScrollY)

            if (!overflowAxes.isVertical) {
                scrollY = scrollY.coerceIn(0f, maxScrollY)
            }
        }
    }

    override fun onInputEvent(event: InputEvent): Boolean {

        if (preventScrolling || event !is ScrollEvent && event !is PointerEvent) {
            return super.onInputEvent(event)
        }

        val wasUserScrolling = isUserScrolling

        if (event is PointerEvent) {
            val isDragPointer = event.pointerId == dragPointerId

            if (!isDragPointer && (event.action != PointerEvent.Action.Down || dragPointerId != -1)) {
                return super.onInputEvent(event)
            }

            // A right click stands for a long press, it doesn't drag the content.
            if (event.action == PointerEvent.Action.Down && event.button == PointerEvent.Button.Right
                && Verktex.options.rightClickAsLongPress) {
                return super.onInputEvent(event)
            }

            when (event.action) {
                PointerEvent.Action.Down -> {
                    dragPointerId = event.pointerId
                    velocityTracker.clear()
                    velocityTracker.addSample(event.timeMillis(), event.x, event.y)
                    lastPointerX = event.x
                    lastPointerY = event.y
                    pointerTravel = 0f
                    dragX = 0f
                    dragY = 0f
                    stopScroll()
                }

                PointerEvent.Action.Move -> {
                    val deltaX = event.x - lastPointerX
                    val deltaY = event.y - lastPointerY
                    lastPointerX = event.x
                    lastPointerY = event.y
                    pointerTravel += hypot(deltaX, deltaY)
                    dragX += deltaX
                    dragY += deltaY
                    velocityTracker.addSample(event.timeMillis(), event.x, event.y)

                    // Nested scrollable containers get the drag first, this one only takes it over
                    // when none of them is scrolling and it goes along one of its scroll axes.
                    if (!isUserScrolling) {
                        val consumedByChild = super.onInputEvent(event)

                        val isHorizontalDrag = abs(dragX) > abs(dragY)
                        val canScrollAlongDrag = if (isHorizontalDrag) scrollAxes.isHorizontal else scrollAxes.isVertical

                        if (hasScrollingDescendant() || pointerTravel <= minimumTravel.y || !canScrollAlongDrag) {
                            return consumedByChild
                        }

                        // Cancel the press of any child now that this is a drag.
                        super.onInputEvent(event.copy(action = PointerEvent.Action.Unknown))
                    }

                    handleUserScroll(deltaX, deltaY, velocityTracker.velocityX, velocityTracker.velocityY)
                    isUserScrolling = true
                }

                PointerEvent.Action.Up -> {
                    // Fling with the velocity the finger had when lifted.
                    if (isUserScrolling) {
                        velocityTracker.release(event.timeMillis())
                        if (scrollAxes.isHorizontal) velocityX = velocityTracker.velocityX
                        if (scrollAxes.isVertical) velocityY = velocityTracker.velocityY
                    }
                    isUserScrolling = false
                    dragPointerId = -1
                }

                PointerEvent.Action.Unknown, PointerEvent.Action.Cancel -> {
                    isUserScrolling = false
                    dragPointerId = -1
                }
            }
        } else {
            // Nested containers under the pointer get the wheel first.
            return super.onInputEvent(event) || scrollByWheel(event as ScrollEvent)
        }

        if (!wasUserScrolling && !isUserScrolling) {
            return super.onInputEvent(event)
        }
        return true
    }


    // The time of the event, or the one of the frame when the platform didn't give one.
    private fun PointerEvent.timeMillis() = if (time != 0L) time else (Verktex.clock.time * 1000f).toLong()

    /**
     * Scrolls by the notches of a mouse wheel ([com.reco1l.verktex.VerktexOptions.wheelScrollStep]
     * each), easing the content there. A vertical wheel scrolls containers that only scroll
     * horizontally. Returns false when the content can't move that way, so the parents scroll.
     */
    private fun scrollByWheel(event: ScrollEvent): Boolean {
        val step = Verktex.options.wheelScrollStep.toPixels()

        var deltaX = event.deltaX
        var deltaY = event.deltaY
        if (scrollAxes == Axis.X && deltaX == 0f) {
            deltaX = deltaY
            deltaY = 0f
        }

        // Positive deltas are up and left, they move towards the start.
        val fromX = if (wheelTargetX.isNaN()) scrollX else wheelTargetX
        val fromY = if (wheelTargetY.isNaN()) scrollY else wheelTargetY
        val toX = if (scrollAxes.isHorizontal) (fromX - deltaX * step).coerceIn(0f, maxScrollX) else fromX
        val toY = if (scrollAxes.isVertical) (fromY - deltaY * step).coerceIn(0f, maxScrollY) else fromY

        val movesX = !toX.almostEquals(fromX)
        val movesY = !toY.almostEquals(fromY)
        if (!movesX && !movesY) {
            return false
        }

        // A fling in progress is replaced by the wheel.
        velocityX = 0f
        velocityY = 0f
        if (movesX) wheelTargetX = toX
        if (movesY) wheelTargetY = toY
        return true
    }

    private fun hasScrollingDescendant() = propagateConsumable {
        it !== this && it is ScrollableContainer && it.isUserScrolling
    }

    //endregion


    companion object {

        // How close to the end, in pixels, the scroll counts as at the end.
        private const val END_TOLERANCE = 1f

        const val DEFAULT_DECELERATION = 0.15f
        const val DEFAULT_MINIMUM_TRAVEL = 20f
        const val DEFAULT_MAX_VELOCITY = 8000f

        // The fraction of the distance to the wheel's target left after one second.
        private const val WHEEL_EASING = 0.00001f

        private val INDICATOR_THICKNESS = 3f.dip
        private val MINIMUM_FLING_VELOCITY = 10f.dip

    }
}

/**
 * Sets the [ScrollableContainer.preventScrolling] property for all scrollable containers
 * in the hierarchy of this entity.
 */
fun Component.setHierarchyScrollPrevention(value: Boolean) {

    var parent = parent
    while (parent != null) {
        if (parent is ScrollableContainer) {
            parent.preventScrolling = value
        }
        parent = parent.parent
    }

    if (this is Container) {
        forEach { child ->
            if (child is ScrollableContainer) {
                child.preventScrolling = value
            }
        }
    }
}