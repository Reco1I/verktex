package com.reco1l.verktex.ui.control

import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.px
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.interpolateFloat
import com.reco1l.verktex.ui.fadeTo
import com.reco1l.verktex.ui.transitionSlot
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.Button
import com.reco1l.verktex.ui.TextButton
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.container.LinearContainer
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.ui.shape.Box
import com.reco1l.verktex.ui.shape.box
import com.reco1l.verktex.ui.themed

/**
 * A row of buttons where only one is selected, marked by an indicator moving between them.
 */
class TabSelector : Container() {

    /**
     * The index of the selected tab, if any.
     */
    var selectedTab: Int?
        get() = selectedButton?.let { buttonsContainer.indexOfFirst { child -> child === it } }?.takeIf { it >= 0 }
        set(value) {
            selectedButton = if (value == null) null else buttonsContainer[value] as TextButton
        }


    private val selectionIndicator: Box
    private val buttonsContainer: LinearContainer

    private var selectedButton: TextButton? = null
        set(value) {
            if (field === value) return
            val wasSelected = field != null
            field = value
            // The selected tab's content is drawn over the indicator, in the color made for it.
            buttonsContainer.forEach { it.restyle() }
            if (wasSelected != (value != null)) {
                onSelectionChanged(value != null)
            }
        }

    private var indicatorX = 0f
    private var indicatorWidth = 0f

    // Whether the indicator was placed on a tab already, before which it has no position to move from.
    private var isIndicatorPlaced = false

    private val indicatorFade by lazy { selectionIndicator.transitionSlot() }


    init {
        padding = themed { Padding(gap.xs) }
        background = Background.Color(themed { (palette.accent.shade(0.1f)).alpha(0.25f) })
        radius = themed { CornerRadius(radius.lg) }

        selectionIndicator = box {
            // Before the alpha, which a color replaces. As solid as a selected control, to be told apart at a glance.
            color = themed { palette.buttonBackgroundSolid }
            alpha = 0f
            height = Dimension.FillAvailable
            radius = themed { CornerRadius(radius.lg) }
        }

        buttonsContainer = linearContainer {
            width = Dimension.FillAvailable
            spacing = themed { gap.xs }
        }
    }


    /**
     * Adds a tab, calling [onSelect] when the user selects it, and returns its button, e.g. to disable it.
     */
    fun addButton(name: String, onSelect: () -> Unit): TextButton {
        val button: TextButton = TextButton().apply {
            width = Dimension.FillAvailable
            text = name
            alignment = Anchor.Center
            density = Button.Density.Small
            variant = Button.Variant.Ghost
            color = themed { if (selectedButton === this@apply) palette.contentOnSolid else palette.accent }
            onActionUp = {
                selectedButton = this
                onSelect()
            }
        }
        buttonsContainer += button
        return button
    }


    override fun onUpdate(clock: FrameClock) {
        val button = selectedButton

        // The indicator follows the bounds of the selected button, which change with the layout, so
        // it's smoothed towards them rather than animated to a fixed target.
        if (button != null && button.bounds.width > 0f) {

            // The first time it shows, it grows from the middle of its tab rather than sliding in from the start.
            if (!isIndicatorPlaced) {
                isIndicatorPlaced = true
                indicatorWidth = 0f
                indicatorX = button.bounds.x + button.bounds.width / 2f
                selectionIndicator.alpha = 0f
                onSelectionChanged(true)
            }

            indicatorWidth = clock.interpolateFloat(INDICATOR_SMOOTHING, indicatorWidth, button.bounds.width)
            indicatorX = clock.interpolateFloat(INDICATOR_SMOOTHING, indicatorX, button.bounds.x)

            selectionIndicator.width = indicatorWidth.px
            selectionIndicator.x = indicatorX.px
        }

        super.onUpdate(clock)
    }

    private fun onSelectionChanged(selected: Boolean) {
        indicatorFade.run { fadeTo(if (selected) 1f else 0f, 0.15f, Easing.OutQuint) }
    }

    private companion object {
        // About the time it takes to reach the selected tab, as quick as a button's feedback.
        const val INDICATOR_SMOOTHING = 0.2f
    }
}
