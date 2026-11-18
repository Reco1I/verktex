package com.reco1l.verktex.desktop.gl

import com.reco1l.verktex.graphics.Device
import com.reco1l.verktex.graphics.Shader
import com.reco1l.verktex.graphics.Texture
import org.lwjgl.opengl.GL33.*

/**
 * The OpenGL implementation of [Device].
 */
class GLDevice : Device {

    // Written on the draw thread once the context is current, read from the update thread. Until
    // then the minimum every OpenGL 3.3 implementation supports.
    @Volatile
    override var capabilities = Device.Capabilities(maxTextureSize = 2048f, supportsExternalTextures = false)
        private set


    /**
     * Reads the capabilities from the context, which must be current on the calling thread.
     */
    fun refreshCapabilities() {
        val maxTextureSize = glGetInteger(GL_MAX_TEXTURE_SIZE).toFloat()
        if (maxTextureSize > 0f) {
            capabilities = capabilities.copy(maxTextureSize = maxTextureSize)
        }
    }


    override fun createBuffer() = GLBuffer()

    override fun createShader(source: Shader.Source) = GLShader(source)

    override fun createTexture(width: Float, height: Float): Texture = GLTexture(width, height)

    // Desktop has no external textures, its video decoder writes frames into a plain texture (see
    // FFmpegVideoDecoder). This one is never fed, which callers know from the capabilities.
    override fun createExternalTexture(width: Float, height: Float): Texture = GLTexture(width, height)
}
