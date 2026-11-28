package com.reco1l.verktex.ui

import com.reco1l.verktex.PerformanceStats
import com.reco1l.verktex.localization.Localization
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.input.HoverEvent
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Shadow
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.Border
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.data.Mat2
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.px
import com.reco1l.verktex.math.almostEquals
import com.reco1l.verktex.settings.VerktexSettings
import com.reco1l.verktex.time.FrameClock
import kotlin.math.max
import kotlin.math.min

/**
 * The base class for all UI components.
 *
 * This class provides the basic functionality for size, position, transformations, drawing, updating,
 * input handling, and layout of UI components.
 *
 * All UI components should inherit from this class and implement the [onDraw], [onMeasure], and [onLayout]
 * methods to define their specific behavior.
 *
 * This class goes in hand with [Container], which is a special type of component that can
 * contain other components and manage their layout.
 */
abstract class Component {

    /**
     * The theme of this component and its children, see [ThemeScope]. Declared first since themeable
     * properties register themselves while the component is constructed.
     */
    val theme = ThemeScope(this)

    //region Size

    /**
     * The width specification of the component.
     *
     * This specifies the dimension and how should it be interpreted, but it doesn't provide the real
     * value of it. To get the final value in pixels use [bounds].
     *
     * @see Dimension
     */
    var width: Dimension by themeable(Dimension.WrapContent) { invalidateMeasure() }

    /**
     * The height specification of the component.
     *
     * This specifies the dimension and how should it be interpreted, but it doesn't provide the real
     * value of it. To get the final value in pixels use [bounds].
     *
     * @see Dimension
     */
    var height: Dimension by themeable(Dimension.WrapContent) { invalidateMeasure() }

    /**
     * The minimum width of the component, either fixed or relative to the available width. It wins over [maxWidth].
     */
    var minWidth: Dimension.ConstraintDriven? = null
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
        }

    /**
     * The maximum width of the component, either fixed or relative to the available width.
     */
    var maxWidth: Dimension.ConstraintDriven? = null
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
        }

    /**
     * The minimum height of the component, either fixed or relative to the available height. It wins over [maxHeight].
     */
    var minHeight: Dimension.ConstraintDriven? = null
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
        }

    /**
     * The maximum height of the component, either fixed or relative to the available height.
     */
    var maxHeight: Dimension.ConstraintDriven? = null
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
        }

    /**
     * Called when the size of this component changes.
     */
    protected open fun onSizeChanged() {}

    //endregion

    //region Position

    /**
     * The X position specification of the component.
     *
     * This specifies the dimension and how should it be interpreted, but it doesn't provide the real
     * value of it. To get the final value in pixels use [bounds].
     *
     * @see Dimension.ConstraintDriven
     */
    var x: Dimension.ConstraintDriven = Dimension.Zero
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
        }

    /**
     * The Y position specification of the component.
     *
     * This specifies the dimension and how should it be interpreted, but it doesn't provide the real
     * value of it. To get the final value in pixels use [bounds].
     *
     * @see Dimension.ConstraintDriven
     */
    var y: Dimension.ConstraintDriven = Dimension.Zero
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
        }

    /**
     * Where the component should be anchored in the parent.
     *
     * @see Anchor
     */
    var anchor = Anchor.TopLeft
        set(value) {
            if (field == value) return
            field = value
            invalidateLayout()
        }

    /**
     * Where the component should be anchored in itself. This is relative to the component's size.
     *
     * @see Anchor
     */
    var origin = Anchor.TopLeft
        set(value) {
            if (field == value) return
            field = value
            invalidateLayout()
        }

    /**
     * Where the component should be rotated around. This is relative to the component's size.
     */
    var rotationOrigin = Anchor.Center
        set(value) {
            if (field == value) return
            field = value
            invalidateTransformations()
        }

    /**
     * Where the component should be scaled around. This is relative to the component's size.
     */
    var scaleOrigin = Anchor.Center
        set(value) {
            if (field == value) return
            field = value
            invalidateTransformations()
        }

    /**
     * Where the component should be skewed around. This is relative to the component's size.
     */
    var skewOrigin = Anchor.Center
        set(value) {
            if (field == value) return
            field = value
            invalidateTransformations()
        }


    /**
     * Called when the position of this component changes.
     */
    open fun onPositionChanged() {}

    //endregion

    //region Transformations

    /**
     * The scale in the X axis.
     */
    var scaleX = 1f
        set(value) {
            if (field.almostEquals(value)) return
            field = value
            invalidateTransformations()
        }

    /**
     * The scale in the Y axis.
     */
    var scaleY = 1f
        set(value) {
            if (field.almostEquals(value)) return
            field = value
            invalidateTransformations()
        }

    /**
     * The scale of the component. This is a convenience property that sets both [scaleX] and
     * [scaleY] to the same value.
     *
     * Its getter returns the maximum of [scaleX] and [scaleY], which can be useful for
     * determining the overall scale of the component.
     */
    var scale
        get() = max(scaleX, scaleY)
        set(value) {
            scaleX = value
            scaleY = value
        }

    /**
     * The translation in the X axis.
     *
     * Translations does not affect the layout system. Similarly to how CSS translate() works.
     */
    var translationX: Dimension.ConstraintDriven = Dimension.Zero
        set(value) {
            if (field == value) return
            field = value
            invalidateTransformations()
        }

    /**
     * The translation in the Y axis.
     *
     * Translations does not affect the layout system. Similarly to how CSS translate() works.
     */
    var translationY: Dimension.ConstraintDriven = Dimension.Zero
        set(value) {
            if (field == value) return
            field = value
            invalidateTransformations()
        }

    /**
     * The skew of the component in degrees along the X axis, around [skewOrigin]: points move
     * horizontally by the tangent of the angle times their distance below the origin, as CSS's
     * skewX(). It doesn't affect the layout.
     */
    var skewX = 0f
        set(value) {
            if (field.almostEquals(value)) return
            field = value
            invalidateTransformations()
        }

    /**
     * The skew of the component in degrees along the Y axis, around [skewOrigin]: points move
     * vertically by the tangent of the angle times their distance right of the origin, as CSS's
     * skewY(). It doesn't affect the layout.
     */
    var skewY = 0f
        set(value) {
            if (field.almostEquals(value)) return
            field = value
            invalidateTransformations()
        }

    /**
     * The rotation of the component in degrees of the X axis.
     */
    var rotationX = 0f
        set(value) {
            if (field.almostEquals(value)) return
            field = value
            invalidateTransformations()
        }

    /**
     * The rotation of the component in degrees of the Y axis.
     */
    var rotationY = 0f
        set(value) {
            if (field.almostEquals(value)) return
            field = value
            invalidateTransformations()
        }

    /**
     * The rotation of the component in degrees of the Z axis.
     */
    var rotationZ = 0f
        set(value) {
            if (field.almostEquals(value)) return
            field = value
            invalidateTransformations()
        }

    /**
     * The transformation matrix from local space to parent space.
     *
     * **It is not recomended to update this [Mat2] outside [calculateTransformations] as it might
     * cause inconsistencies on the layout system.**
     */
    protected val localToParentMatrix = Mat2()

    /**
     * The transformation matrix from parent space to local space.
     *
     * **It is not recomended to update this [Mat2] outside [calculateTransformations] as it might
     * cause inconsistencies on the layout system.**
     */
    protected val parentToLocalMatrix = Mat2()

    /**
     * The transformation matrix from local space to scene space.
     *
     * **It is not recomended to update this [Mat2] outside [calculateTransformations] as it might
     * cause inconsistencies on the layout system.**
     */
    protected val localToSceneMatrix = Mat2()

    /**
     * The transformation matrix from scene space to local space.
     *
     * **It is not recomended to update this [Mat2] outside [calculateTransformations] as it might
     * cause inconsistencies on the layout system.**
     */
    protected val sceneToLocalMatrix = Mat2()


    /**
     * Calculates the transformations of the component into the transformation matrices:
     * * [localToParentMatrix]
     * * [parentToLocalMatrix]
     * * [localToSceneMatrix]
     * * [sceneToLocalMatrix]
     *
     * Default implementation will apply the following transformations:
     * [x], [y], [scaleX], [scaleY], [skewX], [skewY], [rotationZ]
     */
    open fun calculateTransformations() {
        // Mat2 operations are applied after the existing ones, so the order here is the order in
        // which they affect a local point: scale, skew, rotate and finally move to the layout position.
        localToParentMatrix.apply {
            identity()

            if (scaleX != 1f || scaleY != 1f) {
                val scaleCenterX = bounds.width * scaleOrigin.x
                val scaleCenterY = bounds.height * scaleOrigin.y

                translate(-scaleCenterX, -scaleCenterY)
                scale(scaleX, scaleY)
                translate(scaleCenterX, scaleCenterY)
            }

            if (skewX != 0f || skewY != 0f) {
                val skewCenterX = bounds.width * skewOrigin.x
                val skewCenterY = bounds.height * skewOrigin.y

                translate(-skewCenterX, -skewCenterY)
                skew(skewX, skewY)
                translate(skewCenterX, skewCenterY)
            }

            if (rotationZ != 0f) {
                val rotationCenterX = bounds.width * rotationOrigin.x
                val rotationCenterY = bounds.height * rotationOrigin.y

                translate(-rotationCenterX, -rotationCenterY)
                rotate(rotationZ)
                translate(rotationCenterX, rotationCenterY)
            }

            // Fractions of the translation are relative to the component's own size, as in CSS.
            translate(bounds.x + translationX.resolve(bounds.width), bounds.y + translationY.resolve(bounds.height))
        }

        parentToLocalMatrix.set(localToParentMatrix)
        parentToLocalMatrix.invertOrNaN()

        val parent = parent

        // concat() applies the given matrix first, so the local transform goes into the parent's.
        if (parent != null) {
            localToSceneMatrix.set(parent.localToSceneMatrix)
            localToSceneMatrix.concat(localToParentMatrix)
        } else {
            localToSceneMatrix.set(localToParentMatrix)
        }

        sceneToLocalMatrix.set(localToSceneMatrix)
        sceneToLocalMatrix.invertOrNaN()

        attachedBackground.calculateTransformations()
    }

    /**
     * Converts a pair of coordinates to scene space.
     */
    fun toSceneSpace(x: Float, y: Float): Vec2 {
        return localToSceneMatrix.transform(x, y)
    }

    /**
     * Converts a pair of coordinates to parent's local space.
     */
    fun toParentSpace(x: Float, y: Float): Vec2 {
        return localToParentMatrix.transform(x, y)
    }

    /**
     * Converts a pair of coordinates to local space from a parent space.
     */
    fun toLocalSpaceFromParent(x: Float, y: Float): Vec2 {
        return parentToLocalMatrix.transform(x, y)
    }

    /**
     * Converts a pair of coordinates to local space from a scene space.
     */
    fun toLocalSpaceFromScene(x: Float, y: Float): Vec2 {
        return sceneToLocalMatrix.transform(x, y)
    }

    //endregion

    //region Cosmetic

    private val colorProperty = themeable(Color4.White)

    /**
     * The color of the component boxed in a [Color4] object.
     */
    var color: Color4 by colorProperty

    /**
     * When true, this component's RGB is multiplied into its descendants' colours (the alpha always is).
     * The component's own content, background and border are never tinted by its own RGB. Only
     * containers have descendants, so it has no effect on other components. Default true.
     */
    var tintChildren: Boolean = true

    /**
     * The [color] without its alpha, used to draw the component's own content. The alpha is
     * already applied to everything the component draws through the renderer's color stack.
     */
    protected val contentColor
        get() = color.alpha(1f)

    /**
     * The alpha component of the compoent's color.
     */
    var alpha
        get() = color.alpha
        set(value) { color = colorProperty.raw.alpha(value) }

    /**
     * Determines how the component should be clipped.
     */
    var clipping: ClipMode = ClipMode.Disabled

    /**
     * What this component is filled with behind its content, a color, a texture, a component
     * (only for containers) or a blur of what is behind, following its shape and under its border.
     */
    var background: Background by themeable(Background.None) { attachBackground(it) }

    // The background as resolved, attached to this component as long as it's the background.
    private var attachedBackground: Background = Background.None

    private fun attachBackground(background: Background) {
        val old = attachedBackground
        if (old === background) return

        // Attached first, it checks it can be the background of this component.
        background.attach(this)
        old.detach(this)
        attachedBackground = background
        invalidateMeasure()
    }

    /**
     * The transformation matrix from local space to scene space, see [localToSceneMatrix].
     */
    internal val localToScene: Mat2
        get() = localToSceneMatrix

    /**
     * The border drawn along the inner edge of this component. Null for none.
     */
    var border: Border by themeable(Border.None)

    /**
     * The shadow drawn below this component, following its shape.
     */
    var shadow: Shadow by themeable(Shadow.None)

    /**
     * The radius of each corner of this component, which rounds its background, border and shadow.
     */
    var radius: CornerRadius by themeable(CornerRadius.Zero)


    /**
     * Creates a property that can be assigned [themed] values, which are resolved with the theme of
     * this component and again whenever it changes. [onChanged] is called when the resolved value
     * changes, except for the initial one.
     */
    protected fun <T> themeable(initial: T, onChanged: (T) -> Unit = {}): ThemeableProperty<T> {
        return ThemeableProperty(this, initial, onChanged)
    }

    //region Localization

    private var localeBindings: LinkedHashMap<Any, () -> Unit>? = null

    // The Localization.version the locale bindings were last applied with.
    private var boundLocaleVersion = -1

    /**
     * Calls [apply] now, and again when the strings of [Localization] change (e.g. the locale), for
     * values that depend on them, such as a [com.reco1l.verktex.localization.LocalizedString]. It's
     * applied on the next update after the change, so components out of the tree catch up once
     * they're back. Binding with the same [key] again replaces the previous binding.
     */
    fun bindLocale(key: Any, apply: () -> Unit) {
        val bindings = localeBindings ?: LinkedHashMap<Any, () -> Unit>().also { localeBindings = it }
        bindings[key] = apply
        apply()
    }

    /**
     * Removes the binding added with [bindLocale] with the given [key], keeping its last value.
     */
    fun unbindLocale(key: Any) {
        localeBindings?.remove(key)
    }

    private fun applyLocaleBindings() {
        val bindings = localeBindings ?: return
        val version = Localization.version
        if (boundLocaleVersion == version) return

        boundLocaleVersion = version
        bindings.values.toList().forEach { it() }
    }

    //endregion

    /**
     * Resolves the [themed] values of this component and its children again, for when they depend
     * on a state that changed, e.g. the variant of a button.
     */
    fun restyle() {
        val apply = { component: Component ->
            component.theme.applyBindings()
            component.attachedBackground.restyle()
            Unit
        }
        if (this is Container) propagate(apply) else apply(this)
    }

    /**
     * Resolves the [themed] values of this component and its children again, after their theme
     * changed.
     */
    internal fun applyTheme() {
        val apply = { component: Component ->
            component.theme.applyBindings()
            component.onThemeChanged()
            component.attachedBackground.applyTheme()
            Unit
        }
        if (this is Container) propagate(apply) else apply(this)
    }

    /**
     * Called when the theme or the font scale changed, after the [themed] values were resolved
     * again. Cached values depending on them must be invalidated here.
     */
    open fun onThemeChanged() {
        invalidateMeasure()
    }

    //endregion

    //region Invalidation

    /**
     * Whether the component's layout is invalid and needs to be processed next frame.
     */
    // Laying out is cheap, so it runs every frame and this flag is never reset. Scrolling and
    // animated positions rely on it. Only a static layout resets it, see [isLayoutStatic].
    var layoutInvalid = true
        private set

    /**
     * Whether this component keeps its layout until it is invalidated, instead of laying out every
     * frame. See [Container.staticLayout].
     */
    protected open val isLayoutStatic
        get() = false

    /**
     * Whether the component's measurement is invalid and needs to be processed next frame.
     */
    var measureInvalid = true
        private set

    /**
     * Whether the component's transformations are invalid and needs to be processed next frame.
     */
    var transformationsInvalid = true
        private set


    /**
     * Marks this component's layout as invalid.
     */
    fun invalidateLayout() {
        layoutInvalid = true
        // A static ancestor skips its pass while valid, so the change has to reach it to lay this
        // component out again. Every other component is always invalid, there it changes nothing.
        parent?.invalidateLayout()
    }

    /**
     * Marks this component's measure as invalid.
     */
    fun invalidateMeasure() {
        measureInvalid = true
        // The parent's size can depend on this component's one.
        parent?.invalidateMeasure()
    }

    /**
     * Marks this component's transformations as invalid.
     */
    fun invalidateTransformations() {
        transformationsInvalid = true
    }

    /**
     * Brings the transformations of this component up to date along with its ancestors', for drawing it outside
     * its parent's pass, see [Proxy]. Recalculating the topmost invalid one recalculates its descendants too.
     */
    internal fun validateTransformationChain() {
        var topmostInvalid: Component? = null
        var current: Component? = this

        while (current != null) {
            if (current.transformationsInvalid) {
                topmostInvalid = current
            }
            current = current.parent
        }

        if (topmostInvalid != null) {
            topmostInvalid.transformationsInvalid = false
            topmostInvalid.calculateTransformations()
        }
    }

    //endregion

    //region Attachment

    /**
     * The parent container of this component. This is usually set when the component is added to a container.
     *
     * Usually the root component of the UI tree will have a null parent.
     */
    var parent: Container? = null
        set(value) {
            if (field == value) return
            field?.onChildDetached(this)
            field = value

            if (value == null) attachedBackground.onOwnerRemoved()
            field?.onChildAttached(this)

            // Themed values are resolved on construction, before the component has a parent. If
            // it's attached under a different theme they must be resolved again with it.
            if (value != null && theme.isStale) {
                applyTheme()
            }
        }


    /**
     * Called when the component is attached to a parent.
     * This is usually called when the component is added to a container.
     */
    open fun onAttached() {}

    /**
     * Called when the component is detached from a parent.
     * This is usually called when the component is removed from a container.
     */
    open fun onDetached() {}

    /**
     * Removes this component from its parent container. If the component has no parent, this method
     * does nothing.
     */
    fun removeSelf() {
        parent?.minusAssign(this)
    }

    //endregion

    //region Drawing

    /**
     * Whether the component is visible. If false, the component will not be drawn.
     *
     * Being invisible does not mean that the component is removed from the layout, it will still
     * occupy space and be updated, as well it will still intercept input events.
     */
    var isVisible = true
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
        }

    /**
     * The proxy drawing this component instead of its parent, see [Proxy]: its parent skips it while it's set.
     */
    internal var proxy: Proxy? = null

    // Whether the proxy is drawing this component, the only time it draws while it has one.
    internal var isDrawingThroughProxy = false

    /**
     * Whether the bounds of this component, transformed to scene space, overlap the given scene
     * rectangle. Anything drawn outside of the bounds is not considered.
     */
    open fun intersectsSceneRect(x: Float, y: Float, width: Float, height: Float): Boolean {
        if (transformationsInvalid) {
            transformationsInvalid = false
            calculateTransformations()
        }

        // The bounds of the transformed corners, computed from the matrix without transforming
        // each corner: every term of an affine transform reaches its extremes on its own.
        val m = localToSceneMatrix
        val ax = m.a * bounds.width
        val cy = m.c * bounds.height
        val bx = m.b * bounds.width
        val dy = m.d * bounds.height

        val minX = m.tx + min(0f, ax) + min(0f, cy)
        val maxX = m.tx + max(0f, ax) + max(0f, cy)
        val minY = m.ty + min(0f, bx) + min(0f, dy)
        val maxY = m.ty + max(0f, bx) + max(0f, dy)

        return minX <= x + width && maxX >= x && minY <= y + height && maxY >= y
    }

    /**
     * Draws the component using the given renderer. This method is responsible for setting up the
     * rendering context, applying transformations, and calling [onDraw] to perform the actual
     * drawing of the component.
     */
    /**
     * Called as [draw] starts, before anything of this component is drawn, its background included, e.g. for a
     * [Scene] to lay itself out.
     */
    protected open fun onPrepareDraw() {}

    fun draw(renderer: Renderer) {

        if (!isVisible) {
            return
        }

        onPrepareDraw()

        if (transformationsInvalid) {
            transformationsInvalid = false
            calculateTransformations()
        }

        // A proxied component is drawn by its proxy, at another point of the draw order.
        if (proxy != null && !isDrawingThroughProxy) {
            return
        }

        // The alpha is multiplied into everything drawn below, so a transparent component and its
        // children change no pixel but would still be filled and blended, e.g. a faded out
        // fullscreen sprite. The transformations above are kept current for hit testing.
        if (renderer.currentAlpha * color.alpha <= 0f && !drawsWhenTransparent) {
            return
        }

        renderer.pushMatrix(localToSceneMatrix)
        renderer.pushColor(color)

        val radius = radius
        val shadow = shadow
        if (shadow.isVisible && shadow.color.alpha > 0f) {
            val blur = shadow.blur.toPixels()

            // Grown by the blur, so the soft edge fits around the component's shape.
            renderer.drawRect(
                x = -blur,
                y = shadow.offsetY.toPixels() - blur,
                width = bounds.width + blur * 2f,
                height = bounds.height + blur * 2f,
                color = shadow.color,
                // A negative border width tells the shader to cut the shape out.
                borderWidth = if (shadow.cutout) -1f else 0f,
                softness = blur,
                inset = blur,
                radiusTopLeft = radius.topLeft.toPixels(),
                radiusTopRight = radius.topRight.toPixels(),
                radiusBottomRight = radius.bottomRight.toPixels(),
                radiusBottomLeft = radius.bottomLeft.toPixels()
            )
        }

        // The background is drawn before clipping, padding clipping must only affect the content.
        val background = background
        val border = border

        // A color is drawn with the border, a texture, a component or a blur under it.
        val fill = background.fill
        background.draw(renderer, this, radius)

        if (fill.alpha > 0f || border.isVisible) {
            renderer.drawRect(
                x = 0f,
                y = 0f,
                width = bounds.width,
                height = bounds.height,
                color = fill,
                borderWidth = border.width.toPixels(),
                radiusTopLeft = radius.topLeft.toPixels(),
                radiusTopRight = radius.topRight.toPixels(),
                radiusBottomRight = radius.bottomRight.toPixels(),
                radiusBottomLeft = radius.bottomLeft.toPixels(),
                borderTop = border.top,
                borderRight = border.right,
                borderBottom = border.bottom,
                borderLeft = border.left
            )
        }

        drawOverBackground(renderer)

        // A scissor is enough for a rectangle aligned to the screen, and costs nothing in the shaders.
        // Rounded corners, rotations and skews need the shaders' clip.
        var isShaderClipped = false

        if (clipping != ClipMode.Disabled) {
            val area = clippingArea()
            val radii = clippingRadii()

            val x = area.x
            val y = area.y
            val width = (area.z - area.x).coerceAtLeast(0f)
            val height = (area.w - area.y).coerceAtLeast(0f)

            val isRounded = radii.x > 0f || radii.y > 0f || radii.z > 0f || radii.w > 0f
            val isAxisAligned = localToSceneMatrix.b == 0f && localToSceneMatrix.c == 0f

            if (isRounded || !isAxisAligned) {
                renderer.pushClip(x, y, width, height, radii)
                isShaderClipped = true
            } else {
                renderer.pushScissor(x, y, width, height)
            }
        }

        drawContent(renderer)

        // Debug outline
        if (showComponentBoundaries || isOutliningBoundaries) {
            renderer.drawRect(0f, 0f, bounds.width, bounds.height, Color4.Transparent, 1f, Color4.White, 0f)
        }

        if (isShaderClipped) {
            renderer.popClip()
        } else if (clipping != ClipMode.Disabled) {
            renderer.popScissor()
        }

        renderer.popColor()
        renderer.popMatrix()
    }

    /**
     * Draws over the background and the border, under the content and outside the clipping, in
     * local space, e.g. a [StateHighlight].
     */
    protected open fun drawOverBackground(renderer: Renderer) {}

    /**
     * The rectangle the content is clipped to while [clipping] is enabled, in local space as its left, top,
     * right and bottom edges: the bounds, or the area inside the padding.
     */
    protected open fun clippingArea(): Vec4 {
        if (clipping == ClipMode.Padding) {
            val padding = layoutPadding
            return Vec4(
                x = padding.x,
                y = padding.y,
                z = bounds.width - padding.z,
                w = bounds.height - padding.w
            )
        }

        return Vec4(0f, 0f, bounds.width, bounds.height)
    }

    /**
     * The radius of the top left, top right, bottom right and bottom left corners of the area the
     * content is clipped to ([clippingArea]), in local space: the component's [radius], reduced by the
     * padding with [ClipMode.Padding] as the inner corners of a CSS border are.
     */
    protected open fun clippingRadii(): Vec4 {
        val radius = radius

        if (clipping != ClipMode.Padding) {
            return Vec4(
                radius.topLeft.toPixels(),
                radius.topRight.toPixels(),
                radius.bottomRight.toPixels(),
                radius.bottomLeft.toPixels()
            )
        }

        val padding = layoutPadding
        val left = padding.x
        val top = padding.y
        val right = padding.z
        val bottom = padding.w

        return Vec4(
            (radius.topLeft.toPixels() - max(left, top)).coerceAtLeast(0f),
            (radius.topRight.toPixels() - max(right, top)).coerceAtLeast(0f),
            (radius.bottomRight.toPixels() - max(right, bottom)).coerceAtLeast(0f),
            (radius.bottomLeft.toPixels() - max(left, bottom)).coerceAtLeast(0f)
        )
    }

    /**
     * Whether this component must be drawn even when fully transparent, because it draws with a
     * blend mode that ignores the alpha (e.g. [com.reco1l.verktex.graphics.BlendMode.Opaque]).
     */
    protected open val drawsWhenTransparent: Boolean
        get() = false

    /**
     * Draws the component and its children by calling [onDraw]. Subclasses can wrap it to set up
     * render state around the drawing, see [Canvas].
     */
    protected open fun drawContent(renderer: Renderer) {
        onDraw(renderer)
    }

    /**
     * Called when the component needs to be drawn. This is where the actual rendering of the
     * component should happen.
     *
     * @param renderer The renderer to use for drawing.
     */
    protected abstract fun onDraw(renderer: Renderer)

    //endregion

    //region Update

    /**
     * Updates the component. This method is called every update cycle and is responsible for
     * updating the component's state.
     */
    fun update(clock: FrameClock) {
        if (ignoreUpdate) {
            return
        }

        if (PerformanceStats.slowComponentThreshold > 0f) {
            PerformanceStats.profileUpdate(this) { updateSelf(clock) }
        } else {
            updateSelf(clock)
        }
    }

    private fun updateSelf(clock: FrameClock) {
        timing.engineTime = clock.time
        applyLocaleBindings()
        (this as? Interactive)?.interaction?.update()
        transforms?.update(this, clock.elapsedFrameTime)
        choreography?.update(timing.time)
        attachedBackground.update(clock)
        onUpdate(clock)
    }


    /**
     * The clock pacing the transforms and the choreography of this component and its children, see [Timing].
     */
    val timing = Timing(this)

    /**
     * The timeline of this component, see [Choreography].
     */
    var choreography: Choreography? = null

    /**
     * Whether a transform or a step of the choreography is still running.
     */
    val isAnimating: Boolean
        get() = transforms?.hasTransforms == true || choreography?.let { timing.time < it.endTime } == true

    /**
     * Whether this component and its children stop updating, which freezes them in place.
     */
    var ignoreUpdate = false

    /**
     * Removes all the transforms and the choreography, leaving the properties at their current values.
     */
    fun clearAnimations() {
        transforms?.clear()
        choreography?.clear()
    }

    /**
     * Sets the position to the given amount of pixels.
     */
    fun setPosition(x: Float, y: Float) {
        this.x = x.px
        this.y = y.px
    }

    /**
     * Sets the size to the given amount of pixels.
     */
    fun setSize(width: Float, height: Float) {
        this.width = width.px
        this.height = height.px
    }

    /**
     * Sets the color channels, leaving the alpha as it is.
     */
    fun setColor(red: Float, green: Float, blue: Float) {
        color = Color4(red, green, blue, alpha)
    }

    /**
     * The transforms running on this component, created when the first one is added.
     */
    internal var transforms: TransformTracker? = null
        private set

    internal fun transformTracker() = transforms ?: TransformTracker().also { transforms = it }

    /**
     * Called when the component needs to be updated. This is where the actual update logic of the
     * component should happen.
     */
    protected open fun onUpdate(clock: FrameClock) {}

    //endregion

    //region Input

    open fun onInputEvent(event: InputEvent): Boolean {
        return claimHover(event)
    }

    /**
     * Claims a [HoverEvent] for this component if it's [Interactive] and [Interactive.isHoverable].
     * Components handling input themselves call it once their children didn't claim it.
     */
    protected fun claimHover(event: InputEvent): Boolean {
        if (event is HoverEvent && this is Interactive && isHoverable) {
            event.target = this
            return true
        }
        return false
    }

    /**
     * Whether the given point in scene (window) space is inside this component's bounds.
     */
    fun containsScenePoint(x: Float, y: Float): Boolean {
        val local = toLocalSpaceFromScene(x, y)
        return local.x >= 0f && local.y >= 0f && local.x <= bounds.width && local.y <= bounds.height
    }

    //endregion

    //region Layout

    /**
     * How the [padding] measures and places this component, see [PaddingLayout]. Kept while the padding
     * changes its sides alone.
     */
    internal var paddingLayout: PaddingLayout = PaddingLayout.Plain
        private set

    private val paddingProperty = themeable(Padding.Zero) {
        if (it.layoutKind != paddingLayout.kind) paddingLayout = it.createLayout()
        invalidateMeasure()
    }

    /**
     * The padding of the component, which can also make it cover its parent's padding
     * ([Padding.coverParentPadding]) or grow into the safe area ([Padding.extendIntoSafeArea]).
     *
     * Layouts read the space they keep around the content from [measurePadding] and
     * [layoutPadding], which include what these add.
     */
    var padding: Padding by paddingProperty

    /**
     * The padding the content kept in the last measure pass, in pixels as left, top, right and
     * bottom: the [padding]'s sides, plus what its behaviour adds, see [PaddingLayout.measurePadding].
     */
    var measurePadding = Vec4.Zero
        private set

    /**
     * The padding the content kept in the last layout pass, in pixels as left, top, right and
     * bottom: the [padding]'s sides, plus what its behaviour adds, e.g. the safe area on the sides
     * placed against it ([Padding.extendIntoSafeArea]).
     */
    var layoutPadding = Vec4.Zero
        private set

    /**
     * The bounds of the component after the last layout pass. This is the area that the component
     * will occupy in its parent's coordinate system.
     *
     * This is used for layout and rendering, and is updated during the [layout] method.
     */
    var bounds = Vec4.Zero
        protected set

    /**
     * The measured size of the component after the last measure pass.
     * This is the size that the component will use for layout and rendering.
     */
    var measuredSize = Vec2.Zero
        protected set

    private var lastMeasureConstraints: LayoutConstraints? = null

    private var lastLayoutBounds: Vec4? = null

    /**
     * Measures the component with the given layout constraints. This method will call [onMeasure]
     * to perform the actual measurement.
     */
    fun measure(requestedConstraints: LayoutConstraints): Vec2 {
        val layout = paddingLayout
        val parentContraints = adjustParentConstraints(layout.measureConstraints(this, requestedConstraints))
        val newMeasurePadding = layout.measurePadding(padding.toVec4())

        if (!measureInvalid && parentContraints == lastMeasureConstraints && newMeasurePadding == measurePadding) {
            return measuredSize
        }

        measurePadding = newMeasurePadding

        // Reset before measuring, invalidations made meanwhile must trigger another pass.
        measureInvalid = false
        lastMeasureConstraints = parentContraints

        // A measure pass means a size, position or child changed, which a static layout must apply.
        if (isLayoutStatic) {
            layoutInvalid = true
        }

        val ownConstraints = parentContraints.resolve(width, height).limit(
            minWidth,
            maxWidth,
            minHeight,
            maxHeight,
            parentContraints.availableWidth,
            parentContraints.availableHeight
        )
        val contentConstraints = ownConstraints.shrink(measurePadding)

        val oldMeasuredSize = measuredSize

        val contentSize = onMeasure(parentContraints, contentConstraints)
        val widthFactor = (width as? Dimension.ContentSizeFraction)?.factor ?: 1f
        val heightFactor = (height as? Dimension.ContentSizeFraction)?.factor ?: 1f

        measuredSize = ownConstraints.constrain(Vec2(contentSize.x * widthFactor, contentSize.y * heightFactor))

        if (oldMeasuredSize != measuredSize) {
            invalidateLayout()
            onSizeChanged()
        }

        return measuredSize
    }

    /**
     * Adjusts the constraints the parent measures this component with, after growing them by the
     * parent's padding when the [padding] covers it ([Padding.coverParentPadding]).
     */
    protected open fun adjustParentConstraints(constraints: LayoutConstraints) = constraints

    /**
     * Adjusts the bounds this component got from its measured size and the parent, before laying
     * out its content, after growing them over the parent's padding or into the safe area as the
     * [padding] says. They can grow past the measured size without moving the siblings.
     */
    protected open fun adjustBounds(bounds: Vec4) = bounds

    /**
     * Layouts the component within the given bounds. This method will call [onLayout] to perform
     * the actual layout of the component. The bounds are expected to be in the parent's coordinate system.
     */
    open fun layout(assignedBounds: Vec4) {
        if (isLayoutStatic) {
            if (!layoutInvalid && assignedBounds == lastLayoutBounds) {
                return
            }

            // Reset before laying out, invalidations made meanwhile must trigger another pass.
            layoutInvalid = false
            lastLayoutBounds = assignedBounds
        } else if (!layoutInvalid) {
            return
        }

        val oldBounds = bounds
        val placement = paddingLayout.place(this, assignedBounds, placeInArea)

        layoutPadding = padding.toVec4() + placement.inset
        bounds = adjustBounds(placement.bounds)

        onLayout(bounds)
        attachedBackground.layout(this)

        if (oldBounds != bounds) {
            calculateTransformations()

            if (oldBounds.x != bounds.x || oldBounds.y != bounds.y) {
                onPositionChanged()
            }
        }
    }

    // Kept to not create a reference every layout pass.
    private val placeInArea: (Vec4) -> Vec4 = ::placeIn

    // Places the measured size of this component in the given area, as its anchor, origin and position say.
    private fun placeIn(area: Vec4): Vec4 {
        val left = area.x + area.width * anchor.x + x.resolve(area.width) - measuredSize.x * origin.x
        val top = area.y + area.height * anchor.y + y.resolve(area.height) - measuredSize.y * origin.y

        return Vec4(x = left, y = top, z = left + measuredSize.x, w = top + measuredSize.y)
    }

    /**
     * Called when the component needs to be measured. This is where the actual measurement logic of
     * the component should happen.
     *
     * The method receives the parent constraints and the content constraints, which are the
     * constraints after applying the padding.
     */
    protected open fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints): Vec2 {
        return contentConstraints.constrain(Vec2.Zero)
    }

    /**
     * Called when the component needs to be laid out. This is where the actual layout logic of
     * the component should happen.
     *
     * The method receives the bounds of the component in its parent's coordinate system.
     */
    protected open fun onLayout(bounds: Vec4) {}


    //endregion


    companion object {
        private val showComponentBoundaries by VerktexSettings.ShowComponentBoundaries

        /**
         * Whether the components drawn meanwhile are outlined as with the setting, e.g. the content
         * of the UI test browser's stage.
         */
        internal var isOutliningBoundaries = false
    }
}


