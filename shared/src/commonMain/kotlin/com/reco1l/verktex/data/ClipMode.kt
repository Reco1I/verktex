package com.reco1l.verktex.data

/**
 * Determines how the component should be clipped.
 *
 * The clipped area follows the component's corner radius and its transformation (rotations, skews):
 * the shaders clip it, see `Renderer.pushClip`. A rectangle aligned to the screen is clipped with a
 * scissor instead, which costs nothing in the shaders.
 */
enum class ClipMode {
    /**
     * No clipping is applied.
     */
    Disabled,

    /**
     * Clips the component to its own bounds.
     */
    Bounds,

    /**
     * Clips the component to its padding bounds.
     */
    Padding,
}