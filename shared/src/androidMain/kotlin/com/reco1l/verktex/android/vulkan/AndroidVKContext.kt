package com.reco1l.verktex.android.vulkan

import com.reco1l.verktex.android.AndroidWindow
import com.reco1l.verktex.vulkan.VKContext

/**
 * The Vulkan context of an Android window. A new surface (the activity went to the background and
 * back) only recreates the swapchain.
 *
 * @throws IllegalStateException if Vulkan can't be used, so the caller can fall back to OpenGL ES.
 */
class AndroidVKContext(
    override val window: AndroidWindow,
    enableValidation: Boolean,
) : VKContext(checkCreated(VKAndroidNative.createContext(enableValidation))) {

    private var surfaceGeneration = -1


    override fun updateSurface(): Boolean {
        if (!window.hasSurface) {
            if (surfaceGeneration != -1) {
                VKAndroidNative.setSurface(handle, null)
                surfaceGeneration = -1
            }
            return false
        }

        if (surfaceGeneration != window.surfaceGeneration) {
            if (!onSurfaceSet(VKAndroidNative.setSurface(handle, window.holder.surface))) {
                return false
            }
            surfaceGeneration = window.surfaceGeneration
        }
        return true
    }
}
