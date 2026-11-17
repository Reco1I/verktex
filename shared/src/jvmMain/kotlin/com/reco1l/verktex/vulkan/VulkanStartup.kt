package com.reco1l.verktex.vulkan

import com.reco1l.verktex.Logger
import com.reco1l.verktex.settings.Settings
import com.reco1l.verktex.settings.VerktexSettings

/**
 * Starts the Vulkan backend on any platform, behind a crash guard, or returns null when it can't be
 * used: the caller then falls back to OpenGL.
 *
 * A driver crash in native code isn't an exception, the process dies. A flag is kept from before
 * Vulkan starts until its first frame is presented: found on the next launch, Vulkan crashed, and
 * OpenGL is used until it's picked again (see [resetVulkanCrashGuard]) instead of crashing on every
 * launch.
 *
 * [createContext] throws when Vulkan can't be initialized; [createDevice] defaults to a device without
 * external textures.
 */
fun startVulkanRenderer(
    createContext: () -> VKContext,
    createDevice: (VKContext) -> VKDevice = { VKDevice(it) },
): VKRenderer? {
    if (Settings[VerktexSettings.VulkanStarting]) {
        Logger.w("Verktex", "Vulkan didn't start the last time, using OpenGL. Pick it again in the settings to retry.")
        return null
    }
    // Saved right away, it must be on disk if the process dies right after.
    Settings[VerktexSettings.VulkanStarting] = true
    Settings.flush()

    var context: VKContext? = null

    return try {
        context = createContext()
        VKRenderer(context, createDevice(context), context.window).also { renderer ->
            Logger.i("Verktex", "Using the experimental Vulkan backend: ${context.version}.")
            renderer.onFirstFramePresented = { resetVulkanCrashGuard() }
        }
    } catch (e: Throwable) {
        // UnsatisfiedLinkError included, e.g. a platform the native library wasn't built for. It
        // failed without crashing, there's no reason not to try again next time.
        Logger.e("Verktex", "Couldn't use Vulkan, falling back to OpenGL.", e)
        try { context?.destroy() } catch (_: Throwable) {}
        resetVulkanCrashGuard()
        null
    }
}

/**
 * Lets Vulkan be tried again after it crashed while starting (see [startVulkanRenderer]), e.g. once
 * the user picks it again.
 */
fun resetVulkanCrashGuard() {
    Settings[VerktexSettings.VulkanStarting] = false
}
