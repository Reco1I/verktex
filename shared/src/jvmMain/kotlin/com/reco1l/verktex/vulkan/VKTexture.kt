package com.reco1l.verktex.vulkan

import com.reco1l.verktex.graphics.PixelBuffer
import com.reco1l.verktex.graphics.Texture
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * An RGBA8 texture of the Vulkan backend. Uploads are staged and copied before the frame's render
 * pass, see [Texture.flushUploads], which the renderer calls for each texture it draws.
 *
 * It may be created and released from any thread, the image is only destroyed once the GPU is done
 * with it.
 */
class VKTexture internal constructor(
    override val width: Float,
    override val height: Float,
    private val context: VKContext,
) : Texture() {

    /**
     * The native texture, 0 until loaded.
     */
    @Volatile
    var handle = 0L
        private set

    // Uploads need a direct buffer, sources normally provide one.
    private var directCopy: ByteBuffer? = null


    override fun onCreateTexture() {
        if (handle == 0L) {
            handle = VKNative.createTexture(context.handle, width.toInt(), height.toInt())
            check(handle != 0L) { "Couldn't create a ${width.toInt()}x${height.toInt()} texture." }
        }
    }

    override fun onBindTexture() = Unit

    override fun onWritePixels(x: Int, y: Int, width: Int, height: Int, pixels: PixelBuffer) {
        val bytes = pixels
        val size = bytes.remaining()

        if (bytes.isDirect) {
            VKNative.uploadTexture(context.handle, handle, x, y, width, height, bytes, bytes.position(), size)
            return
        }

        val copy = directCopy?.takeIf { it.capacity() >= size }
            ?: ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder()).also { directCopy = it }
        copy.clear()
        copy.put(bytes.duplicate())
        VKNative.uploadTexture(context.handle, handle, x, y, width, height, copy, 0, size)
    }

    override fun onUnload() {
        if (handle != 0L) {
            VKNative.destroyTexture(context.handle, handle)
            handle = 0L
        }
    }
}
