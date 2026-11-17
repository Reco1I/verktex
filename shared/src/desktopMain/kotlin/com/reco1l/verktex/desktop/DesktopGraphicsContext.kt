package com.reco1l.verktex.desktop

import com.reco1l.verktex.graphics.GraphicsContext
import java.nio.ByteBuffer

/**
 * The graphics context of a desktop window, OpenGL ([com.reco1l.verktex.desktop.gl.GLContext]) or
 * Vulkan ([com.reco1l.verktex.desktop.vulkan.DesktopVKContext]): what the host needs from either.
 */
interface DesktopGraphicsContext : GraphicsContext {

    /**
     * The window drawn to, which the context created on the main thread.
     */
    val window: DesktopWindow

    /**
     * Whether presenting waits for the display's refresh, which caps the frame rate to it. Off, the
     * engine draws as fast as it can. Can be changed at any time, it applies from the next frame.
     */
    var vsync: Boolean

    /**
     * Called on the draw thread after a frame was drawn, right before it's presented. Used to read
     * the frame back with [readPixels], e.g. for screenshots.
     */
    var onBeforePresent: (() -> Unit)?

    /**
     * The frame about to be presented as RGBA rows from the top, the window's size, or null when it
     * can't be read. Only from [onBeforePresent].
     */
    fun readPixels(): ByteBuffer?
}
