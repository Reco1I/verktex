package com.reco1l.verktex.graphics

import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4

/**
 * The uniform values every draw starts from, those it doesn't set itself take these. They only
 * depend on the viewport size, so the set is rebuilt only when it changes.
 *
 * Used on the draw thread only.
 */
class DefaultUniforms {

    private var width = -1
    private var height = -1
    private var uniforms = Shader.UniformSet()

    /**
     * The defaults for a viewport of [viewportWidth] x [viewportHeight] pixels. The returned set must
     * not be modified.
     */
    fun forViewport(viewportWidth: Int, viewportHeight: Int): Shader.UniformSet {
        if (viewportWidth != width || viewportHeight != height) {
            width = viewportWidth
            height = viewportHeight

            val viewportSize = Vec2(viewportWidth.toFloat(), viewportHeight.toFloat())

            uniforms = Shader.UniformSet().apply {
                set("uViewportSize", viewportSize)
                set("uColor", Color4.White)
                set("uTexture", 0)
                set("uRadius", 0f)
                set("uBorderWidth", 0f)
                set("uBorderColor", Color4.Transparent)
                set("uSize", viewportSize)
                set("uRadii", ZERO_VEC4)
                set("uMask", ZERO_VEC4)
                set("uQuad", ZERO_VEC4)
                set("uUVRect", ZERO_VEC4)
                set("uTransform", IDENTITY_MAT4)

                // No clip, a draw under one takes its uniforms from DrawCommand.clip.
                set(ShaderClip.TRANSFORM, ShaderClip.NONE.transform)
                set(ShaderClip.TRANSLATION, ShaderClip.NONE.translation)
                set(ShaderClip.RECT, ShaderClip.NONE.rect)
                set(ShaderClip.RADII, ShaderClip.NONE.radii)
            }
        }

        return uniforms
    }

    companion object {
        private val ZERO_VEC4 = Vec4(0f, 0f, 0f, 0f)

        val IDENTITY_MAT4 = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
    }
}
