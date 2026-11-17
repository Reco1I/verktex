package com.reco1l.verktex.vulkan

/**
 * An external texture whose frames are imported from a producer (Android's video frames), sampled
 * through a YCbCr conversion. Implemented by a platform's external texture.
 */
interface VKExternalFrames {

    /**
     * Whether frames can be fed, i.e. the device supports it and the texture is loaded.
     */
    val hasSurface: Boolean

    /**
     * The imported texture of the latest frame, 0 until there's one.
     */
    val handle: Long

    /**
     * The YCbCr sampler of the latest frame, which pipelines sampling it are made for.
     */
    val ycbcrSampler: Long

    /**
     * Imports the latest frame if a new one arrived, called by the renderer on the draw thread before
     * it draws the texture.
     */
    fun latch()
}
