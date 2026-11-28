package com.reco1l.verktex.ui

import com.reco1l.verktex.input.HoverEvent

/**
 * Keeps which components are hovered, see [HoverEvent]: the topmost hoverable component under the
 * pointer and its [Interactive] ancestors.
 */
internal class HoverTracker {

    private val event = HoverEvent()

    // Outermost first.
    private val chain = ArrayList<Interactive>()
    private val newChain = ArrayList<Interactive>()


    /**
     * Hovers what is under ([x], [y]) in [roots], tried in order as input events are.
     */
    fun resolve(x: Float, y: Float, roots: List<Component>) {
        event.x = x
        event.y = y
        event.target = null

        for (root in roots) {
            if (root.onInputEvent(event)) {
                break
            }
        }

        newChain.clear()
        var component = event.target
        while (component != null) {
            if (component is Interactive) newChain.add(component)
            component = component.parent
        }
        newChain.reverse()
        event.target = null

        apply()
    }

    /**
     * Hovers nothing.
     */
    fun clear() {
        newChain.clear()
        apply()
    }

    private fun apply() {
        // Those that left, deepest first.
        for (i in chain.indices.reversed()) {
            val component = chain[i]
            if (component !in newChain) {
                component.interaction.isHovered = false
            }
        }

        // Those that came, outermost first.
        for (component in newChain) {
            if (component !in chain) {
                component.interaction.isHovered = true
            }
        }

        chain.clear()
        chain.addAll(newChain)
        newChain.clear()
    }
}
