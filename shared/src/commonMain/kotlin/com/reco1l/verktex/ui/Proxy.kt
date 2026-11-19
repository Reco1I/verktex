package com.reco1l.verktex.ui

import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.ui.container.Container

/**
 * Adds a [Proxy] drawing [target] here.
 */
inline fun Container.proxy(target: Component, builder: Proxy.() -> Unit = {}) = Proxy(target).apply(builder).also { +it }

/**
 * Draws [target] at the point of the draw order where this proxy is, instead of where it is in its parent. A
 * component can then belong to one parent and be drawn in another layer, e.g. the approach circle of a hit object
 * above every object, while it's updated, animated and laid out with the object.
 *
 * - While the proxy is in a container, its target is skipped by its parent and drawn by the proxy alone. A target has
 *   one proxy at a time, the last one attached.
 * - The target keeps its own position: its matrices are absolute, so it's drawn where its parent puts it.
 * - The alpha and the tint of the target's ancestors are applied, so fading its parent fades it too, on top of the
 *   ones of the proxy's ancestors. It isn't drawn while it has no parent, or one of its ancestors is invisible.
 * - The proxy takes no space and no input: input reaches the target through its parent.
 */
class Proxy(target: Component? = null) : Component() {

    /**
     * The component this proxy draws.
     */
    var target: Component? = null
        set(value) {
            if (field === value) return
            release()
            field = value
            if (parent != null) claim()
        }

    init {
        this.target = target
    }


    private fun claim() {
        target?.proxy = this
    }

    private fun release() {
        val target = target ?: return
        if (target.proxy === this) {
            target.proxy = null
        }
    }

    override fun onAttached() {
        super.onAttached()
        claim()
    }

    override fun onDetached() {
        super.onDetached()
        release()
    }


    override fun intersectsSceneRect(x: Float, y: Float, width: Float, height: Float): Boolean {
        val target = target ?: return false
        target.validateTransformationChain()
        return target.intersectsSceneRect(x, y, width, height)
    }

    override fun onDraw(renderer: Renderer) {
        val target = target ?: return
        if (target.proxy !== this) return

        // A component taken out of its parent isn't shown, as it wouldn't be without a proxy.
        if (target.parent == null) return

        var alpha = 1f
        var red = 1f
        var green = 1f
        var blue = 1f

        var ancestor = target.parent
        while (ancestor != null) {
            if (!ancestor.isVisible) {
                return
            }

            alpha *= ancestor.alpha
            if (ancestor.tintChildren) {
                red *= ancestor.red
                green *= ancestor.green
                blue *= ancestor.blue
            }
            ancestor = ancestor.parent
        }

        target.validateTransformationChain()

        renderer.pushColor(Color4(1f, 1f, 1f, alpha))
        renderer.pushTint(Color4(red, green, blue, 1f))
        target.isDrawingThroughProxy = true
        try {
            target.draw(renderer)
        } finally {
            target.isDrawingThroughProxy = false
            renderer.popTint()
            renderer.popColor()
        }
    }
}
