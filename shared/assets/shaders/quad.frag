#version 300 es
precision highp float;

in vec2 vUV;
in vec4 vColor;

uniform vec4 uColor;
uniform vec4 uBorderColor;
uniform float uBorderWidth;
uniform float uRadius;
uniform vec2 uSize;

out vec4 fragColor;

#import "lib/color.glsl"
#import "lib/sdf.glsl"
#import "lib/clip.glsl"

void main() {
    float clip = clipCoverage();

    vec2 p = (vUV - 0.5) * uSize;
    float r = min(uRadius, min(uSize.x, uSize.y) * 0.5);

    float dist = roundedBoxSDF(p, uSize * 0.5, vec4(r));
    float aa = max(fwidth(dist), 0.0001);

    float alpha = clamp(0.5 - dist / aa, 0.0, 1.0);
    float border = uBorderWidth > 0.0 ? clamp(0.5 + (dist + uBorderWidth) / aa, 0.0, 1.0) : 0.0;

    vec4 finalColor = mix(uColor * vColor, uBorderColor, border);
    finalColor.a *= alpha;
    fragColor = premultiply(finalColor) * clip;
}
