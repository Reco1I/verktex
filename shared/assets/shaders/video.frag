#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;

in vec2 vUV;
in vec4 vColor;

uniform samplerExternalOES uTexture;
uniform mat4 uTransform;

out vec4 fragColor;

#import "lib/color.glsl"
#import "lib/clip.glsl"

void main() {
    fragColor = texture(uTexture, (uTransform * vec4(vUV, 0.0, 1.0)).xy) * premultiply(vColor) * clipCoverage();
}
