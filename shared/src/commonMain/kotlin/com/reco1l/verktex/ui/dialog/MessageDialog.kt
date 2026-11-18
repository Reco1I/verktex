package com.reco1l.verktex.ui.dialog

import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.ui.text.Text
import com.reco1l.verktex.ui.themed

/**
 * A dialog showing a text message.
 */
open class MessageDialog : Dialog<Text>(
    innerContent = Text().apply {
        width = fill()
        alignment = Anchor.TopCenter
        padding = themed { Padding(gap.lg) }
    }
) {

    /**
     * The text of the dialog.
     */
    var text by innerContent::text

}
