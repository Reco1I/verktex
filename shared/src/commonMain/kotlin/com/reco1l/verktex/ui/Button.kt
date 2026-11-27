package com.reco1l.verktex.ui

import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.ui.container.ClickableContainer
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.data.Border
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.HoverEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Shadow
import com.reco1l.verktex.data.horizontalPadding
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.text.text

fun Container.button(builder: Button.() -> Unit) = Button().apply(builder).also { +it }

open class Button : ClickableContainer() {

    var density = Density.Medium
        set(value) {
            if (field != value) {
                field = value
                restyle()
            }
        }

    var variant: Variant = Variant.Surface
        set(value) {
            if (field != value) {
                field = value
                restyle()
            }
        }


    //region State

    /**
     * Whether the button is enabled or not. If disabled, the button will not process any
     * touch events.
     */
    var isEnabled = true

    /**
     * Whether the button is selected or not, e.g. the chosen option of a select. Selected buttons
     * are highlighted regardless of their [variant].
     */
    var isSelected = false
        set(value) {
            if (field != value) {
                field = value
                restyle()
            }
        }


    //endregion


    init {
        scaleOrigin = Anchor.Center

        background = themed {
            Background.Color(when {
                isSelected -> palette.controlActiveBackground
                variant == Variant.Solid -> palette.buttonBackgroundSolid
                variant == Variant.Soft -> palette.buttonBackgroundSoft
                variant == Variant.Surface -> palette.buttonBackgroundSurface
                else -> Color4.Transparent
            })
        }

        height = themed { buttonStyle[density].height }
        padding = horizontalPadding(themed { buttonStyle[density].horizontalPadding })
        radius = themed { CornerRadius(buttonStyle[density].radius) }
        border = themed {
            when (variant) {
                Variant.Solid, Variant.Soft, Variant.Surface -> buttonStyle.elevation.border
                Variant.Outline -> Border(1.5f.dip, palette.accent)
                else -> Border.None
            }
        }
        shadow = themed {
            when (variant) {
                Variant.Solid, Variant.Soft, Variant.Surface -> buttonStyle.elevation.shadow
                else -> Shadow.None
            }
        }
    }

    override val interactionState
        get() = if (!isEnabled) InteractionState.Disabled else super.interactionState

    /**
     * The layer highlighting the button while hovered or pressed, in its content color.
     */
    val highlight = StateHighlight(this)

    // Declared transitions replace the built-in scale and fade, the highlight stays.
    override val defaultTransitions
        get() = builtInTransitions

    override val tracksInteractionState
        get() = true

    override fun onInteractionStateChanged(state: InteractionState) {
        highlight.onStateChanged(state)
    }

    /**
     * The color of the content of the button, which the highlight is drawn in: the theme's
     * [Theme.ColorPalette.contentOnSolid] for [Variant.Solid] buttons, its accent otherwise.
     *
     * The button doesn't tint its content: its [color] is left white, so content added to a plain
     * [Button] colors itself, e.g. with this color. [TextButton] and [IconButton] set their [color]
     * to it instead, tinting their white text or icon, so `color` changes their content's color.
     */
    open val highlightColor: Color4
        get() = resolvedTheme.palette.let { if (variant == Variant.Solid) it.contentOnSolid else it.accent }

    override fun drawOverBackground(renderer: Renderer) {
        highlight.draw(renderer, highlightColor)
    }

    //region Touch

    override fun onInputEvent(event: InputEvent): Boolean {
        if (!isEnabled) {
            // Swallow presses but let up and cancel events reach other pressed components. It's
            // still hovered, its state is disabled.
            if (event is HoverEvent) return super.onInputEvent(event)
            return event is PointerEvent && event.action == PointerEvent.Action.Down
        }
        return super.onInputEvent(event)
    }


    private companion object {
        const val FEEDBACK_DURATION = 0.2f

        val builtInTransitions = StateTransitions().apply {
            idle {
                scaleTo(1f, FEEDBACK_DURATION, Easing.OutQuint)
                fadeTo(1f, FEEDBACK_DURATION, Easing.OutQuint)
            }
            pressed {
                scaleTo(0.95f, FEEDBACK_DURATION, Easing.OutQuint)
                fadeTo(1f, FEEDBACK_DURATION, Easing.OutQuint)
            }
            disabled {
                scaleTo(1f, FEEDBACK_DURATION, Easing.OutQuint)
                fadeTo(0.5f, FEEDBACK_DURATION, Easing.OutQuint)
            }
        }
    }


    /**
     * The variants of the button.
     */
    enum class Variant {
        Solid,
        Soft,
        Surface,
        Outline,
        Ghost
    }

    /**
     * The sizes of the button.
     */
    enum class Density {
        Small,
        Medium,
        Large
    }
}


