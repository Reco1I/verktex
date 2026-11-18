package com.reco1l.verktex.ui

import com.reco1l.verktex.graphics.BlendMode
import com.reco1l.verktex.graphics.DepthState
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.graphics.Shader

/**
 * A canvas is a component that exposes low-level rendering settings, such as the blend mode or the
 * depth state, and allows for usage of custom shaders and uniforms.
 */
abstract class Canvas : Component() {

    /**
     * The shader to use for rendering the canvas. If null, the default shader will be used. It must
     * use the built-in vertex layout, see [Renderer.pushShader]. To be clipped by the shape of a
     * rounded or transformed ancestor, it imports `lib/clip.glsl` and multiplies its output by
     * `clipCoverage()`, see `docs/shaders.md`.
     */
    var shader: Shader? = null

    /**
     * The blend mode to use for rendering the canvas.
     */
    var blendMode: BlendMode = BlendMode.Alpha

    /**
     * The state of the depth buffer for rendering the canvas.
     */
    var depthState: DepthState = DepthState.Default

    /**
     * Whether the depth buffer is cleared before the canvas draws, so its depth testing is not affected
     * by anything drawn earlier in the frame.
     */
    var clearDepthBeforeDraw = false

    // Opaque draws write their colour whatever the alpha.
    override val drawsWhenTransparent: Boolean
        get() = blendMode == BlendMode.Opaque


    override fun drawContent(renderer: Renderer) {
        val shader = shader
        if (shader != null) renderer.pushShader(shader)
        renderer.pushBlendMode(blendMode)
        renderer.pushDepthState(depthState)
        if (clearDepthBeforeDraw) renderer.clearDepth()
        onDraw(renderer)
        renderer.popDepthState()
        renderer.popBlendMode()
        if (shader != null) renderer.popShader()
    }
}