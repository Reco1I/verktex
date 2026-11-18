package com.reco1l.verktex.ui.shape

import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.ui.Canvas
import com.reco1l.verktex.ui.container.Container

inline fun Container.box(builder: Box.() -> Unit) = Box().apply(builder).also { +it }

/**
 * A rectangle filled with the component's [color], with its [radius] and [border].
 */
open class Box : Canvas() {
    override fun onDraw(renderer: Renderer) {
        renderer.drawRect(
            0f, 0f, bounds.width, bounds.height,
            color = contentColor,
            radiusTopLeft = radius.topLeft.toPixels(),
            radiusTopRight = radius.topRight.toPixels(),
            radiusBottomRight = radius.bottomRight.toPixels(),
            radiusBottomLeft = radius.bottomLeft.toPixels()
        )
    }
}
