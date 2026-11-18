#version 300 es

layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aUV;
layout(location = 2) in vec4 aColor;

uniform vec2 uViewportSize;

out vec2 vUV;
out vec4 vColor;

#import "lib/viewport.glsl"

void main() {
    gl_Position = vec4(toNDC(aPosition, uViewportSize), 0.0, 1.0);

    vUV = aUV;
    vColor = aColor;
}