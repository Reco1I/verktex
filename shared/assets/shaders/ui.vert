#version 300 es

// Boxes and text drawn in the same batches, see ui.frag. Everything describing a box is a vertex
// attribute instead of a uniform, so boxes of any shape share a draw call.
//
// OpenGL ES 2.0 guarantees only 8 attributes and 8 varyings, so the four border colors are packed
// two per attribute, each in two floats holding 16 bits (red * 256 + green, blue * 256 + alpha),
// which are exact in a float, and unpacked here, where floats are always high precision.

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aUV;
layout(location = 2) in vec4 aColor;
// Width, height, border width, and the mode plus 4 times the fade flags (see ui.frag).
layout(location = 3) in vec4 aBox;
// The radius of the top left, top right, bottom right and bottom left corners.
layout(location = 4) in vec4 aRadii;
// The top and right border colors, packed. For soft boxes: softness and inset.
layout(location = 5) in vec4 aBorder0;
// The bottom and left border colors, packed.
layout(location = 6) in vec4 aBorder1;

uniform vec2 uViewportSize;

out vec2 vUV;
out vec4 vColor;
flat out vec4 vBox;
flat out vec4 vRadii;
flat out vec4 vBorderTop;
flat out vec4 vBorderRight;
flat out vec4 vBorderBottom;
flat out vec4 vBorderLeft;

vec4 unpackColor(vec2 bits) {
    float r = floor(bits.x / 256.0);
    float b = floor(bits.y / 256.0);
    return vec4(r, bits.x - r * 256.0, b, bits.y - b * 256.0) / 255.0;
}

void main() {
    vec2 ndc = vec2(
        (aPosition.x / uViewportSize.x) * 2.0 - 1.0,
        1.0 - (aPosition.y / uViewportSize.y) * 2.0
    );

    gl_Position = vec4(ndc, 0.0, 1.0);

    vUV = aUV;
    vColor = aColor;
    vBox = aBox;
    vRadii = aRadii;

    float mode = aBox.w - floor((aBox.w + 0.5) / 4.0) * 4.0;

    if (mode > 1.5) {
        // A soft box has no border, its slot holds the softness and the inset as they are.
        vBorderTop = aBorder0;
        vBorderRight = vec4(0.0);
        vBorderBottom = vec4(0.0);
        vBorderLeft = vec4(0.0);
    } else {
        vBorderTop = unpackColor(aBorder0.xy);
        vBorderRight = unpackColor(aBorder0.zw);
        vBorderBottom = unpackColor(aBorder1.xy);
        vBorderLeft = unpackColor(aBorder1.zw);
    }
}
