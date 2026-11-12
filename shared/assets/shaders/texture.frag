#version 300 es
precision highp float;

in vec2 vUV;
in vec4 vColor;

uniform sampler2D uTexture;

// Rounded corners, only set by draws that have them (zero by default): the radius of the top
// left, top right, bottom right and bottom left corners of the mask, a rectangle (x, y, width,
// height) in the same space as the quad, and the quad and its texture coordinates (u0, v0, u1,
// v1), to find where each fragment is in that space. The mask can differ from the quad, e.g. a
// cropped or letterboxed background is cut by the shape of its component.
uniform vec4 uRadii;
uniform vec4 uMask;
uniform vec4 uQuad;
uniform vec4 uUVRect;

out vec4 fragColor;

// Colours given to shaders have straight alpha, textures and the output are premultiplied.

float roundedBoxSDF(vec2 p, vec2 halfSize, vec4 radii) {
    float r = p.x > 0.0
        ? (p.y > 0.0 ? radii.z : radii.y)
        : (p.y > 0.0 ? radii.w : radii.x);
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {

    vec4 color = texture(uTexture, vUV) * vec4(vColor.rgb * vColor.a, vColor.a);

    if (uMask.z > 0.0) {
        vec2 local = uQuad.xy + (vUV - uUVRect.xy) / (uUVRect.zw - uUVRect.xy) * uQuad.zw;
        vec2 halfSize = uMask.zw * 0.5;
        vec4 radii = min(uRadii, vec4(min(halfSize.x, halfSize.y)));

        // Antialiased over a unit, a pixel unless the component is scaled. Derivatives would need
        // an extension on OpenGL ES 2.0, which every textured draw would then depend on.
        float dist = roundedBoxSDF(local - uMask.xy - halfSize, halfSize, radii);
        color *= clamp(0.5 - dist, 0.0, 1.0);
    }

    fragColor = color;
}
