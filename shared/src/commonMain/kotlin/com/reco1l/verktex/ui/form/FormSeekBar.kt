package com.reco1l.verktex.ui.form

import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.ui.control.SeekBar
import com.reco1l.verktex.ui.text.Text
import kotlin.math.abs
import com.reco1l.verktex.ui.themed

/**
 * A form control with a slider below its label and its formatted value on the right.
 */
@Suppress("LeakingThis")
open class FormSeekBar(initialValue: Float = 0f) : FormControl<Float, SeekBar>(initialValue) {

    override val control = SeekBar(initialValue).apply {
        width = fill()
    }

    override val isDefault
        get() = abs(value - defaultValue) < 1e-6f

    override val valueText = Text().apply {
        anchor = Anchor.CenterRight
        origin = Anchor.CenterRight
        text = initialValue.toString()
        fontSettings = themed { typography.caption }
        padding = themed { Padding(gap.sm, gap.xs) }
        background = Background.Color(themed { palette.controlInactiveBackground })
        radius = themed { CornerRadius(radius.md) }
    }


    init {
        orientation = Orientation.Vertical

        +createHeader { +valueText }
        +control
    }
}
