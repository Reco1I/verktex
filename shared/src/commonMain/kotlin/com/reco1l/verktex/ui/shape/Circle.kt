package com.reco1l.verktex.ui.shape

import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.ui.Canvas
import com.reco1l.verktex.ui.container.Container

inline fun Container.circle(builder: Circle.() -> Unit) = Circle().apply(builder).also { +it }

open class Circle : Canvas() {

    /**
     * The angle where the circle starts to draw in degrees. By default, it is 0 degrees.
     */
    var startAngle: Float = 0f
        set(value) {
            field = value.coerceIn(-360f, 360f)
        }

    /**
     * The angle where the circle ends to draw in degrees. By default, it is 360 degrees.
     */
    var endAngle = 360f
        set(value) {
            field = value.coerceIn(-360f, 360f)
        }


    /**
     * Sets the portion of the circle to be drawn starting from the start angle.
     *
     * Positive values will draw the circle clockwise and negative values will draw it counter-clockwise.
     */
    fun setPortion(value: Float) {
        endAngle = startAngle + 360f * value.coerceIn(-1f, 1f)
    }


    override fun onDraw(renderer: Renderer) {
        renderer.drawArc(
            x = 0f,
            y = 0f,
            width = bounds.width,
            height = bounds.height,
            startAngle = startAngle,
            endAngle = endAngle,
            color = contentColor
        )
    }

}
