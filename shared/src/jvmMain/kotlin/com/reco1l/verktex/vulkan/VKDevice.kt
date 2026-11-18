package com.reco1l.verktex.vulkan

import com.reco1l.verktex.graphics.Device
import com.reco1l.verktex.graphics.Shader
import com.reco1l.verktex.graphics.Texture

/**
 * The Vulkan implementation of [Device]. External (video) textures are a platform's: without
 * them, as on desktop where decoders upload RGBA frames, they're ordinary textures.
 */
open class VKDevice(
    protected val context: VKContext,
    supportsExternalTextures: Boolean = false,
) : Device {

    override val capabilities = Device.Capabilities(
        maxTextureSize = VKNative.maxImageDimension2D(context.handle).toFloat(),
        supportsExternalTextures = supportsExternalTextures,
    )

    override fun createBuffer() = VKBuffer()

    override fun createShader(source: Shader.Source): Shader = VKShader(source, context)

    override fun createTexture(width: Float, height: Float): Texture = VKTexture(width, height, context)

    override fun createExternalTexture(width: Float, height: Float): Texture = VKTexture(width, height, context)
}
