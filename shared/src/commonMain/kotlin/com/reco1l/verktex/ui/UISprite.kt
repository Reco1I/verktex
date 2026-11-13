package com.reco1l.verktex.ui

import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.ScaleType
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.texture.TextureReference
import com.reco1l.verktex.texture.reference
import com.reco1l.verktex.texture.storedTexture
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.container.UIContainer


inline fun UIContainer.sprite(builder: UISprite.() -> Unit): UISprite {
    return UISprite().apply(builder).also(::plusAssign)
}

/**
 * Sprite that allows to change texture once created.
 *
 * Its [texture] can be a region given directly or one stored in [com.reco1l.verktex.texture.Textures]
 * under a key ([storedTexture]), which the sprite follows as it's replaced.
 */
open class UISprite(texture: TextureReference? = null) : UICanvas() {

    constructor(region: GraphicsTexture.Region?) : this(region?.reference)

    /**
     * Whether the texture should be flipped horizontally.
     */
    var flippedHorizontal = false

    /**
     * Whether the texture should be flipped vertically.
     */
    var flippedVertical = false

    /**
     * The texture of the sprite.
     */
    var texture: TextureReference? = texture
        set(value) {
            if (field == value) return
            field = value
            refresh()
        }

    // The region the texture resolved to, the one the sprite is measured and drawn with.
    private var resolvedRegion: GraphicsTexture.Region? = texture?.resolve()

    /**
     * The texture region the sprite draws. Setting it is a shorthand for setting [texture] to the
     * region itself.
     */
    var textureRegion: GraphicsTexture.Region?
        get() = texture?.resolve()
        set(value) {
            texture = value?.reference
        }

    /**
     * The scale type of the sprite.
     */
    var scaleType: ScaleType = ScaleType.Fit

    /**
     * The alignment of the texture.
     *
     * If the scale type is [ScaleType.Stretch] it will not take effect.
     */
    var gravity: Vec2 = Anchor.Center


    /**
     * Resolves the texture again, measuring the sprite again if its region changed.
     */
    private fun refresh() {
        val region = texture?.resolve()

        if (region !== resolvedRegion) {
            resolvedRegion = region
            invalidateMeasure()
        }
    }

    override fun onUpdate(clock: FrameClock) {
        refresh()
        super.onUpdate(clock)
    }

    override fun onMeasure(
        parentConstraints: LayoutConstraints,
        contentConstraints: LayoutConstraints
    ): Vec2 {
        refresh()

        val textureSize = resolvedRegion?.let { region ->
            Vec2(
                x = region.width,
                y = region.height
            )
        } ?: Vec2.Zero

        return parentConstraints.constrain(textureSize)
    }

    // The rectangle and texture coordinates the region is drawn with, see ScaleType.place.
    private val placement = FloatArray(8)

    override fun onDraw(renderer: GraphicsRenderer) {

        // A region replaced after this sprite was updated this frame is released, never draw it.
        refresh()

        val region = resolvedRegion ?: return
        val p = placement

        if (!scaleType.place(region, bounds.width, bounds.height, gravity, flippedHorizontal, flippedVertical, p)) {
            return
        }

        renderer.drawTexture(region.texture, region, p[0], p[1], p[2], p[3], color = contentColor, uv = Vec4(p[4], p[5], p[6], p[7]))
    }

}

