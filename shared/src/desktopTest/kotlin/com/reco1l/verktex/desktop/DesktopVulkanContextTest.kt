package com.reco1l.verktex.desktop

import com.reco1l.verktex.desktop.vulkan.VKDesktopNative
import com.reco1l.verktex.vulkan.VKNative
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Creates the desktop Vulkan context without a window: the native library loads, the core
 * initializes on the machine's device and resources can be made. Skipped where the library wasn't
 * built or there's no Vulkan device.
 */
class DesktopVulkanContextTest {

    private fun createContext(): Long {
        val loaded = try {
            VKNative.ensureLoaded()
            true
        } catch (e: Throwable) {
            println("Skipped, the Vulkan library can't be loaded: $e")
            false
        }
        assumeTrue(loaded)

        val handle = VKDesktopNative.createContext(false, emptyArray())
        if (handle == 0L) println("Skipped, Vulkan can't be initialized: ${VKNative.lastError()}")
        assumeTrue(handle != 0L)
        return handle
    }

    @Test
    fun contextInitializesAndMakesResources() {
        val context = createContext()
        try {
            println("Device: ${VKNative.deviceName(context)}, API ${VKNative.apiVersion(context).let { "${it ushr 22}.${(it ushr 12) and 0x3FF}" }}")
            assertTrue(VKNative.maxImageDimension2D(context) >= 4096)
            assertNotEquals(0L, VKDesktopNative.instance(context))

            val texture = VKNative.createTexture(context, 4, 4)
            assertNotEquals(0L, texture)
            val pixels = ByteBuffer.allocateDirect(4 * 4 * 4).order(ByteOrder.nativeOrder())
            VKNative.uploadTexture(context, texture, 0, 0, 4, 4, pixels, 0, pixels.capacity())
            VKNative.destroyTexture(context, texture)

            val spirv = javaClass.getResourceAsStream("/shaders/color.frag.spv")?.use { it.readBytes() }
            assumeTrue("The SPIR-V wasn't built (no glslc)", spirv != null)
            val module = VKNative.createShaderModule(context, spirv!!)
            assertNotEquals(0L, module)
            VKNative.destroyShaderModule(context, module)

            // No surface: there's no frame to acquire, which is skipped rather than failing.
            assertTrue(VKNative.acquireFrame(context, 64, 64) < 0)
        } finally {
            VKNative.destroyContext(context)
        }
    }
}
