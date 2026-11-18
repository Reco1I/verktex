package com.reco1l.verktex.testing

import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.dip

/**
 * A safe area the UI test browser gives the window where it has none (on desktop), so components
 * and scenes can be tried as on a phone in landscape. Its sides are in dip, as left, top, right and
 * bottom.
 *
 * @param label The short name the browser shows, and that `--safe-area` takes (case ignored), as
 * the name of the constant does.
 */
enum class SimulatedSafeArea(
    val label: String,
    private val left: Float,
    private val top: Float,
    private val right: Float,
    private val bottom: Float,
) {
    /**
     * The window's own safe area.
     */
    None("No cutout", 0f, 0f, 0f, 0f),

    /**
     * An Android phone with its camera cutout on the left, as the system bars are hidden in the game.
     */
    CutoutLeft("Cutout left", 32f, 0f, 0f, 0f),

    /**
     * The same phone turned the other way.
     */
    CutoutRight("Cutout right", 0f, 0f, 32f, 0f),

    /**
     * A phone with a notch, whose safe area is kept on both sides whichever way it's turned, and
     * the home indicator at the bottom.
     */
    Notch("Notch", 48f, 0f, 48f, 21f);


    /**
     * The safe area in pixels of the window, at the current density.
     */
    fun toPixels() = Vec4(left.dip.toPixels(), top.dip.toPixels(), right.dip.toPixels(), bottom.dip.toPixels())

    companion object {
        /**
         * The preset named [name] or labeled with it, ignoring the case, or null.
         */
        fun find(name: String) = entries.firstOrNull { it.name.equals(name, true) || it.label.equals(name, true) }
    }
}
