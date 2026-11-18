// The clip of a component drawn by the shader, see Renderer.pushClip: a rounded rectangle in the
// component's local space, which the fragment is brought back to with the inverse of its
// transformation, so it follows rotations and skews. Every draw under a clip multiplies its output
// by clipCoverage(). Without a clip, uClipTranslation.w is 0 and the coverage is 1.
//
// Fragment shaders only: it uses gl_FragCoord.

#import "sdf.glsl"

// The matrix from the scene to the clip's local space: a, b, c and d...
uniform vec4 uClipTransform;
// ...its translation, then the framebuffer height when its origin is at the bottom left (OpenGL, 0
// otherwise), and whether there is a clip (1) or not (0).
uniform vec4 uClipTranslation;
// The rectangle in local space, x, y, width and height.
uniform vec4 uClipRect;
// The radius of the top left, top right, bottom right and bottom left corners, in local space.
uniform vec4 uClipRadii;

float clipCoverage() {
    // Most draws aren't clipped. The condition is a uniform, every fragment takes the same branch, so
    // they skip the rest at the cost of a test.
    if (uClipTranslation.w < 0.5) {
        return 1.0;
    }

    // The fragment in scene pixels, from the top left corner.
    vec2 scene = vec2(
        gl_FragCoord.x,
        uClipTranslation.z > 0.0 ? uClipTranslation.z - gl_FragCoord.y : gl_FragCoord.y
    );

    vec2 local = vec2(
        uClipTransform.x * scene.x + uClipTransform.z * scene.y + uClipTranslation.x,
        uClipTransform.y * scene.x + uClipTransform.w * scene.y + uClipTranslation.y
    );

    vec2 halfSize = uClipRect.zw * 0.5;
    vec4 radii = min(uClipRadii, vec4(min(halfSize.x, halfSize.y)));
    float dist = roundedBoxSDF(local - uClipRect.xy - halfSize, halfSize, radii);

    // Antialiased over a pixel of the screen: the size of a pixel in local space is the scale of the
    // matrix, the same for every fragment. Without derivatives, which would need an extension on
    // OpenGL ES 2.0 that shaders such as texture.frag don't otherwise depend on.
    float pixel = sqrt(abs(uClipTransform.x * uClipTransform.w - uClipTransform.y * uClipTransform.z));
    return clamp(0.5 - dist / max(pixel, 0.0001), 0.0, 1.0);
}
