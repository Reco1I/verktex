package com.reco1l.verktex.ui.control

import com.reco1l.verktex.ui.StateHighlight
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.ui.InteractionState
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.ui.animatableFloat
import com.reco1l.verktex.ui.animate
import com.reco1l.verktex.ui.scaleTo
import com.reco1l.verktex.ui.transitionSlot
import com.reco1l.verktex.math.interpolateFloat
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.container.setHierarchyScrollPrevention
import com.reco1l.verktex.ui.shape.Box
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import com.reco1l.verktex.ui.themed

inline fun Container.seekBar(builder: SeekBar.() -> Unit): SeekBar {
    return SeekBar().apply(builder).also(::plusAssign)
}

/**
 * A horizontal slider to pick a value between [min] and [max] by dragging its thumb.
 */
open class SeekBar(initialValue: Float = 0f) : Control<Float>(initialValue) {

    /**
     * The minimum allowed value of the slider.
     */
    var min = 0f
        set(value) {
            if (field == value) return
            field = value
            this.value = onProcessValue(this.value)
        }

    /**
     * The maximum allowed value of the slider.
     */
    var max = 1f
        set(value) {
            if (field == value) return
            field = value
            this.value = onProcessValue(this.value)
        }

    /**
     * The step size of the slider. If set to 0, the slider allows any value between [min] and [max].
     */
    var step = 0f
        set(value) {
            require(value >= 0f) { "step must be greater than or equal to 0" }
            field = value
        }

    /**
     * The number of decimal places to round [value] to, or `null` to not round it.
     */
    var precision: Int? = null
        set(value) {
            require(value == null || value >= 0) { "precision must be greater than or equal to 0" }
            if (field == value) return
            field = value
            this.value = onProcessValue(this.value)
        }

    /**
     * Called when the user starts dragging the slider.
     */
    var onStartDragging: () -> Unit = {}

    /**
     * Called when the user stops dragging the slider.
     */
    var onStopDragging: () -> Unit = {}


    private val progressBar = Box().apply {
        height = fill()
        anchor = Anchor.CenterLeft
        origin = Anchor.CenterLeft
    }

    private val thumb = Box().apply {
        height = fill()
        anchor = Anchor.CenterLeft
        origin = Anchor.Center
    }


    private var thumbX = 0f

    private val thumbScale = thumb.transitionSlot()

    // How far the slider is stretched by dragging past its ends, from 0 to 1.
    private var stretch = 0f
        set(value) {
            field = value
            scaleX = 1f + value * 0.15f
            scaleY = 1f - value * 0.3f
        }

    private val stretchProperty = animatableFloat({ stretch }, { stretch = it })

    private val stretchTransition = transitionSlot()

    // The finger dragging the slider, others are ignored.
    private var pressPointerId = -1

    private val isPressed
        get() = pressPointerId != -1

    override val interactionState
        get() = if (isPressed) InteractionState.Pressed else super.interactionState

    /**
     * The layer highlighting the track while hovered or dragged.
     */
    val highlight = StateHighlight(this)

    override fun drawOverBackground(renderer: Renderer) {
        highlight.draw(renderer, theme.resolved.palette.accent)
    }

    override val tracksInteractionState
        get() = true

    override fun onInteractionStateChanged(state: InteractionState) {
        highlight.onStateChanged(state)
    }


    init {
        width = fill()

        height = themed { controlStyle.minimumHeight }
        radius = themed { CornerRadius(radius.lg) }
        background = Background.Color(themed { palette.controlInactiveBackground })

        progressBar.radius = themed { CornerRadius(radius.lg) }
        progressBar.color = themed { palette.controlActiveBackground }

        thumb.width = 16f.dip
        thumb.radius = themed { CornerRadius(radius.lg) }
        thumb.color = themed { palette.accent }

        +progressBar
        +thumb
    }


    override fun onProcessValue(value: Float): Float {
        var result = value

        if (step > 0f) {
            result = min + step * round((result - min) / step)
        }

        val precision = precision
        if (precision != null) {
            val factor = 10f.pow(precision)
            result = round(result * factor) / factor
        }

        // The bounds are set one at a time, so min can briefly be above the previous max.
        return if (max >= min) result.coerceIn(min, max) else min
    }


    //region Update

    override fun onUpdate(clock: FrameClock) {
        val width = bounds.width
        val thumbWidth = thumb.bounds.width
        val progress = if (max > min) (value - min) / (max - min) else 0f

        // The thumb stays inside the track, the bar ends at its center.
        val targetThumbX = (width * progress).coerceIn(thumbWidth / 2f, (width - thumbWidth / 2f).coerceAtLeast(thumbWidth / 2f))

        // Ranges crossing zero fill from zero, so negative and positive values grow in opposite ways.
        val zeroX = if (min < 0f && max > 0f) width * (-min / (max - min)) else 0f

        // The bar follows the animated thumb, so both always meet.
        thumbX = clock.interpolateFloat(0.15f, thumbX, targetThumbX)
        thumb.x = thumbX.px
        progressBar.x = minOf(zeroX, thumbX).px
        progressBar.width = abs(thumbX - zeroX).px

        super.onUpdate(clock)
    }

    //endregion

    //region Input

    override fun onInputEvent(event: InputEvent): Boolean {
        if (event !is PointerEvent) {
            return super.onInputEvent(event)
        }

        if (event.action != PointerEvent.Action.Down && (!isPressed || event.pointerId != pressPointerId)) {
            return false
        }

        when (event.action) {
            PointerEvent.Action.Down -> {
                if (isPressed) return true

                pressPointerId = event.pointerId
                // Dragging the slider must not scroll its parents.
                setHierarchyScrollPrevention(true)
                onStartDragging()
                thumbScale.run { scaleTo(1.25f, 0.1f, Easing.InExpo) }
                updateValueFromPointer(event.x, event.y)
            }

            PointerEvent.Action.Move -> updateValueFromPointer(event.x, event.y)

            PointerEvent.Action.Up, PointerEvent.Action.Unknown, PointerEvent.Action.Cancel -> {
                pressPointerId = -1
                setHierarchyScrollPrevention(false)
                onStopDragging()
                thumbScale.run { scaleTo(1f, 0.3f, Easing.OutBounce) }
                // Springs back from the stretch.
                stretchTransition.run { animate(stretchProperty, 0f, 0.4f, Easing.OutElastic) }
                return event.action == PointerEvent.Action.Up
            }
        }
        return true
    }

    private fun updateValueFromPointer(sceneX: Float, sceneY: Float) {
        val localX = toLocalSpaceFromScene(sceneX, sceneY).x
        val width = bounds.width

        // Dragging past an end stretches the slider towards the finger, anchored at the other end.
        val overflow = maxOf(-localX, localX - width, 0f)
        scaleOrigin = if (localX < 0f) Anchor.CenterRight else Anchor.CenterLeft
        stretchTransition.cancel()
        stretch = if (width > 0f) minOf(overflow / width, 1f) else 0f

        val progress = if (width > 0f) (localX / width).coerceIn(0f, 1f) else 0f
        value = min + progress * (max - min)
    }

    //endregion

}
