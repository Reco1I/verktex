package com.reco1l.verktex.vulkan

import java.nio.ByteBuffer

/**
 * Loads the native library of the Vulkan backend, `verktex_vulkan`, the way the platform packages it.
 */
internal expect fun loadVulkanLibrary()

/**
 * The JNI surface of the Vulkan backend, implemented in `natives/vulkan` and shared by every platform.
 * Calls are coarse, the per-draw work is encoded into one command stream per frame, see
 * [VKCommandEncoder]. What only a platform has (creating the context, its window, Android's video
 * frames) is in that platform's own object, which loads the library through this one.
 *
 * Handles are opaque `Long`s, 0 being none. Every call takes the context handle.
 */
internal object VKNative {

    init {
        loadVulkanLibrary()
    }

    /**
     * Makes sure the library is loaded, for the platform objects to call before their own functions.
     */
    fun ensureLoaded() = Unit

    @JvmStatic external fun destroyContext(context: Long)

    /**
     * Why the last call that failed did.
     */
    @JvmStatic external fun lastError(): String

    @JvmStatic external fun deviceName(context: Long): String
    @JvmStatic external fun apiVersion(context: Long): Int
    @JvmStatic external fun maxImageDimension2D(context: Long): Int
    @JvmStatic external fun minUniformBufferOffsetAlignment(context: Long): Int

    /**
     * The size in bytes of each frame's ring, see [VKFrameRing].
     */
    @JvmStatic external fun ringSize(): Int

    /**
     * The size in bytes of the uniform block descriptors, which every block must fit in.
     */
    @JvmStatic external fun uniformRange(): Int

    /**
     * Waits for the next frame and acquires a swapchain image of the given size. Returns the frame's
     * index, which picks its ring, or -1 to skip the frame.
     */
    @JvmStatic external fun acquireFrame(context: Long, width: Int, height: Int): Int

    /**
     * The persistently mapped ring of a frame, see [VKFrameRing].
     */
    @JvmStatic external fun ringBuffer(context: Long, frameIndex: Int): ByteBuffer

    /**
     * Records the [count] ints of [commands] into the acquired frame and submits it.
     */
    @JvmStatic external fun submit(context: Long, commands: ByteBuffer, count: Int)

    /**
     * Presents the submitted frame, which may wait for the display.
     */
    @JvmStatic external fun present(context: Long)

    @JvmStatic external fun createShaderModule(context: Long, spirv: ByteArray): Long
    @JvmStatic external fun destroyShaderModule(context: Long, module: Long)

    /**
     * Creates a pipeline for the render passes. [vertexInput] is the binding count, the stride and input
     * rate of each, the attribute count and the location, binding, VkFormat and offset of each.
     * [blend] holds the VkBlendFactors of source and destination RGB, then of source and destination
     * alpha. [depth] holds whether the test and writes are enabled, then the VkCompareOp. [ycbcrSampler]
     * is the sampler of the video frames the pipeline samples (Android's `textureYcbcrSampler`), 0
     * otherwise.
     */
    @JvmStatic external fun createPipeline(context: Long, vertexModule: Long, fragmentModule: Long, vertexInput: IntArray, blend: IntArray, depth: IntArray, ycbcrSampler: Long): Long

    /**
     * Destroys the pipeline once the frames using it are done.
     */
    @JvmStatic external fun destroyPipeline(context: Long, pipeline: Long)

    /**
     * Creates an RGBA8 texture, cleared to transparent before the next frame draws. Any thread.
     */
    @JvmStatic external fun createTexture(context: Long, width: Int, height: Int): Long

    /**
     * Copies [size] bytes of [pixels] (a direct buffer) from [offset] into a rectangle of the texture,
     * before the next frame draws. [pixels] is only read during the call.
     */
    @JvmStatic external fun uploadTexture(context: Long, texture: Long, x: Int, y: Int, width: Int, height: Int, pixels: ByteBuffer, offset: Int, size: Int)

    /**
     * Destroys the texture once the frames using it are done. Any thread.
     */
    @JvmStatic external fun destroyTexture(context: Long, texture: Long)

    /**
     * The frames submitted so far, and how many of them the GPU completed.
     */
    @JvmStatic external fun submissionCount(context: Long): Long
    @JvmStatic external fun completedSubmissionCount(context: Long): Long

    /**
     * Milliseconds the GPU took for the last measured frame, -1 when unknown.
     */
    @JvmStatic external fun gpuTime(context: Long): Float
}
