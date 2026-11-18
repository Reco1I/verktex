package com.reco1l.verktex.graphics

/**
 * An interface that represents a graphics device, which is responsible for managing the rendering
 * context and providing access to graphics resources such as buffers, shaders, and textures.
 */
interface Device {

    /**
     * The capabilities of the device, which describe the features and limitations of the graphics hardware.
     */
    val capabilities: Capabilities

    /**
     * Creates a new instance of a [Buffer] object, which is used to store vertex data for rendering.
     */
    fun createBuffer(): Buffer

    /**
     * Creates a new instance of a [Shader] object using the provided source, which contains
     * the source code for the shader program.
     */
    fun createShader(source: Shader.Source): Shader

    /**
     * Creates a new instance of a [Texture] object with the specified width and height, which can be used
     * to store image data for rendering.
     */
    fun createTexture(width: Float, height: Float): Texture

    /**
     * Creates a new external [Texture] (such as one fed with video frames) with the specified
     * width and height. It has no storage of its own and is sampled with [com.reco1l.verktex.BuiltIn.Shaders.Video].
     */
    fun createExternalTexture(width: Float, height: Float): Texture


    /**
     * Represents the capabilities of the device's graphics hardware.
     */
    data class Capabilities(
        /**
         * The maximum texture size supported by the device's graphics hardware.
         */
        val maxTextureSize: Float,

        /**
         * Whether external textures (video frames, see [createExternalTexture]) are shown. When they
         * aren't, they never get a surface to be fed through and callers should leave videos out.
         */
        val supportsExternalTextures: Boolean = true,
    )

}