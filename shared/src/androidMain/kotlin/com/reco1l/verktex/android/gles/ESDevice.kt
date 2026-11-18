package com.reco1l.verktex.android.gles

import android.opengl.GLES20
import com.reco1l.verktex.graphics.Device
import com.reco1l.verktex.graphics.Shader
import com.reco1l.verktex.graphics.Texture

/**
 * The OpenGL ES implementation of [Device]. Textures and buffers are the same in every
 * version, shaders depend on the backend.
 */
abstract class ESDevice : Device {

    // Written on the draw thread once a context is current, read from the update thread.
    @Volatile
    final override var capabilities: Device.Capabilities = Device.Capabilities(maxTextureSize = 2048f)
        private set


    init {
        refreshCapabilities()
    }


    /**
     * Re-reads the capabilities from the current GL context. Does nothing when no context is current.
     */
    fun refreshCapabilities() {
        // No context is current yet when the device is created, in which case the query returns 0
        // and the fallback of 2048 is kept.
        val maxTextureSize = GLESHelper.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE).toFloat()
        if (maxTextureSize > 0f) {
            capabilities = Device.Capabilities(maxTextureSize = maxTextureSize)
        }
    }


    override fun createTexture(width: Float, height: Float): Texture {
        return ESTexture(width, height)
    }

    override fun createExternalTexture(width: Float, height: Float): ESExternalTexture {
        return ESExternalTexture(width, height)
    }

    override fun createBuffer(): ESBuffer {
        return ESBuffer()
    }

    abstract override fun createShader(source: Shader.Source): ESShader
}
