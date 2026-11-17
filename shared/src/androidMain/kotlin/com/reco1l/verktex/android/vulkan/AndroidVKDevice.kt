package com.reco1l.verktex.android.vulkan

import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.vulkan.VKContext
import com.reco1l.verktex.vulkan.VKDevice

/**
 * The Vulkan device on Android, whose external textures import the video frames of an `ImageReader`.
 */
class AndroidVKDevice(context: VKContext) : VKDevice(context, VKExternalTexture.isSupported(context)) {

    override fun createExternalTexture(width: Float, height: Float): GraphicsTexture = VKExternalTexture(width, height, context)
}
