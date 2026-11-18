package com.reco1l.verktex.vulkan

import com.reco1l.verktex.Logger
import com.reco1l.verktex.graphics.GPUContext
import com.reco1l.verktex.graphics.Window

/**
 * The Vulkan instance, device and swapchain, see `vk_context.cpp`. The device lives as long as the
 * context, a new window surface only recreates the swapchain, so resources survive it.
 *
 * Each platform creates the context (with the instance extensions its windows need) and gives it its
 * window's surface in [updateSurface].
 */
abstract class VKContext protected constructor(
    /**
     * The native context, see [VKNative].
     */
    internal val handle: Long,
) : GPUContext {

    /**
     * The window drawn to, whose size is the swapchain's.
     */
    abstract val window: Window

    override val version: String = VKNative.apiVersion(handle).let { version ->
        "Vulkan ${version ushr 22}.${(version ushr 12) and 0x3FF}.${version and 0xFFF} (${VKNative.deviceName(handle)})"
    }

    /**
     * The alignment of uniform block offsets in the ring.
     */
    val uniformAlignment = VKNative.minUniformBufferOffsetAlignment(handle).coerceAtLeast(4)

    /**
     * The bytes each uniform block slice takes in the ring, the size of its descriptor.
     */
    val uniformRange = VKNative.uniformRange()

    /**
     * The commands of the frame, submitted by [submit].
     */
    val encoder = VKCommandEncoder()

    /**
     * Called when the render pass was recreated, the pipelines made for the previous one are invalid.
     */
    var onRenderPassRecreated: (() -> Unit)? = null

    /**
     * The ring of the frame being recorded, valid from [acquireFrame] to [submit].
     */
    lateinit var ring: VKFrameRing
        private set

    private val rings = arrayOfNulls<VKFrameRing>(2)


    /**
     * Gives the native context the window's current surface when it changed, on the draw thread before
     * each frame. Returns false when there's nothing to draw to, the frame is then skipped.
     */
    protected abstract fun updateSurface(): Boolean

    /**
     * Handles the result of the native call that set a new surface: 1 when the render pass was
     * recreated, 0 when it wasn't, -1 on failure. Returns whether the surface can be drawn to.
     */
    protected fun onSurfaceSet(result: Int): Boolean {
        if (result < 0) {
            Logger.e("VKContext", "Couldn't use the window: ${VKNative.lastError()}")
            return false
        }
        if (result == 1) {
            onRenderPassRecreated?.invoke()
        }
        return true
    }

    override fun acquireFrame(): Boolean {
        if (!updateSurface()) {
            return false
        }

        val frameIndex = VKNative.acquireFrame(handle, window.windowWidth, window.windowHeight)
        if (frameIndex < 0) {
            return false
        }

        ring = rings[frameIndex] ?: VKFrameRing(VKNative.ringBuffer(handle, frameIndex)).also { rings[frameIndex] = it }
        ring.reset()
        encoder.reset()
        return true
    }

    /**
     * Records the frame's commands and submits them to the GPU.
     */
    fun submit() {
        VKNative.submit(handle, encoder.buffer, encoder.size)
    }

    override fun present() {
        VKNative.present(handle)
    }

    override fun destroy() {
        VKNative.destroyContext(handle)
    }


    protected companion object {

        /**
         * Checks a handle returned by a platform's `createContext`.
         *
         * @throws IllegalStateException if it's 0, so the caller can fall back to OpenGL.
         */
        fun checkCreated(handle: Long): Long {
            if (handle == 0L) throw IllegalStateException("Couldn't initialize Vulkan: ${VKNative.lastError()}")
            return handle
        }
    }
}
