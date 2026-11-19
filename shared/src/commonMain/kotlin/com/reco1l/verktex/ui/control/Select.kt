package com.reco1l.verktex.ui.control

import com.reco1l.verktex.ui.FAIcon
import com.reco1l.verktex.ui.Icon
import com.reco1l.verktex.ui.TextButton
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.ui.rotateTo
import com.reco1l.verktex.ui.transitionSlot
import com.reco1l.verktex.ui.text.Text
import com.reco1l.verktex.ui.textButton

/**
 * A dropdown menu that allows the user to select an option from a list.
 */
@Suppress("LeakingThis")
open class Select<T : Any>(initialValues: List<T> = emptyList()) : Control<List<T>>(initialValues) {

    private val dropdown = Dropdown(this)

    /**
     * The button that toggles the dropdown menu.
     */
    val button = textButton {
        width = fill()
        alignment = Anchor.CenterLeft
        onActionUp = {
            if (dropdown.isExpanded) {
                dropdown.hide()
            } else {
                dropdown.show()
            }
        }
    }

    val chevronIcon = Icon(FAIcon.ChevronDown).apply {
        anchor = Anchor.CenterRight
        origin = Anchor.CenterRight

        // Already thinted by the button
        color = Color4.White
    }

    /**
     * The options available in the dropdown menu.
     */
    var options = listOf<Option<T>>()
        set(value) {
            if (field != value) {
                field = value
                listChanged = true
            }
        }


    /**
     * The mode of selection for the dropdown menu.
     *
     * @see SelectionMode
     */
    var selectionMode = SelectionMode.Single

    /**
     * The text displayed on the button when no option is selected.
     */
    var placeholder: CharSequence = "Choose an option"
        set(value) {
            if (field != value) {
                field = value
                updateTrigger()
            }
        }


    private val chevronRotation = chevronIcon.transitionSlot()

    private var buttons = mapOf<Option<T>, TextButton>()

    private var listChanged = true


    init {
        // The button is already attached by its builder.
        updateTrigger()
        button += chevronIcon

        dropdown.onExpand = { rotateChevron(180f) }
        dropdown.onCollapse = { rotateChevron(0f) }
    }

    private fun rotateChevron(rotation: Float) {
        chevronRotation.run { rotateTo(rotation, 0.6f, Easing.OutBounce) }
    }

    override fun onDraw(renderer: Renderer) {
        if (listChanged) {
            listChanged = false
            onOptionsChanged()
        }

        super.onDraw(renderer)
    }

    open fun onOptionsChanged() {
        dropdown.clearButtons()
        buttons = mapOf()

        options.forEach { option ->

            dropdown.addButton {
                text = option.text
                if (option.color != null) color = option.color
                leadingIcon = option.leadingIcon
                trailingIcon = option.trailingIcon
                isSelected = option.value in value
                buttons += option to this

                onActionUp = {
                    when (selectionMode) {

                        SelectionMode.Single -> {
                            value = listOf(option.value)

                            dropdown.forEachButton { it.isSelected = it == this }
                            dropdown.hide()
                        }

                        SelectionMode.Multiple -> {
                            value = if (option.value in value) value - option.value else value + option.value
                        }
                    }
                }

            }

        }

        updateTrigger()
    }

    // Shows the selected options on the button, or the placeholder when there are none.
    private fun updateTrigger() {
        val selectedOptions = when (selectionMode) {
            SelectionMode.Single -> listOfNotNull(options.firstOrNull { it.value == value.firstOrNull() })
            SelectionMode.Multiple -> options.filter { it.value in value }
        }

        button.text = when (selectedOptions.size) {
            0 -> placeholder
            1 -> selectedOptions[0].text
            else -> selectedOptions.joinToString { it.text }
        }
    }

    override fun onValueChanged() {
        super.onValueChanged()

        updateTrigger()

        buttons.forEach { (option, button) ->
            button.isSelected = option.value in value
        }
    }

    /**
     * Represents an option in the dropdown menu.
     */
    data class Option<T : Any>(

        /**
         * The value associated with this option.
         */
        val value: T,

        /**
         * The text displayed for this option.
         */
        val text: CharSequence,

        /**
         * The color of the option text and icons.
         */
        val color: Color4? = null,

        /**
         * An optional icon displayed before the text.
         */
        val leadingIcon: Text.IconLookup? = null,

        /**
         * An optional icon displayed after the text.
         */
        val trailingIcon: Text.IconLookup? = null,
    )

    enum class SelectionMode {

        /**
         * Only one option can be selected at a time.
         */
        Single,

        /**
         * Multiple options can be selected at a time.
         */
        Multiple
    }
}
