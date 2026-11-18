// Positions are in pixels of the viewport from its top left corner, with the y axis down.

// The normalized device coordinates of a position in pixels.
vec2 toNDC(vec2 position, vec2 viewportSize) {
    return vec2(
        (position.x / viewportSize.x) * 2.0 - 1.0,
        1.0 - (position.y / viewportSize.y) * 2.0
    );
}
