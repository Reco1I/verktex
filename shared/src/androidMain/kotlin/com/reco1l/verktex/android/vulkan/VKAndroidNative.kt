package com.reco1l.verktex.android.vulkan

import android.hardware.HardwareBuffer
import android.view.Surface
import com.reco1l.verktex.vulkan.VKNative

/**
 * The Android part of the Vulkan backend's JNI surface, implemented in `natives/vulkan/vk_jni_android.cpp`:
 * the context, its window and the video frames, imported from hardware buffers. The rest is [VKNative].
 */
internal object VKAndroidNative {

    init {
        VKNative.ensureLoaded()
    }

    /**
     * Creates the instance, device and per-frame resources, 0 on failure (see [VKNative.lastError]).
     */
    @JvmStatic external fun createContext(enableValidation: Boolean): Long

    /**
     * Replaces the window rendered to, null to release it. Returns 1 when the render pass was recreated
     * (pipelines must be recreated), 0 otherwise, or -1 on failure (see [VKNative.lastError]).
     */
    @JvmStatic external fun setSurface(context: Long, surface: Surface?): Int

    /**
     * Whether video frames can be imported with [importHardwareBuffer].
     */
    @JvmStatic external fun supportsExternalTextures(context: Long): Boolean

    /**
     * Imports a video frame as a texture, acquired from its producer before the next frame draws, 0
     * when it can't be. It keeps its own reference to the buffer. Destroy it with [VKNative.destroyTexture].
     */
    @JvmStatic external fun importHardwareBuffer(context: Long, hardwareBuffer: HardwareBuffer): Long

    /**
     * The YCbCr sampler an imported frame is sampled with, 0 for none. Pipelines sampling it are made for it.
     */
    @JvmStatic external fun textureYcbcrSampler(context: Long, texture: Long): Long
}
