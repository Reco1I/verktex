package com.reco1l.verktex.ui.shape

import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.ui.Canvas
import com.reco1l.verktex.ui.container.Container

inline fun Container.triangle(builder: Triangle.() -> Unit) = Triangle().apply(builder).also { +it }

open class Triangle : Canvas() {

    /**
     * The position of the triangle's tip along the base, where 0.0 is the left corner and 1.0
     * is the right corner.
     */
    var middle: Float = 0.5f
        set(value) { field = value.coerceIn(0f, 1f) }


    override fun onDraw(renderer: Renderer) {
        renderer.drawTriangle(
            x = 0f,
            y = 0f,
            width = bounds.width,
            height = bounds.height,
            middle = middle,
            color = contentColor
        )
    }
}


