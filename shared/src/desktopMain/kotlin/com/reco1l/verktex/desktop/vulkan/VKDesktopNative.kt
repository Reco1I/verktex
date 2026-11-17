package com.reco1l.verktex.desktop.vulkan

import com.reco1l.verktex.vulkan.VKNative
import java.nio.ByteBuffer

/**
 * The desktop part of the Vulkan backend's JNI surface, implemented in `natives/vulkan/vk_jni_desktop.cpp`:
 * the context with the instance extensions GLFW needs, the surface GLFW creates, vsync and reading
 * frames back. The rest is [VKNative].
 */
internal object VKDesktopNative {

    init {
        VKNative.ensureLoaded()
    }

    /**
     * Creates the instance (with [instanceExtensions]), device and per-frame resources, 0 on failure
     * (see [VKNative.lastError]).
     */
    @JvmStatic external fun createContext(enableValidation: Boolean, instanceExtensions: Array<String>): Long

    /**
     * The context's `VkInstance`, which surfaces are created from.
     */
    @JvmStatic external fun instance(context: Long): Long

    /**
     * Replaces the surface rendered to (0 to release it), owned by the context from then on. Returns 1
     * when the render pass was recreated, 0 otherwise, or -1 on failure (see [VKNative.lastError]).
     */
    @JvmStatic external fun setSurface(context: Long, surface: Long): Int

    /**
     * Whether presenting waits for the display, from the next frame.
     */
    @JvmStatic external fun setVsync(context: Long, enabled: Boolean)

    /**
     * Copies the submitted frame, before it's presented, into [pixels] (a direct buffer of
     * `width * height * 4` bytes) as RGBA rows from the top. False when it can't.
     */
    @JvmStatic external fun readPixels(context: Long, pixels: ByteBuffer, width: Int, height: Int): Boolean
}
