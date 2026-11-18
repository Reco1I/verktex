package com.reco1l.verktex.ui

import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.text.Text

fun Container.iconButton(builder: IconButton.() -> Unit) = IconButton().apply(builder).also { +it }

/**
 * A button that displays a text.
 */
open class IconButton : Button() {

    /**
     * The text component of the button.
     */
    protected val iconComponent = Icon().apply {
        width = fill()
        anchor = Anchor.Center
        origin = Anchor.Center

        iconSize = themed { buttonStyle[density].iconSize }
    }


    //region Delegation
    var icon by iconComponent::iconCode
    var family by iconComponent::iconFamily
    //endregion


    init {
        +iconComponent
        tintChildren = false
    }

}