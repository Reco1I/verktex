package com.reco1l.verktex.desktop.vulkan

import com.reco1l.verktex.desktop.DesktopGPUContext
import com.reco1l.verktex.desktop.DesktopWindow
import com.reco1l.verktex.vulkan.VKContext
import com.reco1l.verktex.vulkan.VKNative
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import org.lwjgl.glfw.GLFWVulkan.nglfwCreateWindowSurface
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The Vulkan context of a GLFW window. The window has no OpenGL context (`GLFW_NO_API`) and its
 * surface is created by GLFW from the context's instance, once, as a desktop window keeps it for its
 * lifetime; resizing only recreates the swapchain.
 *
 * Created with [create] on the main thread, which GLFW requires for windows.
 */
class DesktopVKContext private constructor(
    override val window: DesktopWindow,
    handle: Long,
) : VKContext(handle), DesktopGPUContext {

    @Volatile
    override var vsync = false

    @Volatile
    override var onBeforePresent: (() -> Unit)? = null

    // The vsync the swapchain was made with, the native side defaults to it.
    private var appliedVsync = true


    override fun updateSurface(): Boolean {
        val vsync = vsync
        if (vsync != appliedVsync) {
            VKDesktopNative.setVsync(handle, vsync)
            appliedVsync = vsync
        }

        // Minimised, there's nothing to draw to.
        return window.windowWidth > 0 && window.windowHeight > 0
    }

    override fun present() {
        onBeforePresent?.invoke()
        super.present()
    }

    override fun readPixels(): ByteBuffer? {
        val width = window.windowWidth
        val height = window.windowHeight
        val pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        return pixels.takeIf { VKDesktopNative.readPixels(handle, it, width, height) }
    }

    override fun destroy() {
        // Called once the draw worker stopped. The surface goes with the context, before its window.
        super.destroy()
        glfwDestroyWindow(window.handle)
    }


    companion object {

        /**
         * Whether GLFW found a Vulkan loader with a device that can present, so the backend may work.
         * GLFW must be initialised.
         */
        fun isSupported(): Boolean = glfwVulkanSupported()

        /**
         * Whether the backend may be offered: GLFW supports Vulkan and the library loads. Doesn't
         * create anything, a device may still fail to start (the host then falls back to OpenGL).
         */
        fun isAvailable(): Boolean = try {
            isSupported() && run { VKNative.ensureLoaded(); true }
        } catch (_: Throwable) {
            false
        }

        /**
         * Creates a window and its Vulkan context, on the main thread. GLFW must be initialised.
         *
         * @throws IllegalStateException if Vulkan can't be used, so the caller can fall back to OpenGL.
         * @throws UnsatisfiedLinkError if the native library isn't built for this platform.
         */
        fun create(title: String, width: Int, height: Int, enableValidation: Boolean): DesktopVKContext {
            check(isSupported()) { "GLFW found no Vulkan loader or no device that can present." }

            val extensionNames = glfwGetRequiredInstanceExtensions()
                ?: throw IllegalStateException("GLFW can't create Vulkan surfaces on this system.")
            val extensions = Array(extensionNames.remaining()) { i -> MemoryUtil.memUTF8(extensionNames.get(extensionNames.position() + i)) }

            // Loads the library before a window is opened, so a missing one fails early.
            VKNative.ensureLoaded()

            glfwDefaultWindowHints()
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
            glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE)
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)

            val windowHandle = glfwCreateWindow(width, height, title, NULL, NULL)
            check(windowHandle != NULL) { "Couldn't create a window for Vulkan." }

            var handle = 0L
            try {
                handle = checkCreated(VKDesktopNative.createContext(enableValidation, extensions))

                val surface = MemoryStack.stackPush().use { stack ->
                    val pointer = stack.mallocLong(1)
                    val result = nglfwCreateWindowSurface(VKDesktopNative.instance(handle), windowHandle, NULL, MemoryUtil.memAddress(pointer))
                    check(result == 0) { "glfwCreateWindowSurface failed: VkResult $result" }
                    pointer.get(0)
                }

                if (VKDesktopNative.setSurface(handle, surface) < 0) {
                    throw IllegalStateException("Couldn't use the window: ${VKNative.lastError()}")
                }

                return DesktopVKContext(DesktopWindow(windowHandle), handle)
            } catch (e: Throwable) {
                if (handle != 0L) VKNative.destroyContext(handle)
                glfwDestroyWindow(windowHandle)
                throw e
            }
        }
    }
}
