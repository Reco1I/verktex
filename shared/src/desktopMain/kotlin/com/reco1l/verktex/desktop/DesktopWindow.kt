package com.reco1l.verktex.desktop

import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.graphics.Window
import org.lwjgl.glfw.GLFW.*

/**
 * A GLFW window, the size being its framebuffer's in pixels. Its handle is made by the graphics
 * context (an OpenGL or a Vulkan one, see [DesktopGPUContext]) on the main thread, and the host
 * keeps the size up to date from GLFW's callbacks.
 */
class DesktopWindow(val handle: Long) : Window {

    @Volatile
    override var windowWidth = 0

    @Volatile
    override var windowHeight = 0

    // No system bars, cutouts or on-screen keyboard on desktop.
    override var insets = Window.Insets(Vec4.Zero, Vec2.Zero)

    @Volatile
    private var cachedRefreshRate = 60f

    override val refreshRate: Float
        get() = cachedRefreshRate


    init {
        val width = IntArray(1)
        val height = IntArray(1)
        glfwGetFramebufferSize(handle, width, height)
        windowWidth = width[0]
        windowHeight = height[0]
        updateRefreshRate()
    }


    /**
     * Reads the refresh rate of the monitor the window is on. GLFW is only called from the main
     * thread, so the host calls this when the window moves.
     */
    fun updateRefreshRate() {
        val monitor = glfwGetWindowMonitor(handle).takeIf { it != 0L } ?: glfwGetPrimaryMonitor()
        if (monitor == 0L) return

        val mode = glfwGetVideoMode(monitor) ?: return
        if (mode.refreshRate() > 0) {
            cachedRefreshRate = mode.refreshRate().toFloat()
        }
    }

    override fun shouldClose(): Boolean {
        return glfwWindowShouldClose(handle)
    }

    override fun pollEvents() {
        // The host polls GLFW's events on the main thread.
    }
}
