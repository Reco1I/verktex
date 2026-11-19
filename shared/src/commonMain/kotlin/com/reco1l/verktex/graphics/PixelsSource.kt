package com.reco1l.verktex.graphics

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A texture source of pixels made in code: [width] x [height] RGBA8 [pixels] with premultiplied alpha, rows tightly
 * packed from the top. Load it with `Textures.load(key, source)`, e.g. for a soft dot drawn by a particle system.
 */
class PixelsSource(width: Int, height: Int, private val pixels: ByteArray) : Texture.Source {

    init {
        require(pixels.size == width * height * 4) { "Expected ${width * height * 4} bytes for $width x $height pixels, got ${pixels.size}" }
    }

    override val width = width.toFloat()
    override val height = height.toFloat()

    private val pixelWidth = width
    private val pixelHeight = height

    override fun writePixels(target: Texture.PixelTarget, x: Int, y: Int) {
        target.write(x, y, pixelWidth, pixelHeight, pixelBufferOf(pixels))
    }

    companion object {

        /**
         * A [size] x [size] white dot fading from its centre to its edge: its alpha is `(1 - d)^falloff` at the
         * distance `d` from the centre, 1 at the edge.
         */
        fun softDot(size: Int, falloff: Float = 2f): PixelsSource {
            val pixels = ByteArray(size * size * 4)
            val center = (size - 1) / 2f
            val radius = size / 2f

            for (py in 0 until size) {
                for (px in 0 until size) {
                    val dx = (px - center) / radius
                    val dy = (py - center) / radius
                    val distance = sqrt(dx * dx + dy * dy)
                    val alpha = if (distance >= 1f) 0f else (1f - distance).pow(falloff)
                    val value = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()

                    // Premultiplied white: every channel is the alpha.
                    val index = (py * size + px) * 4
                    pixels[index] = value
                    pixels[index + 1] = value
                    pixels[index + 2] = value
                    pixels[index + 3] = value
                }
            }
            return PixelsSource(size, size, pixels)
        }
    }
}
