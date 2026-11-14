package com.reco1l.verktex.ui.container

import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import kotlin.math.max
import kotlin.math.min

inline fun UIContainer.scalingContainer(builder: UIScalingContainer.() -> Unit): UIScalingContainer {
    return UIScalingContainer().apply(builder).also(::plusAssign)
}

/**
 * A container whose children are laid out in a space of a [referenceSize] and drawn scaled to the
 * space the container gets, e.g. a HUD designed for a 1280 pixels wide screen that keeps its
 * proportions on any screen.
 *
 * It's measured with the constraints of its parent divided by [factor], so its size and the space of
 * its children are in reference units: filling children cover the whole container, and pixel sizes
 * and positions are reference pixels. It's then drawn scaled by [factor], so drawing, hit testing
 * and clipping follow on their own. Pointer events stay in scene pixels, see [toLocalDelta].
 *
 * It fills its parent by default. It owns its [scaleX], [scaleY] and [scaleOrigin], which are set to
 * [factor] from its top left corner, and a parent wrapping its content sees its size in reference
 * units.
 */
open class UIScalingContainer : UIContainer() {

    /**
     * How the [factor] is taken from the available space and the [referenceSize].
     */
    enum class Mode {
        /**
         * The available width over the reference width.
         */
        Width,

        /**
         * The available height over the reference height.
         */
        Height,

        /**
         * The smaller of the two, so the reference size fits in the available space.
         */
        Fit,

        /**
         * The larger of the two, so the reference size covers the available space.
         */
        Fill
    }


    /**
     * The size the children are designed for, in reference units.
     */
    var referenceSize = Vec2(1280f, 720f)
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
        }

    /**
     * How the [factor] is taken, see [Mode].
     */
    var mode = Mode.Width
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
        }

    /**
     * The scale from reference units to the parent's, from the last measure pass.
     */
    var factor = 1f
        private set


    init {
        width = fill()
        height = fill()
        scaleOrigin = Anchor.TopLeft
    }


    /**
     * Converts a distance in scene pixels, e.g. between two pointer events, to reference units.
     */
    fun toLocalDelta(x: Float, y: Float): Vec2 {
        val origin = toLocalSpaceFromScene(0f, 0f)
        val point = toLocalSpaceFromScene(x, y)
        return Vec2(point.x - origin.x, point.y - origin.y)
    }


    private fun factorFor(availableWidth: Float, availableHeight: Float): Float {
        val horizontal = availableWidth / referenceSize.x
        val vertical = availableHeight / referenceSize.y

        val factor = when (mode) {
            Mode.Width -> horizontal
            Mode.Height -> vertical
            Mode.Fit -> min(horizontal, vertical)
            Mode.Fill -> max(horizontal, vertical)
        }

        // An unbounded or empty axis gives nothing to scale to.
        return if (factor.isFinite() && factor > 0f) factor else 1f
    }

    override fun adjustParentConstraints(constraints: LayoutConstraints): LayoutConstraints {
        val factor = factorFor(constraints.availableWidth, constraints.availableHeight)

        this.factor = factor
        scaleX = factor
        scaleY = factor

        return LayoutConstraints(
            minWidth = constraints.minWidth / factor,
            maxWidth = constraints.maxWidth / factor,
            minHeight = constraints.minHeight / factor,
            maxHeight = constraints.maxHeight / factor
        )
    }

    override fun adjustBounds(bounds: Vec4): Vec4 {
        // The origin is taken from the measured size, which is in reference units, while the
        // container covers it scaled in its parent.
        val offsetX = measuredSize.x * origin.x * (factor - 1f)
        val offsetY = measuredSize.y * origin.y * (factor - 1f)

        if (offsetX == 0f && offsetY == 0f) {
            return bounds
        }
        return Vec4(bounds.x - offsetX, bounds.y - offsetY, bounds.z - offsetX, bounds.w - offsetY)
    }
}
