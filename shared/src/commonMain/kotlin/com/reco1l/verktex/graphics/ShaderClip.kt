package com.reco1l.verktex.graphics

import com.reco1l.verktex.data.Vec4

/**
 * A clip the shaders apply to what is drawn under it, see [Renderer.pushClip]: a rectangle with
 * rounded corners in the local space of a component, which the fragments are brought back to with
 * the inverse of the component's transformation. Unlike a scissor it follows rounded corners,
 * rotations and skews, and its edges are antialiased.
 *
 * The built-in shaders apply it with `lib/clip.glsl`, from the uniforms [uniform] returns. A clip is
 * immutable, a draw command keeps it until the frame is executed, and two commands with the same
 * clip instance can be merged.
 */
class ShaderClip(

    /**
     * The matrix from the scene to the clip's local space, as its a, b, c and d.
     */
    val transform: Vec4,

    /**
     * The translation of the matrix from the scene to the clip's local space, the height of the
     * framebuffer when its origin is at the bottom left (0 otherwise), and 1.
     */
    val translation: Vec4,

    /**
     * The clipped rectangle in local space, as x, y, width and height.
     */
    val rect: Vec4,

    /**
     * The radius of the top left, top right, bottom right and bottom left corners, in local space.
     */
    val radii: Vec4,
) {

    /**
     * The value of a clip uniform of `lib/clip.glsl`, null for other names.
     */
    fun uniform(name: String): Any? = when (name) {
        TRANSFORM -> transform
        TRANSLATION -> translation
        RECT -> rect
        RADII -> radii
        else -> null
    }


    companion object {
        const val TRANSFORM = "uClipTransform"
        const val TRANSLATION = "uClipTranslation"
        const val RECT = "uClipRect"
        const val RADII = "uClipRadii"

        /**
         * The uniform names of a clip.
         */
        val UNIFORMS = listOf(TRANSFORM, TRANSLATION, RECT, RADII)

        /**
         * The uniforms without a clip: its translation's last component is 0, which the shaders read
         * as no clip.
         */
        val NONE = ShaderClip(
            transform = Vec4(1f, 0f, 0f, 1f),
            translation = Vec4(0f, 0f, 0f, 0f),
            rect = Vec4(0f, 0f, 0f, 0f),
            radii = Vec4(0f, 0f, 0f, 0f),
        )
    }
}
