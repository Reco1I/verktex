package com.reco1l.verktex.ui

import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.ScaleType
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.ui.container.UIContainer


inline fun UIContainer.sprite(builder: UISprite.() -> Unit): UISprite {
    return UISprite().apply(builder).also(::plusAssign)
}

/**
 * Sprite that allows to change texture once created.
 */
open class UISprite(texture: GraphicsTexture.Region? = null) : UICanvas() {

    /**
     * Whether the texture should be flipped horizontally.
     */
    var flippedHorizontal = false

    /**
     * Whether the texture should be flipped vertically.
     */
    var flippedVertical = false

    /**
     * The texture region of the sprite.
     */
    var textureRegion: GraphicsTexture.Region? = texture
        set(value) {
            if (field == value) return
            field = value
            invalidateMeasure()
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


    override fun onMeasure(
        parentConstraints: LayoutConstraints,
        contentConstraints: LayoutConstraints
    ): Vec2 {
        val textureSize = textureRegion?.let { region ->
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

        val region = textureRegion ?: return
        val p = placement

        if (!scaleType.place(region, bounds.width, bounds.height, gravity, flippedHorizontal, flippedVertical, p)) {
            return
        }

        renderer.drawTexture(region.texture, region, p[0], p[1], p[2], p[3], color = contentColor, uv = Vec4(p[4], p[5], p[6], p[7]))
    }

}

