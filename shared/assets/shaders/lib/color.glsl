// Colours given to shaders have straight alpha, textures and the output are premultiplied.

vec4 premultiply(vec4 color) {
    return vec4(color.rgb * color.a, color.a);
}
