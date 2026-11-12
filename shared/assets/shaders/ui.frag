#version 300 es
precision highp float;

// A rounded box like box.frag with its parameters per vertex, or a textured quad (text).

in vec2 vUV;
in vec4 vColor;
// Width, height, border width, and the mode plus 4 times the fade flags.
flat in vec4 vBox;
// The radius of the top left, top right, bottom right and bottom left corners.
flat in vec4 vRadii;
// The colors of each side of the border. For soft boxes, vBorderTop holds the softness and inset.
flat in vec4 vBorderTop;
flat in vec4 vBorderRight;
flat in vec4 vBorderBottom;
flat in vec4 vBorderLeft;

uniform vec4 uColor;
uniform sampler2D uTexture;

out vec4 fragColor;

// Colours given to shaders have straight alpha, textures and the output are premultiplied.

// Modes: 0 draws a rounded box, 1 samples the texture, 2 draws a soft rounded box (a shadow).
//
// The fade flags tell which sides have no color of their own and fade between their neighbors:
// 1 top, 2 right, 4 bottom, 8 left. Without a color on the left and the right, the whole border
// fades from the top color to the bottom one (and likewise from left to right).

// Whether the given bit is set in the flags. GLSL ES 1.00 has no integer bit operations.
bool hasFlag(float flags, float bit) {
    return mod(floor(flags / bit), 2.0) > 0.5;
}

// Signed distance to a rounded box centered at the origin, in pixels, with a radius per corner
// (top left, top right, bottom right, bottom left). The y axis points down.
float roundedBoxSDF(vec2 p, vec2 halfSize, vec4 radii) {
    float r = p.x > 0.0
        ? (p.y > 0.0 ? radii.z : radii.y)
        : (p.y > 0.0 ? radii.w : radii.x);
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// The color of the border at the given point, in pixels from the top left corner of the box.
vec4 borderColor(vec2 pixel, vec2 size, float flags) {
    bool fadeTop = hasFlag(flags, 1.0);
    bool fadeRight = hasFlag(flags, 2.0);
    bool fadeBottom = hasFlag(flags, 4.0);
    bool fadeLeft = hasFlag(flags, 8.0);

    vec2 uv = pixel / size;
    vec4 vertical = mix(vBorderTop, vBorderBottom, uv.y);
    vec4 horizontal = mix(vBorderLeft, vBorderRight, uv.x);

    if (fadeLeft && fadeRight) {
        return vertical;
    }
    if (fadeTop && fadeBottom) {
        return horizontal;
    }

    // Each side owns the pixels closest to it, so sides of the same width meet on the diagonals
    // from the corners, as in CSS. Different widths would compare the distances over each width.
    float toTop = pixel.y;
    float toBottom = size.y - pixel.y;
    float toLeft = pixel.x;
    float toRight = size.x - pixel.x;

    vec4 top = fadeTop ? horizontal : vBorderTop;
    vec4 bottom = fadeBottom ? horizontal : vBorderBottom;
    vec4 left = fadeLeft ? vertical : vBorderLeft;
    vec4 right = fadeRight ? vertical : vBorderRight;

    vec4 nearestRow = toTop < toBottom ? top : bottom;
    vec4 nearestColumn = toLeft < toRight ? left : right;
    float rowDistance = min(toTop, toBottom);
    float columnDistance = min(toLeft, toRight);

    // Antialiased along the diagonal.
    float aa = max(fwidth(pixel.x), 0.0001);
    return mix(nearestColumn, nearestRow, clamp(0.5 + (columnDistance - rowDistance) / aa, 0.0, 1.0));
}

void main() {
    float flags = floor((vBox.w + 0.5) / 4.0);
    float mode = vBox.w - flags * 4.0;

    if (mode > 0.5 && mode < 1.5) {
        vec4 tint = vColor * uColor;
        fragColor = texture(uTexture, vUV) * vec4(tint.rgb * tint.a, tint.a);
        return;
    }

    bool isSoft = mode > 1.5;
    float borderWidth = vBox.z;
    float softness = isSoft ? vBorderTop.x : 0.0;
    float inset = isSoft ? vBorderTop.y : 0.0;

    vec2 p = (vUV - 0.5) * vBox.xy;
    vec2 boxSize = vBox.xy - 2.0 * inset;
    vec4 radii = min(vRadii, vec4(min(boxSize.x, boxSize.y) * 0.5));

    float dist = roundedBoxSDF(p, boxSize * 0.5, radii);
    float aa = max(fwidth(dist), 0.0001);

    float alpha = softness > 0.0
        ? 1.0 - smoothstep(-softness, softness, dist)
        : clamp(0.5 - dist / aa, 0.0, 1.0);

    // A negative border width cuts the shape out of a soft edge (a glow): it fades from the edge
    // outwards and nothing is drawn inside.
    if (borderWidth < 0.0 && softness > 0.0) {
        alpha = (1.0 - smoothstep(0.0, softness, dist)) * clamp(0.5 + dist / aa, 0.0, 1.0);
    }

    vec4 fill = uColor * vColor;

    if (borderWidth > 0.0 && !isSoft) {
        float borderMask = clamp(0.5 + (dist + borderWidth) / aa, 0.0, 1.0);

        vec4 border = borderColor(vUV * vBox.xy, vBox.xy, flags);
        border.a *= borderMask;

        // The border goes over the fill, so a translucent border tints it instead of showing
        // what is behind the box.
        float outAlpha = border.a + fill.a * (1.0 - border.a);
        vec3 outColor = outAlpha > 0.0
            ? (border.rgb * border.a + fill.rgb * fill.a * (1.0 - border.a)) / outAlpha
            : fill.rgb;

        fill = vec4(outColor, outAlpha);
    }

    fill.a *= alpha;
    fragColor = vec4(fill.rgb * fill.a, fill.a);
}
