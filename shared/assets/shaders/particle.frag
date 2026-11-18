#version 300 es
precision highp float;

in vec2 vUV;
in vec4 vColor;

uniform sampler2D uTexture;

out vec4 fragColor;

#import "lib/color.glsl"
#import "lib/clip.glsl"

void main() {
    fragColor = texture(uTexture, vUV) * premultiply(vColor) * clipCoverage();
}
