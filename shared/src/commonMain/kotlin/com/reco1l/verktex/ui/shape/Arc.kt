package com.reco1l.verktex.ui.shape

import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.math.toRadians
import com.reco1l.verktex.ui.Canvas
import com.reco1l.verktex.ui.container.Container
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

inline fun Container.arc(builder: Arc.() -> Unit) = Arc().apply(builder).also { +it }

/**
 * A ring, or a part of one, [thickness] wide along the inside of the component's bounds, filled with its colour: a
 * progress ring, such as a spinner's. It's drawn from [startAngle] over [progress] of a turn, clockwise from the top
 * at 0°, as [Circle] is.
 */
open class Arc : Canvas() {

    /**
     * The width of the ring, from the edge of the bounds inwards.
     */
    var thickness = 1f

    /**
     * The angle the ring starts at, in degrees clockwise from the top.
     */
    var startAngle = 0f

    /**
     * How much of a turn is drawn from [startAngle]: 1 is the whole ring, 0 nothing, a negative value goes
     * counter-clockwise.
     */
    var progress = 1f
        set(value) {
            field = value.coerceIn(-1f, 1f)
        }

    /**
     * Whether the ends of a partial ring are round, as a stroke with round caps.
     */
    var roundedCaps = false

    /**
     * The angle the ring ends at, in degrees clockwise from the top.
     */
    val endAngle
        get() = startAngle + 360f * progress


    override fun onDraw(renderer: Renderer) {
        if (progress == 0f || thickness <= 0f) return

        val width = bounds.width
        val height = bounds.height

        renderer.drawArcStroke(0f, 0f, width, height, thickness, startAngle, endAngle, contentColor)

        if (roundedCaps && abs(progress) < 1f) {
            drawCap(renderer, startAngle, width, height)
            drawCap(renderer, endAngle, width, height)
        }
    }

    private fun drawCap(renderer: Renderer, angle: Float, width: Float, height: Float) {
        // In the middle of the ring's width, 0° at the top as the arc's.
        val radians = (angle - 90f).toRadians()
        val x = width / 2f + (width / 2f - thickness / 2f) * cos(radians)
        val y = height / 2f + (height / 2f - thickness / 2f) * sin(radians)

        renderer.drawRect(x - thickness / 2f, y - thickness / 2f, thickness, thickness, color = contentColor, radius = thickness / 2f)
    }
}
