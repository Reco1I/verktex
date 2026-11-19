package com.reco1l.verktex.ui.control

import com.reco1l.verktex.ui.InteractionState
import com.reco1l.verktex.ui.StateHighlight
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.ui.text.codePointCount
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.*
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Border
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.horizontalPadding
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.animatableColor
import com.reco1l.verktex.ui.animateColor
import com.reco1l.verktex.ui.transitionSlot
import com.reco1l.verktex.ui.IFocusable
import com.reco1l.verktex.ui.ITextInput
import com.reco1l.verktex.ui.Theme
import com.reco1l.verktex.ui.Icon
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.container.LinearContainer
import com.reco1l.verktex.ui.shape.Box
import com.reco1l.verktex.ui.text.Text
import kotlin.math.min
import com.reco1l.verktex.ui.themed

inline fun Container.textInput(builder: TextInput.() -> Unit): TextInput {
    return TextInput().apply(builder).also(::plusAssign)
}

/**
 * A single line text field. The text is typed with the platform's IME, which is attached while the
 * field is focused.
 */
open class TextInput(initialValue: String = "") : Control<String>(initialValue), IFocusable {

    private val leadingIconComponent = Icon().apply {
        anchor = Anchor.CenterLeft
        origin = Anchor.CenterLeft
        isVisible = false
    }

    private val trailingIconComponent = Icon().apply {
        anchor = Anchor.CenterLeft
        origin = Anchor.CenterLeft
        isVisible = false
    }

    // Text scrolled past the start is hidden here, so it's never drawn below the icons.
    private val textArea = Container().apply {
        width = fill()
        height = fill()
        clipping = ClipMode.Bounds
    }

    private val row = LinearContainer().apply {
        width = fill()
        height = fill()
    }

    private val placeholderText = Text().apply {
        anchor = Anchor.CenterLeft
        origin = Anchor.CenterLeft
        wrapText = false
        maxLines = 1
    }

    private val textComponent = Text().apply {
        anchor = Anchor.CenterLeft
        origin = Anchor.CenterLeft
        wrapText = false
    }

    private val caret = Box().apply {
        width = 2f.dip
        anchor = Anchor.CenterLeft
        origin = Anchor.CenterLeft
    }


    /**
     * The placeholder text displayed while the field is empty.
     */
    var placeholder by placeholderText::text

    /**
     * The icon displayed before the text. If set to null, no leading icon is displayed.
     */
    var leadingIcon: Text.IconLookup? = null
        set(value) {
            if (field == value) return
            field = value
            leadingIconComponent.setIcon(value)
        }

    /**
     * The icon displayed after the text. If set to null, no trailing icon is displayed.
     */
    var trailingIcon: Text.IconLookup? = null
        set(value) {
            if (field == value) return
            field = value
            trailingIconComponent.setIcon(value)
        }

    /**
     * The maximum number of characters allowed in the field. If set to 0, there is no limit.
     */
    var maxCharacters = 0

    /**
     * Whether the IME action (e.g. the enter key) confirms the input, calling [onConfirm] and
     * blurring the field.
     */
    var confirmOnEnter = true

    /**
     * Whether the text is a password, displayed masked and hidden from the IME's suggestions.
     */
    var isPassword = false
        set(value) {
            if (field != value) {
                field = value
                updateVisuals()
            }
        }

    /**
     * A callback that is invoked when the input is confirmed.
     */
    var onConfirm: (() -> Unit)? = null

    /**
     * Whether this field is currently focused and receiving text.
     */
    val isFocused
        get() = Verktex.focusedComponent === this

    override val canFocus = true


    private var isPressed = false

    override val interactionState
        get() = if (isPressed) InteractionState.Pressed else super.interactionState
    private var caretTime = 0f


    // A separate object since the value property is inherited from Control.
    private val imeTarget = object : ITextInput {

        override var value: String
            get() = this@TextInput.value
            set(value) { this@TextInput.value = value }

        override val maxCharacters
            get() = this@TextInput.maxCharacters

        override val isPassword
            get() = this@TextInput.isPassword

        override fun isCharacterAllowed(char: String) = this@TextInput.isCharacterAllowed(char)

        override fun isTextValid(text: String) = this@TextInput.isTextValid(text)

        override fun notifyInputError() = this@TextInput.notifyInputError()

        override fun onImeAction() {
            if (confirmOnEnter) {
                onConfirm?.invoke()
                blur()
            }
        }
    }


    init {
        height = themed { controlStyle.minimumHeight }
        padding = themed { horizontalPadding(gap.md) }
        radius = themed { CornerRadius(radius.md) }
        background = Background.Color(themed { palette.controlInactiveBackground })
        // Its color is animated when the focus changes.
        border = Border(1.5f.dip, borderTargetColor(resolvedTheme))

        placeholderText.color = themed { palette.accent.alpha(0.5f) }
        caret.color = themed { palette.accent }
        leadingIconComponent.color = themed { palette.accent }
        trailingIconComponent.color = themed { palette.accent }
        row.spacing = themed { controlStyle.iconSpacing }

        textArea += placeholderText
        textArea += textComponent
        textArea += caret

        row += leadingIconComponent
        row += textArea
        row += trailingIconComponent
        +row

        updateVisuals()
    }


    //region Focus

    /**
     * Focuses this field, showing the soft keyboard.
     */
    fun focus() = Verktex.requestFocus(this)

    /**
     * Removes the focus from this field, hiding the soft keyboard.
     */
    fun blur() {
        if (isFocused) Verktex.clearFocus()
    }

    override fun onFocusGained() {
        caretTime = 0f
        Verktex.host.attachTextInput(imeTarget)
        animateBorder()
    }

    override fun onFocusLost() {
        Verktex.host.detachTextInput()
        animateBorder()
    }

    //endregion

    //region Update

    private fun borderTargetColor(theme: Theme) =
        if (isFocused) theme.palette.accent else theme.palette.accent.alpha(0f)

    /**
     * The layer highlighting the field while hovered or pressed.
     */
    val highlight = StateHighlight(this)

    override fun drawOverBackground(renderer: Renderer) {
        highlight.draw(renderer, resolvedTheme.palette.accent)
    }

    override val tracksInteractionState
        get() = true

    override fun onInteractionStateChanged(state: InteractionState) {
        highlight.onStateChanged(state)
    }

    private val borderTransition = transitionSlot()

    private val borderColor = animatableColor(
        get = { border.color },
        set = { border = border.withColor(it) }
    )

    // Towards the color of the focus, from wherever it is (e.g. the red of an error).
    private fun animateBorder() {
        val target = borderTargetColor(resolvedTheme)
        borderTransition.run { animateColor(borderColor, target, 0.2f, Easing.OutQuint) }
    }

    override fun onUpdate(clock: FrameClock) {
        val contentWidth = textArea.bounds.width
        val textWidth = if (value.isEmpty()) 0f else textComponent.measuredSize.x
        val caretWidth = caret.measuredSize.x

        // Keeps the end of the text (where the caret is) visible when it overflows.
        val textX = min(0f, contentWidth - textWidth - caretWidth)
        textComponent.x = textX.px

        caretTime += clock.elapsedFrameTime
        caret.isVisible = isFocused && caretTime % 1f < 0.5f
        caret.x = (textX + textWidth).px
        caret.height = textComponent.measuredSize.y.px

        super.onUpdate(clock)
    }

    //endregion

    //region Input

    override fun onInputEvent(event: InputEvent): Boolean {
        if (event !is PointerEvent) {
            return super.onInputEvent(event)
        }

        return when (event.action) {
            PointerEvent.Action.Down -> {
                isPressed = true
                true
            }
            PointerEvent.Action.Up -> {
                if (!isPressed) return false
                isPressed = false
                if (containsScenePoint(event.x, event.y)) {
                    focus()
                }
                true
            }
            PointerEvent.Action.Unknown, PointerEvent.Action.Cancel -> {
                isPressed = false
                false
            }
            else -> isPressed
        }
    }

    //endregion

    //region Validation

    /**
     * Checks if a character is allowed to be typed in this field.
     */
    open fun isCharacterAllowed(char: String) = true

    /**
     * Checks whether a text is valid as a [value] for this field.
     */
    open fun isTextValid(text: String) = true

    /**
     * Called when the last typed text was rejected.
     */
    open fun notifyInputError() {
        // Flashes the border red, then it goes back to its normal color.
        border = border.withColor(ERROR_COLOR)
        animateBorder()
    }

    //endregion

    private fun Icon.setIcon(icon: Text.IconLookup?) {
        isVisible = icon != null
        if (icon != null) {
            iconCode = icon.code
            iconFamily = icon.family
        }
    }

    private fun updateVisuals() {
        textComponent.text = if (isPassword) "•".repeat(value.codePointCount()) else value
        placeholderText.isVisible = value.isEmpty()
    }

    override fun onValueChanged() {
        super.onValueChanged()
        updateVisuals()
    }


    companion object {
        private val ERROR_COLOR = Color4(0xFFF38BA8)
    }

}

/**
 * A [TextInput] whose [value] is constrained to a range of values.
 */
sealed class RangeConstrainedTextInput<T : Comparable<T>?>(
    initialValue: T?,

    /**
     * The minimum value that can be entered in this [RangeConstrainedTextInput].
     *
     * If set to `null`, there is no minimum value.
     */
    val minValue: T? = null,

    /**
     * The maximum value that can be entered in this [RangeConstrainedTextInput].
     *
     * If set to `null`, there is no maximum value.
     */
    val maxValue: T? = null,
) : TextInput(initialValue?.toString() ?: "") {
    override fun isTextValid(text: String): Boolean {
        // Avoid calling convertValue whenever necessary, in case it is expensive
        if (!super.isTextValid(text)) {
            return false
        }

        val minValue = minValue
        val maxValue = maxValue

        if (minValue == null && maxValue == null) {
            return true
        }

        val value = convertValue(text)

        return value == null || ((minValue == null || value >= minValue) && (maxValue == null || value <= maxValue))
    }

    /**
     * Converts a value from a [String] to a [T].
     *
     * @param value The value to convert.
     * @return The converted value. If `null`, [value] is always considered valid.
     */
    protected abstract fun convertValue(value: String): T?
}

/**
 * A [TextInput] that only allows [Int]s to be entered.
 */
class IntegerTextInput(
    initialValue: Int?,
    minValue: Int? = -Int.MAX_VALUE,
    maxValue: Int? = Int.MAX_VALUE,
) : RangeConstrainedTextInput<Int>(initialValue, minValue, maxValue) {
    override fun isCharacterAllowed(char: String) = super.isCharacterAllowed(char) && (char.all(Char::isDigit) || char == "-")

    override fun isTextValid(text: String) =
        // Check for underflow/overflow
        super.isTextValid(text) && (text.isEmpty() || text == "-" || text.toIntOrNull() != null)

    override fun convertValue(value: String) = value.toIntOrNull()
}

/**
 * A [TextInput] that only allows [Float]s to be entered.
 */
class FloatTextInput(
    initialValue: Float?,
    minValue: Float? = -Float.MAX_VALUE,
    maxValue: Float? = Float.MAX_VALUE,
) : RangeConstrainedTextInput<Float>(initialValue, minValue, maxValue) {
    override fun isCharacterAllowed(char: String) =
        super.isCharacterAllowed(char) && (char.all(Char::isDigit) || char == "." || char == "-")

    override fun isTextValid(text: String) =
        // Check for underflow/overflow
        super.isTextValid(text) && (text.isEmpty() || text == "-" || text.toFloatOrNull() != null)

    override fun convertValue(value: String) = value.toFloatOrNull()
}
