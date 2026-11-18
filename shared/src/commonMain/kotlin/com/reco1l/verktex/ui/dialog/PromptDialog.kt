package com.reco1l.verktex.ui.dialog

import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.ui.container.LinearContainer
import com.reco1l.verktex.ui.control.TextInput
import com.reco1l.verktex.ui.text.Text
import com.reco1l.verktex.ui.themed

/**
 * A dialog asking for a text with [input], below an optional message.
 */
open class PromptDialog<T : TextInput>(val input: T) : Dialog<LinearContainer>(
    innerContent = LinearContainer().apply {
        orientation = LinearContainer.Orientation.Vertical
        width = fill()
        padding = themed { Padding(gap.lg) }
        spacing = themed { gap.md }
    }
) {

    private val messageText = Text().apply {
        width = fill()
        alignment = Anchor.TopCenter
        isVisible = false
    }

    /**
     * The message shown above the input, hidden while it's empty.
     */
    var text: CharSequence
        get() = messageText.text
        set(value) {
            messageText.text = value
            messageText.isVisible = value.isNotEmpty()
        }

    /**
     * The text entered.
     */
    var value by input::value


    init {
        input.width = fill()

        innerContent += messageText
        innerContent += input
    }


    override fun onShown() {
        super.onShown()
        input.focus()
    }
}
