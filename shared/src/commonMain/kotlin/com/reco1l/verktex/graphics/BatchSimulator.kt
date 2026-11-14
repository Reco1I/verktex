package com.reco1l.verktex.graphics

import com.reco1l.verktex.BuiltIn

/**
 * Counts the commands a frame would need if a draw could join an earlier command than the last
 * one, without changing what is drawn: a measurement of how much merging across the traversal
 * order would save.
 *
 * A draw joins the newest of the last [lookBack] commands it's compatible with, as long as none of
 * the commands after that one overlaps it, so that moving it before them doesn't change a pixel.
 * Depth clears and framebuffer captures stop the search, and draws whose bounds aren't known
 * (vertices the shader transforms) overlap everything.
 */
internal class BatchSimulator(private val lookBack: Int = 16) {

    // The simulated commands: a representative of their state, or null for a barrier, the texture
    // they sample and their bounds in scene space.
    private val states = ArrayList<DrawCommand?>()
    private val textures = ArrayList<GraphicsTexture?>()
    private var bounds = FloatArray(64)

    /**
     * The commands simulated so far in this frame.
     */
    val commandCount
        get() = states.size

    fun reset() {
        states.clear()
        textures.clear()
    }

    /**
     * A command nothing may be moved across, e.g. a depth clear.
     */
    fun barrier() {
        add(null, null, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    }

    /**
     * A draw recorded into [command]. [texture] is the texture the draw itself samples, which for
     * the UI shader can be null while its command has one (a box in a command with text).
     */
    fun draw(command: DrawCommand, texture: GraphicsTexture?, minX: Float, minY: Float, maxX: Float, maxY: Float) {
        val isMergeable = command.vertexCount < 0 && command.instanceBuffer == null && command.firstVertex == 0

        if (isMergeable) {
            val oldest = maxOf(0, states.size - lookBack)
            var index = states.size - 1

            while (index >= oldest) {
                val state = states[index] ?: break

                if (isCompatible(state, textures[index], command, texture)) {
                    if (texture != null) textures[index] = texture
                    union(index, minX, minY, maxX, maxY)
                    return
                }

                // The draw can't be moved before a command it overlaps.
                if (overlaps(index, minX, minY, maxX, maxY)) break
                index--
            }
        }

        add(command, texture ?: command.texture, minX, minY, maxX, maxY)
    }

    private fun isCompatible(state: DrawCommand, stateTexture: GraphicsTexture?, command: DrawCommand, texture: GraphicsTexture?): Boolean {
        // The UI shader draws boxes without sampling, so they join commands of any texture.
        if (command.vertexLayout === VertexLayout.UI && command.shader === BuiltIn.Shaders.UI) {
            return state.vertexLayout === VertexLayout.UI &&
                    state.shader === command.shader &&
                    state.isQuads == command.isQuads &&
                    state.uniformSet === command.uniformSet &&
                    state.scissor == command.scissor &&
                    state.blendMode == command.blendMode &&
                    state.depthState == command.depthState &&
                    (texture == null || stateTexture == null || stateTexture === texture)
        }

        return state.canReuse(
            command.shader,
            command.texture,
            command.scissor,
            command.blendMode,
            command.depthState,
            command.uniformSet,
            command.vertexLayout,
            command.isQuads
        )
    }

    private fun add(state: DrawCommand?, texture: GraphicsTexture?, minX: Float, minY: Float, maxX: Float, maxY: Float) {
        val index = states.size
        if (bounds.size < (index + 1) * 4) {
            bounds = bounds.copyOf(bounds.size * 2)
        }
        states.add(state)
        textures.add(texture)
        bounds[index * 4] = minX
        bounds[index * 4 + 1] = minY
        bounds[index * 4 + 2] = maxX
        bounds[index * 4 + 3] = maxY
    }

    private fun union(index: Int, minX: Float, minY: Float, maxX: Float, maxY: Float) {
        val i = index * 4
        if (minX < bounds[i]) bounds[i] = minX
        if (minY < bounds[i + 1]) bounds[i + 1] = minY
        if (maxX > bounds[i + 2]) bounds[i + 2] = maxX
        if (maxY > bounds[i + 3]) bounds[i + 3] = maxY
    }

    private fun overlaps(index: Int, minX: Float, minY: Float, maxX: Float, maxY: Float): Boolean {
        val i = index * 4
        return minX < bounds[i + 2] && maxX > bounds[i] && minY < bounds[i + 3] && maxY > bounds[i + 1]
    }
}
