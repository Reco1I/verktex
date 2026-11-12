package com.reco1l.verktex.data

import com.reco1l.verktex.graphics.GraphicsTexture
import kotlin.math.max
import kotlin.math.min

/**
 * Defines how a texture is scaled to an area, e.g. the one of a sprite or of a background.
 */
enum class ScaleType {

    /**
     * Scale the texture to fill the entire area cropping the excess.
     */
    Crop,

    /**
     * Scale the texture to fit the area without cropping.
     */
    Fit,

    /**
     * Stretch the texture to fill the entire area, ignoring its aspect ratio.
     */
    Stretch;


    /**
     * Places [region] in an area of [width] by [height] pixels, aligned by [gravity] where it doesn't
     * fill it, and writes the rectangle it's drawn in (x, y, width, height) then the texture
     * coordinates it shows (u0, v0, u1, v1) to [out]. Returns false if the region has no size.
     */
    fun place(
        region: GraphicsTexture.Region,
        width: Float,
        height: Float,
        gravity: Vec2,
        flippedHorizontal: Boolean,
        flippedVertical: Boolean,
        out: FloatArray,
    ): Boolean {
        val textureWidth = region.width
        val textureHeight = region.height

        if (textureWidth <= 0f || textureHeight <= 0f) {
            return false
        }

        var x = 0f
        var y = 0f
        var drawnWidth = width
        var drawnHeight = height

        // Fraction of the texture shown on each axis, cropping shows only a part of it.
        var visibleX = 1f
        var visibleY = 1f

        when (this) {

            Crop -> {
                val scale = max(width / textureWidth, height / textureHeight)
                visibleX = width / (textureWidth * scale)
                visibleY = height / (textureHeight * scale)
            }

            Fit -> {
                val scale = min(width / textureWidth, height / textureHeight)
                drawnWidth = textureWidth * scale
                drawnHeight = textureHeight * scale
                x = (width - drawnWidth) * gravity.x
                y = (height - drawnHeight) * gravity.y
            }

            Stretch -> {}
        }

        val uStart = region.u0 + (region.u1 - region.u0) * (1f - visibleX) * gravity.x
        val uEnd = uStart + (region.u1 - region.u0) * visibleX
        val vStart = region.v0 + (region.v1 - region.v0) * (1f - visibleY) * gravity.y
        val vEnd = vStart + (region.v1 - region.v0) * visibleY

        out[0] = x
        out[1] = y
        out[2] = drawnWidth
        out[3] = drawnHeight
        out[4] = if (flippedHorizontal) uEnd else uStart
        out[5] = if (flippedVertical) vEnd else vStart
        out[6] = if (flippedHorizontal) uStart else uEnd
        out[7] = if (flippedVertical) vStart else vEnd
        return true
    }
}
