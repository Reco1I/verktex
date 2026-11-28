package com.reco1l.verktex.data

import com.reco1l.verktex.Verktex
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.graphics.Texture as GPUTexture
import com.reco1l.verktex.ui.BlurCapture
import com.reco1l.verktex.ui.Theme
import com.reco1l.verktex.ui.ThemeDependent
import com.reco1l.verktex.ui.Component as UIComponent
import com.reco1l.verktex.ui.themed

/**
 * What a component is filled with behind its content, see [com.reco1l.verktex.ui.Component.background].
 * It follows the shape of the component, rounded by its radius, and lies under its border. It can be
 * a color, a texture, a component or a blur of what is behind.
 *
 * It can be taken from the theme with `themed { Background.Color(palette.surface) }`, see [Themed].
 */
sealed class Background : ThemeDependent<Background> {

    /**
     * Whether the background draws anything.
     */
    abstract val isVisible: Boolean

    override val isThemed: Boolean
        get() = false

    override fun resolve(theme: Theme): Background = this


    /**
     * No background.
     */
    data object None : Background() {
        override val isVisible get() = false
    }

    /**
     * A background of a single [color], which can be [themed].
     */
    class Color(val color: Color4) : Background() {

        override val isVisible get() = color.alpha > 0f

        override val isThemed get() = color.isThemed

        override fun resolve(theme: Theme): Background = if (isThemed) Color(color.resolve(theme)) else this

        override fun equals(other: Any?) = this === other || other is Color && color == other.color

        override fun hashCode() = color.hashCode()

        override fun toString() = "Color($color)"
    }

    /**
     * A background of a texture [region], scaled to the component by [scaleType] and aligned by
     * [gravity] where it doesn't fill it, then tinted by [tint].
     */
    class Texture(
        val region: GPUTexture.Region,
        val scaleType: ScaleType = ScaleType.Crop,
        val gravity: Vec2 = Anchor.Center,
        val tint: Color4 = Color4.White,
    ) : Background() {

        override val isVisible get() = tint.alpha > 0f

        override val isThemed get() = tint.isThemed

        override fun resolve(theme: Theme): Background =
            if (isThemed) Texture(region, scaleType, gravity, tint.resolve(theme)) else this

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Texture
                && region == other.region
                && scaleType == other.scaleType
                && gravity == other.gravity
                && tint == other.tint
        }

        override fun hashCode(): Int {
            var result = region.hashCode()
            result = 31 * result + scaleType.hashCode()
            result = 31 * result + gravity.hashCode()
            result = 31 * result + tint.hashCode()
            return result
        }

        override fun toString() = "Texture($region, $scaleType, $gravity, $tint)"
    }

    /**
     * A background drawn by a [component], e.g. a sprite or a container of several layers. While
     * it's the background of a component, that component is its parent: it's laid out over the
     * whole component (its padding aside), updated before the content, follows its transformations
     * and theme, is clipped to its rounded corners and drawn under its border. It doesn't receive
     * input.
     *
     * Only a [com.reco1l.verktex.ui.container.Container] can have one, since a component's parent
     * is a container, and a component is the background of a single one at a time.
     */
    class Component(val component: UIComponent) : Background() {

        override val isVisible get() = component.isVisible

        override fun equals(other: Any?) = this === other || other is Component && component === other.component

        override fun hashCode() = component.hashCode()

        override fun toString() = "Component($component)"
    }

    /**
     * A background of what is drawn behind the component, blurred by [radius], which can be [themed] (roughly the standard
     * deviation of a Gaussian blur, zero to not blur it), with [color], which can be [themed], over it.
     * It's captured from the framebuffer when the background is drawn, so it shows what was drawn
     * before the component: the scene, the overlays below, the components under it.
     *
     * A [static] blur is captured once and shown until [invalidate] is called or the bounds of the
     * component in the window change: what is behind keeps being updated, but changes to it aren't
     * seen. The blur costs nothing more than showing a texture then. Otherwise it's captured and
     * blurred every frame, following what is behind.
     *
     * A static blur on an overlay covering the whole window (see [Verktex.addOverlay]), such as a
     * menu, also stands for what is behind it: the scene and the overlays below aren't drawn while
     * its capture is shown.
     *
     * It holds the capture, a texture as large as the window, so a blur is the background of a single
     * component at a time. The capture is freed when the component is removed from its parent or
     * its background changes.
     */
    class Blur(
        val radius: Dimension.Fixed,
        val static: Boolean = true,
        val color: Color4 = Color4.Transparent,
    ) : Background() {

        override val isVisible get() = radius.value > 0f || color.alpha > 0f

        override val isThemed get() = radius.isThemed || color.isThemed

        override fun resolve(theme: Theme): Background =
            if (isThemed) Blur(radius.resolve(theme), static, color.resolve(theme)) else this

        /**
         * The component this is the background of.
         */
        internal var owner: UIComponent? = null

        internal val capture = BlurCapture()

        /**
         * Captures what is behind again in the next frame, e.g. after it changed. Only needed when
         * [static], otherwise it's captured every frame.
         */
        fun invalidate() = capture.invalidate()

        internal fun draw(renderer: Renderer, component: UIComponent, localToScene: Mat2, radii: CornerRadius) {
            capture.draw(renderer, component, localToScene, radius.toPixels(), static, radii)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other is Blur && radius == other.radius && static == other.static && color == other.color
        }

        override fun hashCode(): Int {
            var result = radius.hashCode()
            result = 31 * result + static.hashCode()
            result = 31 * result + color.hashCode()
            return result
        }

        override fun toString() = "Blur($radius, static = $static, $color)"
    }

    /**
     * A background taken from the theme, see [themed]. Themeable properties resolve it with the
     * theme of their component, elsewhere it's the one of [Verktex.theme].
     */
    class Themed(val block: Theme.() -> Background) : Background() {

        override val isVisible get() = Verktex.theme.block().isVisible

        override val isThemed get() = true

        override fun resolve(theme: Theme): Background = theme.block().resolve(theme)

        override fun equals(other: Any?) = this === other || other is Themed && block == other.block

        override fun hashCode() = block.hashCode()

        override fun toString() = "Themed(${Verktex.theme.block()})"
    }
}
