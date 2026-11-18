// Signed distances, in the units of the given point: negative inside, positive outside.

// The distance to a box centered at the origin with a radius per corner (top left, top right,
// bottom right, bottom left). The y axis points down.
float roundedBoxSDF(vec2 p, vec2 halfSize, vec4 radii) {
    float r = p.x > 0.0
        ? (p.y > 0.0 ? radii.z : radii.y)
        : (p.y > 0.0 ? radii.w : radii.x);
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
