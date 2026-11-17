package com.reco1l.verktex.vulkan

import com.reco1l.verktex.graphics.GraphicsDevice
import com.reco1l.verktex.graphics.GraphicsShader
import com.reco1l.verktex.graphics.GraphicsTexture

/**
 * The Vulkan implementation of [GraphicsDevice]. External (video) textures are a platform's: without
 * them, as on desktop where decoders upload RGBA frames, they're ordinary textures.
 */
open class VKDevice(
    protected val context: VKContext,
    supportsExternalTextures: Boolean = false,
) : GraphicsDevice {

    override val capabilities = GraphicsDevice.Capabilities(
        maxTextureSize = VKNative.maxImageDimension2D(context.handle).toFloat(),
        supportsExternalTextures = supportsExternalTextures,
    )

    override fun createBuffer() = VKBuffer()

    override fun createShader(source: GraphicsShader.Source): GraphicsShader = VKShader(source, context)

    override fun createTexture(width: Float, height: Float): GraphicsTexture = VKTexture(width, height, context)

    override fun createExternalTexture(width: Float, height: Float): GraphicsTexture = VKTexture(width, height, context)
}
