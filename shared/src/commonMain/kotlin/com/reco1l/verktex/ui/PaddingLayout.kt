package com.reco1l.verktex.ui

import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Vec4
import kotlin.math.max

/**
 * How a [Padding] measures and places its component: a component holds one, created by its padding
 * ([Padding.createLayout]), since the behaviours keep state of their own while a padding is a value
 * components share. The behaviours wrap the one of their base padding.
 */
internal abstract class PaddingLayout {

    /**
     * The [Padding.layoutKind] of the paddings placing a component with this layout.
     */
    abstract val kind: Int

    /**
     * Where a component was placed and how much its content is inset by, on top of its padding's sides,
     * as left, top, right and bottom.
     */
    class Placement(val bounds: Vec4, val inset: Vec4)

    /**
     * How much the component grew past the bounds it was given on each side, as left, top, right and bottom,
     * which a child covering its padding covers too.
     */
    open val extent: Vec4
        get() = Vec4.Zero

    /**
     * The constraints [component] is measured with, from the ones its parent [requested].
     */
    open fun measureConstraints(component: Component, requested: LayoutConstraints): LayoutConstraints = requested

    /**
     * The padding the content of the component keeps while measured, from its padding's [sides].
     */
    open fun measurePadding(sides: Vec4): Vec4 = sides

    /**
     * Places [component] within the bounds its parent [assigned], [placeIn] placing its measured size in an
     * area as its anchor, origin and position say.
     */
    abstract fun place(component: Component, assigned: Vec4, placeIn: (Vec4) -> Vec4): Placement


    /**
     * A padding of sides alone.
     */
    object Plain : PaddingLayout() {
        override val kind get() = PLAIN

        override fun place(component: Component, assigned: Vec4, placeIn: (Vec4) -> Vec4) =
            Placement(placeIn(assigned), Vec4.Zero)
    }

    /**
     * Covers the parent's padding, see [Padding.coverParentPadding]: the component is measured and placed as
     * if its parent had none, then grown by how much the parent itself grew past its bounds.
     */
    class CoverParent(private val base: PaddingLayout) : PaddingLayout() {

        override val kind = base.kind or COVER_PARENT

        override val extent get() = base.extent

        override fun measureConstraints(component: Component, requested: LayoutConstraints): LayoutConstraints {
            val constraints = base.measureConstraints(component, requested)
            val covered = component.parent?.measurePadding ?: return constraints
            if (covered == Vec4.Zero) return constraints

            return LayoutConstraints(
                minWidth = constraints.minWidth + covered.x + covered.z,
                maxWidth = constraints.maxWidth + covered.x + covered.z,
                minHeight = constraints.minHeight + covered.y + covered.w,
                maxHeight = constraints.maxHeight + covered.y + covered.w
            )
        }

        override fun measurePadding(sides: Vec4) = base.measurePadding(sides)

        override fun place(component: Component, assigned: Vec4, placeIn: (Vec4) -> Vec4): Placement {
            val parent = component.parent ?: return base.place(component, assigned, placeIn)

            // Over the padding the parent measured its content with, then grown by how much the parent grew.
            val growth = parent.paddingLayout.extent
            val covered = parent.layoutPadding - growth
            val area = Vec4(
                x = assigned.x - covered.x,
                y = assigned.y - covered.y,
                z = assigned.z + covered.z,
                w = assigned.w + covered.w
            )

            return base.place(component, area) { placeIn(it).grow(growth) }
        }
    }

    /**
     * Grows into the safe area of the scene, see [Padding.extendIntoSafeArea]: on each side placed against the
     * safe area (or within it), the component grows to the edge of the scene (the window unless the scene is
     * embedded) and its content is inset by the safe area.
     */
    class SafeArea(private val base: PaddingLayout) : PaddingLayout() {

        override val kind = base.kind or SAFE_AREA

        override var extent = Vec4.Zero
            private set

        // The part of the safe area padding the component without growing into it, e.g. all of it for a
        // component already at the edges of the scene. It's measured with it, found when laid out.
        private var padding = Vec4.Zero

        // The constraints the parent last measured the component with, to measure it again when laying it
        // out finds another safe area padding.
        private var requested: LayoutConstraints? = null

        override fun measureConstraints(component: Component, requested: LayoutConstraints): LayoutConstraints {
            this.requested = requested
            return base.measureConstraints(component, requested)
        }

        override fun measurePadding(sides: Vec4) = base.measurePadding(sides) + padding

        override fun place(component: Component, assigned: Vec4, placeIn: (Vec4) -> Vec4): Placement {
            var placement = base.place(component, assigned, placeIn)
            var (growth, inset) = compute(component, placement.bounds)

            // The content was measured with another part of the safe area than what the bounds don't grow
            // into, e.g. the first time a component at the edges is laid out: measured again.
            val requested = requested
            if (inset - growth != padding && requested != null) {
                padding = inset - growth
                component.measure(requested)
                placement = base.place(component, assigned, placeIn)
                compute(component, placement.bounds).let { (g, i) -> growth = g; inset = i }
            }

            extent = growth
            return Placement(placement.bounds.grow(growth), placement.inset + inset)
        }

        // How much the component grows into the safe area of its scene from the given bounds, and how much
        // its content is inset by it.
        private fun compute(component: Component, bounds: Vec4): Pair<Vec4, Vec4> {
            // A scene is the reference of its own safe area, at its edges. Outside of a scene there's none.
            val scene = component as? Scene ?: component.findScene() ?: return Vec4.Zero to Vec4.Zero
            val isScene = scene === component
            val safeArea = scene.safeArea
            val sceneWidth = if (isScene) bounds.width else scene.bounds.width
            val sceneHeight = if (isScene) bounds.height else scene.bounds.height

            // The layout positions of the ancestors up to the scene, the transformations are only
            // applied when drawing and aren't up to date yet.
            var sceneX = if (isScene) 0f else bounds.x
            var sceneY = if (isScene) 0f else bounds.y
            var ancestor = if (isScene) null else component.parent
            while (ancestor != null && ancestor !== scene) {
                sceneX += ancestor.bounds.x
                sceneY += ancestor.bounds.y
                ancestor = ancestor.parent
            }

            val distances = Vec4(
                x = sceneX,
                y = sceneY,
                z = sceneWidth - sceneX - bounds.width,
                w = sceneHeight - sceneY - bounds.height
            )

            // A side is placed against the safe area when it doesn't reach past it. The inset can be
            // negative, e.g. the top of a scene shifted up by the on-screen keyboard.
            fun touches(inset: Float, distance: Float) = inset != 0f && distance <= max(inset, 0f) + TOLERANCE
            fun growth(inset: Float, distance: Float) = if (touches(inset, distance)) distance else 0f
            fun inset(inset: Float, distance: Float) = if (touches(inset, distance)) inset else 0f

            return Vec4(
                x = growth(safeArea.x, distances.x),
                y = growth(safeArea.y, distances.y),
                z = growth(safeArea.z, distances.z),
                w = growth(safeArea.w, distances.w)
            ) to Vec4(
                x = inset(safeArea.x, distances.x),
                y = inset(safeArea.y, distances.y),
                z = inset(safeArea.z, distances.z),
                w = inset(safeArea.w, distances.w)
            )
        }

        private companion object {
            // How far from the safe area a side still counts as placed against it.
            const val TOLERANCE = 1f
        }
    }


    companion object {
        const val PLAIN = 0
        const val COVER_PARENT = 1
        const val SAFE_AREA = 2

        // The bounds grown by the given amount on each side.
        private fun Vec4.grow(by: Vec4): Vec4 {
            if (by == Vec4.Zero) return this
            return Vec4(x = x - by.x, y = y - by.y, z = z + by.z, w = w + by.w)
        }
    }
}
