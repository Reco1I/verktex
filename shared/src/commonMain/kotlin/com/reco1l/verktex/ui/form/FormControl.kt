package com.reco1l.verktex.ui.form

import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.ui.InteractionState
import com.reco1l.verktex.input.HoverEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.fadeTo
import com.reco1l.verktex.ui.moveToX
import com.reco1l.verktex.ui.transitionSlot
import com.reco1l.verktex.ui.FAIcon
import com.reco1l.verktex.ui.Button
import com.reco1l.verktex.ui.IconButton
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.container.LinearContainer
import com.reco1l.verktex.ui.control.Control
import com.reco1l.verktex.ui.text.Text
import com.reco1l.verktex.ui.themed

/**
 * A labeled row around a [control] used to change the value of a property, with a button to reset
 * it to its [defaultValue].
 */
@Suppress("LeakingThis", "MemberVisibilityCanBePrivate")
abstract class FormControl<V : Any, C : Control<V>>(initialValue: V) : LinearContainer() {

    /**
     * The control that is used to change the value.
     */
    abstract val control: C

    /**
     * The text showing the formatted [value], if the form control has one.
     */
    open val valueText: Text? = null

    /**
     * The text that is displayed as the label of the control.
     */
    val labelText = Text().apply {
        // Takes what the header's row leaves, wrapping when it's short, and pushes what follows to
        // the right. Stretched to its height, so it's centered next to the reset button.
        width = fill()
        height = fill()
        alignment = Anchor.CenterLeft
        fontSettings = themed { typography.caption }
    }

    /**
     * The button that resets the value of the control to its default value.
     */
    val resetButton = IconButton().apply {
        icon = FAIcon.RotateLeft
        density = Button.Density.Small
        variant = Button.Variant.Soft
        onActionUp = { value = defaultValue }
    }

    // Buttons animate their own alpha, so the reset button is faded through a holder. It always
    // takes its space, so a form as wide as its content (e.g. in a wrapped panel) doesn't widen
    // when it's shown, and it ignores input while hidden.
    private val resetButtonHolder = object : Container() {
        override fun onInputEvent(event: InputEvent) = isResetShown && super.onInputEvent(event)
    }.apply {
        // Centered with the label and what follows it, however many lines the label takes.
        anchor = Anchor.CenterLeft
        origin = Anchor.CenterLeft
        alpha = 0f
        translationX = RESET_BUTTON_OFFSET.dip
        +resetButton
    }


    /**
     * The key that is used to identify the control in a [FormContainer].
     */
    var key
        get() = control.key
        set(value) { control.key = value }

    /**
     * The value of the control.
     */
    var value
        get() = control.value
        set(value) { control.value = value }

    /**
     * The value the reset button sets the control back to.
     */
    var defaultValue = initialValue

    /**
     * The callback that is called when the value of the control changes.
     */
    var onValueChanged: ((V) -> Unit)? = null

    /**
     * The label text.
     */
    var label by labelText::text

    /**
     * Whether the control can be used. A disabled form control is faded and ignores touches.
     */
    var isEnabled = true
        set(value) {
            if (field != value) {
                field = value
                enabledTransition.run { fadeTo(if (value) 1f else DISABLED_ALPHA, 0.2f, Easing.OutQuint) }
            }
        }

    private val enabledTransition = transitionSlot()

    private val resetTransition = resetButtonHolder.transitionSlot()

    // Whether the reset button is shown or being shown.
    private var isResetShown = false

    override val interactionState
        get() = if (!isEnabled) InteractionState.Disabled else super.interactionState

    /**
     * The formatter of the [valueText].
     */
    var valueFormatter: (value: V) -> String = Any::toString
        set(formatter) {
            field = formatter
            valueText?.text = formatter(value)
        }

    /**
     * Whether to show the reset button while the value is not the default one.
     */
    var showResetButton = true

    /**
     * Whether the current [value] equals to [defaultValue].
     */
    protected open val isDefault
        get() = value == defaultValue


    init {
        width = fill()
        padding = themed { Padding(gap.sm) }
        spacing = themed { gap.sm }
    }


    /**
     * Creates the header of the form control: the label and the reset button on the left, and
     * [trailing] on the right. They're laid out in a row, so a header as wide as its content fits
     * all of them instead of overlapping them.
     */
    protected fun createHeader(trailing: Container.() -> Unit = {}) = LinearContainer().apply {
        width = fill()
        spacing = themed { gap.sm }

        +labelText
        +resetButtonHolder

        trailing()
    }


    /**
     * Called when the value of the [control] changes.
     */
    open fun onControlValueChanged() {
        valueText?.text = valueFormatter(value)
        onValueChanged?.invoke(value)
    }

    override fun onUpdate(clock: FrameClock) {
        // Checked every update as subclasses decide what the default is (isDefault is open).
        val showReset = showResetButton && !isDefault
        if (showReset != isResetShown) {
            isResetShown = showReset
            animateResetButton(showReset)
        }

        super.onUpdate(clock)
    }

    // The reset button slides in from the left while the value differs from the default.
    private fun animateResetButton(show: Boolean) {
        resetTransition.run {
            moveToX(if (show) 0f else RESET_BUTTON_OFFSET.dip.toPixels(), 0.1f, Easing.OutQuint)
            fadeTo(if (show) 1f else 0f, 0.1f, Easing.OutQuint)
        }
    }

    override fun onInputEvent(event: InputEvent): Boolean {
        // Disabled form controls swallow presses, so nothing below reacts to them either.
        if (!isEnabled) {
            // Hovered as a whole, so its control doesn't highlight.
            if (event is HoverEvent) {
                event.target = this
                return true
            }
            return event is PointerEvent && event.action == PointerEvent.Action.Down
        }
        return super.onInputEvent(event)
    }


    companion object {
        private const val DISABLED_ALPHA = 0.25f
        private const val RESET_BUTTON_OFFSET = -10f
    }
}
