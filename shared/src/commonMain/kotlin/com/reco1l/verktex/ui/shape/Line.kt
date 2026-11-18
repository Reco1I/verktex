package com.reco1l.verktex.ui.shape

import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.ui.Canvas
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.data.px

inline fun Container.line(builder: Line.() -> Unit) = Line().apply(builder).also { +it }

class Line : Canvas() {

    /**
     * The starting point of the line.
     */
    var fromPoint = Vec2.Zero

    /**
     * The ending point of the line.
     */
    var toPoint = Vec2.Zero

    /**
     * The width of the line.
     */
    var lineWidth = 1f.px


    override fun onDraw(renderer: Renderer) {
        renderer.drawLine(fromPoint.x, fromPoint.y, toPoint.x, toPoint.y, lineWidth.toPixels(), contentColor)
    }
}
