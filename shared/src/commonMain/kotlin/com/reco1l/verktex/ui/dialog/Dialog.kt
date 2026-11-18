package com.reco1l.verktex.ui.dialog

import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.Button
import com.reco1l.verktex.ui.Component
import com.reco1l.verktex.ui.Modal
import com.reco1l.verktex.ui.TextButton
import com.reco1l.verktex.ui.container.LinearContainer
import com.reco1l.verktex.ui.shape.Box
import com.reco1l.verktex.ui.text.Text
import com.reco1l.verktex.ui.themed

/**
 * A modal with a title, a content and a row of buttons below it.
 */
abstract class Dialog<T : Component>(val innerContent: T) : Modal(card = LinearContainer().apply {
    orientation = LinearContainer.Orientation.Vertical
    width = fill(0.5f)
    anchor = Anchor.Center
    origin = Anchor.Center
    background = Background.Color(themed { palette.surface })
    radius = themed { CornerRadius(radius.xl) }
}) {

    private val titleText = Text().apply {
        width = fill()
        alignment = Anchor.Center
        fontSettings = themed { typography.caption }
        color = themed { palette.accent }
        padding = themed { Padding(gap.md) }
    }

    private val buttonLayout = LinearContainer().apply {
        orientation = LinearContainer.Orientation.Horizontal
        width = fill()
        padding = themed { Padding(gap.md) }
        spacing = themed { gap.sm }
    }

    /**
     * The title of the dialog.
     */
    var title by titleText::text


    init {
        detachOnHide = true

        card += titleText
        card += divider()
        card += innerContent
        card += divider()
        card += buttonLayout
    }


    /**
     * Adds a text button to the bottom of the dialog.
     */
    fun addButton(block: TextButton.() -> Unit) {
        addButton(TextButton().apply(block))
    }

    /**
     * Adds a button to the bottom of the dialog, buttons share the dialog width equally.
     */
    fun addButton(button: Button) {
        button.width = fill()
        buttonLayout += button
    }


    private fun divider() = Box().apply {
        width = fill()
        height = 2f.px
        color = themed { palette.divider }
    }
}
