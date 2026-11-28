package com.reco1l.verktex.ui

import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.math.Easing

/**
 * The default feedback of controls while hovered or pressed: a translucent layer of the content
 * color over the background of [component], in its shape. It fades in when the component is
 * hovered, more when pressed, and out otherwise (also when disabled). Its opacities and speed come
 * from [Theme.highlightStyle].
 *
 * The component tells it its state from [Interactive.onInteractionStateChanged] (with
 * [Interactive.tracksInteractionState]) and draws it from [Component.drawOverBackground]. The fades
 * are transforms of the component.
 */
class StateHighlight(private val component: Component) {

    /**
     * Whether the component highlights. When false the highlight fades out and stays hidden.
     */
    var isEnabled = true
        set(value) {
            if (field != value) {
                field = value
                fade()
            }
        }

    /**
     * The current opacity of the highlight.
     */
    var opacity = 0f
        private set

    private var state = InteractionState.Idle

    private val opacityProperty = animatableFloat({ opacity }, { opacity = it })


    /**
     * Fades to the opacity of [state], the new state of the component.
     */
    fun onStateChanged(state: InteractionState) {
        this.state = state
        fade()
    }

    private fun fade() {
        val style = component.theme.resolved.highlightStyle

        val target = if (!isEnabled) 0f else when (state) {
            InteractionState.Hovered -> style.hoveredAlpha
            InteractionState.Pressed -> style.pressedAlpha
            InteractionState.Idle, InteractionState.Disabled -> 0f
        }

        component.animate(opacityProperty, target, style.duration, Easing.OutQuint)
    }

    /**
     * Draws the highlight in [color], whose alpha is ignored.
     */
    fun draw(renderer: Renderer, color: Color4) {
        if (opacity <= 0f) {
            return
        }

        val radius = component.radius
        renderer.drawRect(
            x = 0f,
            y = 0f,
            width = component.bounds.width,
            height = component.bounds.height,
            color = color.alpha(opacity),
            radiusTopLeft = radius.topLeft.toPixels(),
            radiusTopRight = radius.topRight.toPixels(),
            radiusBottomRight = radius.bottomRight.toPixels(),
            radiusBottomLeft = radius.bottomLeft.toPixels(),
        )
    }
}
