package com.reco1l.verktex.testing

import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.fonts.Font
import com.reco1l.verktex.data.Background
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.shape.PathShape
import com.reco1l.verktex.math.Path
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Border
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.ui.FAIcon
import com.reco1l.verktex.ui.Component
import com.reco1l.verktex.ui.Proxy
import com.reco1l.verktex.ui.proxy
import com.reco1l.verktex.ui.Badge
import com.reco1l.verktex.ui.Button
import com.reco1l.verktex.ui.Scene
import com.reco1l.verktex.ui.shape.Box
import com.reco1l.verktex.ui.badge
import com.reco1l.verktex.ui.card
import com.reco1l.verktex.ui.container.LinearContainer.Orientation
import com.reco1l.verktex.ui.container.container
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.ui.control.Checkbox
import com.reco1l.verktex.ui.control.Select
import com.reco1l.verktex.ui.control.TabSelector
import com.reco1l.verktex.ui.control.seekBar
import com.reco1l.verktex.ui.control.textInput
import com.reco1l.verktex.ui.control.IntegerTextInput
import com.reco1l.verktex.ui.dialog.ConfirmDialog
import com.reco1l.verktex.ui.dialog.MessageDialog
import com.reco1l.verktex.ui.dialog.PromptDialog
import com.reco1l.verktex.ui.form.FormCheckbox
import com.reco1l.verktex.ui.form.FormContainer
import com.reco1l.verktex.ui.form.FormInput
import com.reco1l.verktex.ui.form.FormSeekBar
import com.reco1l.verktex.ui.gradientBox
import com.reco1l.verktex.ui.icon
import com.reco1l.verktex.ui.iconButton
import com.reco1l.verktex.ui.labeledBadge
import com.reco1l.verktex.ui.loadingIndicator
import com.reco1l.verktex.ui.rotateTo
import com.reco1l.verktex.ui.shape.arc
import com.reco1l.verktex.ui.shape.box
import com.reco1l.verktex.ui.shape.circle
import com.reco1l.verktex.ui.shape.line
import com.reco1l.verktex.ui.shape.triangle
import com.reco1l.verktex.ui.text.faSolidIcon
import com.reco1l.verktex.ui.text.text
import com.reco1l.verktex.ui.text.spans
import com.reco1l.verktex.ui.text.TextEffect
import com.reco1l.verktex.ui.text.TextGradient
import com.reco1l.verktex.ui.textButton
import com.reco1l.verktex.ui.themed
import com.reco1l.verktex.ui.transformSequence
import com.reco1l.verktex.ui.colorTo
import com.reco1l.verktex.ui.fadeTo
import com.reco1l.verktex.ui.scaleTo
import com.reco1l.verktex.ui.transitions
import com.reco1l.verktex.ui.container.clickableContainer
import com.reco1l.verktex.ui.toast.Toasts
import kotlin.math.roundToInt

/**
 * Tests for the components built into Verktex.
 */
object VerktexUITests : UITestSuite("Verktex") {

    init {
        test("Text") {
            column {
                val typography = com.reco1l.verktex.Verktex.theme.typography

                section("Typography") {
                    orientation = Orientation.Vertical
                    text { text = "Heading 1"; fontSettings = typography.h1 }
                    text { text = "Heading 2"; fontSettings = typography.h2 }
                    text { text = "Heading 3"; fontSettings = typography.h3 }
                    text { text = "Body text"; fontSettings = typography.body }
                    text { text = "Small body text"; fontSettings = typography.smallBody }
                    text { text = "Caption"; fontSettings = typography.caption }
                }

                section("Icons") {
                    text { text = "Leading"; leadingIcon = faSolidIcon(FAIcon.Star) }
                    text { text = "Trailing"; trailingIcon = faSolidIcon(FAIcon.Heart) }
                    icon { iconCode = FAIcon.Gear }
                    icon { iconCode = FAIcon.Music }
                }

                section("Wrapping") {
                    text {
                        width = 300f.dip
                        text = "A long paragraph that should wrap inside a fixed width of three hundred " +
                            "density independent pixels, breaking between words."
                    }
                    text {
                        width = 300f.dip
                        maxLines = 1
                        text = "A single line that is cut once it reaches the end of its width."
                    }
                }
            }
        }

        test("Text effects") {
            column {
                val gold = Color4("#FFCC22")

                fun Container.sample(label: String, size: Float = 56f, block: com.reco1l.verktex.ui.text.Text.() -> Unit) {
                    linearContainer {
                        orientation = Orientation.Vertical
                        spacing = 4f.dip
                        text { text = label; color = Color4(1f, 1f, 1f, 0.6f) }
                        text {
                            text = "SS 98.7%"
                            fontFamily = BuiltIn.Fonts.Fredoka
                            fontWeight = Font.Weight.Bold
                            fontSize = size.dip
                            color = Color4.White
                            // Room for the effects, which don't change the measured size.
                            padding = Padding(12f.dip)
                            block()
                        }
                    }
                }

                section("Each effect") {
                    sample("Shadow") { effects = listOf(TextEffect.Shadow(offsetY = 4f.dip)) }
                    sample("Soft shadow") { effects = listOf(TextEffect.Shadow(Color4(0f, 0f, 0f, 0.8f), offsetY = 4f.dip, softness = 6f.dip)) }
                    sample("Outline") { effects = listOf(TextEffect.Outline(Color4("#2B1A00"), 3f.dip)) }
                    sample("Glow") { effects = listOf(TextEffect.Glow(gold, 14f.dip)) }
                    sample("Gradient") { fillGradient = TextGradient(Color4("#FFF3B0"), gold) }
                }

                section("Combined") {
                    sample("All of them", 96f) {
                        fillGradient = TextGradient(Color4("#FFF3B0"), gold)
                        effects = listOf(
                            TextEffect.Shadow(Color4(0f, 0f, 0f, 0.6f), offsetY = 6f.dip, softness = 8f.dip),
                            TextEffect.Glow(gold, 20f.dip),
                            TextEffect.Outline(Color4("#2B1A00"), 3f.dip),
                        )
                    }

                    // The glow pulses through effectsAlpha.
                    sample("Pulsing glow", 96f) {
                        color = Color4("#88DA20")
                        effects = listOf(TextEffect.Glow(Color4("#88DA20"), 24f.dip, 1.5f))
                        var time = 0f
                        onEachFrame {
                            time += it.elapsedFrameTime
                            effectsAlpha = 0.6f + 0.4f * sin(time * 3f)
                        }
                    }
                }

                section("Small sizes") {
                    for (size in listOf(14f, 20f, 28f)) {
                        sample("$size dip", size) {
                            effects = listOf(TextEffect.Shadow(offsetY = 1f.dip), TextEffect.Outline(Color4.Black, 1f.dip))
                        }
                    }
                }
            }
        }

        test("Variable fonts") {
            column {
                // Nunito and Fredoka are variable on their weight, Anta is static and keeps its own.
                section("Weights") {
                    for ((name, family) in listOf("Nunito" to BuiltIn.Fonts.Default, "Fredoka" to BuiltIn.Fonts.Fredoka, "Anta" to BuiltIn.Fonts.Anta)) {
                        linearContainer {
                            orientation = Orientation.Vertical
                            spacing = 4f.dip
                            text { text = name; color = Color4(1f, 1f, 1f, 0.6f) }
                            for (weight in 100..900 step 100) {
                                text {
                                    text = "Weight $weight"
                                    fontFamily = family
                                    fontWeight = Font.Weight(weight)
                                }
                            }
                        }
                    }
                }

                // Anta has a single upright weight and Nunito no italics: what they lack is synthesised.
                section("Synthesised bold and italic") {
                    for ((name, family) in listOf("Anta" to BuiltIn.Fonts.Anta, "Nunito" to BuiltIn.Fonts.Default)) {
                        linearContainer {
                            orientation = Orientation.Vertical
                            spacing = 4f.dip
                            text { text = name; color = Color4(1f, 1f, 1f, 0.6f) }
                            for ((label, weight, style) in listOf(
                                Triple("Regular", Font.Weight.Normal, Font.Style.Normal),
                                Triple("Bold", Font.Weight.Bold, Font.Style.Normal),
                                Triple("Italic", Font.Weight.Normal, Font.Style.Italic),
                                Triple("Bold italic", Font.Weight.Bold, Font.Style.Italic),
                            )) {
                                text {
                                    text = label
                                    fontFamily = family
                                    fontWeight = weight
                                    fontStyle = style
                                }
                            }
                        }
                    }
                }

                section("Any weight") {
                    orientation = Orientation.Vertical
                    val sample = text {
                        text = "The quick brown fox (400)"
                        fontSize = 24f.dip
                        fontWeight = Font.Weight.Normal
                    }
                    seekBar {
                        width = 300f.dip
                        min = Font.Weight.MIN.toFloat()
                        max = Font.Weight.MAX.toFloat()
                        step = 50f
                        value = Font.Weight.Normal.value.toFloat()
                        onValueChange = {
                            val weight = Font.Weight(it.roundToInt())
                            sample.fontWeight = weight
                            sample.text = "The quick brown fox (${weight.value})"
                        }
                    }
                }
            }
        }

        test("Spanned text") {
            column {
                section("Styles") {
                    orientation = Orientation.Vertical
                    text {
                        spans {
                            +"Plain, "
                            color(Color4.Red400) { +"colored, " }
                            bold { +"bold, " }
                            italic { +"italic, " }
                            bold { italic { +"both, " } }
                            size(24f.dip) { +"bigger, " }
                            background(Color4(0.3f, 0.3f, 0.6f)) { +" highlighted " }
                            +" and "
                            font(com.reco1l.verktex.Verktex.theme.typography.h3) { +"another font" }
                        }
                    }
                }

                section("Icons") {
                    orientation = Orientation.Vertical
                    text {
                        spans {
                            icon(FAIcon.Star)
                            space(6f.dip)
                            bold { +"5.00" }
                        }
                    }
                    text {
                        spans {
                            +"Icons "
                            color(Color4.Red400) { icon(FAIcon.Heart) }
                            +" in the middle "
                            icon(FAIcon.Music)
                            +" of a text"
                        }
                    }
                    text {
                        spans {
                            size(32f.dip) { icon(FAIcon.CircleInfo) }
                            space(12f.dip)
                            +"A big icon centered with the text"
                        }
                    }
                    text {
                        text = "Leading and trailing icons"
                        leadingIcon = faSolidIcon(FAIcon.ArrowLeft)
                        trailingIcon = faSolidIcon(FAIcon.ArrowRight)
                        iconSpacing = 8f.dip
                    }
                }

                section("Mixed sizes share a baseline") {
                    text {
                        spans {
                            size(32f.dip) { bold { +"128" } }
                            size(14f.dip) { color(Color4(1f, 1f, 1f, 0.6f)) { +" ms" } }
                        }
                    }
                }

                section("Wrapping across spans") {
                    orientation = Orientation.Vertical
                    text {
                        width = 300f.dip
                        spans {
                            +"A paragraph with "
                            bold { +"bold words" }
                            +" and "
                            color(Color4.Red400) { +"colored ones" }
                            +" that wraps between words, even "
                            icon(FAIcon.Star)
                            +" around icons and "
                            size(22f.dip) { +"bigger text" }
                            +" inside a fixed width."
                        }
                    }
                    text {
                        width = 300f.dip
                        maxLines = 2
                        spans {
                            bold { +"Truncated: " }
                            +"a long text limited to two lines that ends with an ellipsis once it doesn't fit anymore."
                        }
                    }
                }
            }
        }

        test("Buttons") {
            column {
                for (density in Button.Density.entries) {
                    section("Density: $density") {
                        for (variant in Button.Variant.entries) {
                            textButton {
                                text = variant.name
                                this.variant = variant
                                this.density = density
                                onActionUp = { log("Clicked $variant ($density)") }
                            }
                        }
                    }
                }

                section("States") {
                    textButton { text = "Disabled"; isEnabled = false }
                    textButton { text = "Selected"; isSelected = true }
                    textButton { text = "Leading icon"; leadingIcon = faSolidIcon(FAIcon.Play) }
                    textButton { text = "Trailing icon"; trailingIcon = faSolidIcon(FAIcon.Plus) }
                    textButton {
                        text = "Long press"
                        onActionLongPress = { log("Long pressed") }
                    }
                }

                section("Icon buttons") {
                    for (variant in Button.Variant.entries) {
                        iconButton {
                            icon = FAIcon.Gear
                            this.variant = variant
                            onActionUp = { log("Clicked icon $variant") }
                        }
                    }
                }
            }
        }

        test("Badges") {
            column {
                for (density in Badge.Density.entries) {
                    section("Density: $density") {
                        for (variant in Badge.Variant.entries) {
                            badge {
                                text = variant.name
                                this.variant = variant
                                this.density = density
                            }
                        }
                        badge {
                            text = "Icon"
                            leadingIcon = faSolidIcon(FAIcon.Star)
                            this.density = density
                        }
                    }
                }

                section("Labeled") {
                    for (variant in Badge.Variant.entries) {
                        labeledBadge {
                            label = "Label"
                            value = variant.name
                            this.variant = variant
                        }
                    }
                }
            }
        }

        test("Controls") {
            column {
                section("Checkbox") {
                    +Checkbox().apply { onValueChange = { log("Checkbox: $it") } }
                    +Checkbox(true).apply { onValueChange = { log("Checkbox: $it") } }
                }

                section("Slider") {
                    orientation = Orientation.Vertical
                    seekBar {
                        width = 300f.dip
                        value = 0.5f
                        onValueChange = { log("Slider: $it") }
                    }
                    seekBar {
                        width = 300f.dip
                        min = -10f
                        max = 10f
                        step = 1f
                        onValueChange = { log("Stepped slider: $it") }
                    }
                }

                section("Text input") {
                    orientation = Orientation.Vertical
                    textInput {
                        width = 300f.dip
                        onValueChange = { log("Input: $it") }
                    }
                    textInput {
                        width = 300f.dip
                        placeholder = "Search..."
                        trailingIcon = faSolidIcon(FAIcon.MagnifyingGlass)
                        onValueChange = { log("Search: $it") }
                    }
                    textInput {
                        width = 300f.dip
                        placeholder = "Username"
                        leadingIcon = faSolidIcon(FAIcon.User)
                        trailingIcon = faSolidIcon(FAIcon.Xmark)
                    }
                }

                section("Select") {
                    +Select<String>().apply {
                        width = 220f.dip
                        options = listOf(
                            Select.Option("easy", "Easy"),
                            Select.Option("normal", "Normal"),
                            Select.Option("hard", "Hard", leadingIcon = faSolidIcon(FAIcon.Star)),
                        )
                        onValueChange = { log("Select: $it") }
                    }
                    +Select<String>().apply {
                        width = 220f.dip
                        selectionMode = Select.SelectionMode.Multiple
                        placeholder = "Multiple"
                        options = listOf("A", "B", "C", "D").map { Select.Option(it, it) }
                        onValueChange = { log("Multi select: $it") }
                    }
                }

                section("Tab selector") {
                    +TabSelector().apply {
                        for (name in listOf("General", "Audio", "Graphics")) {
                            addButton(name) { log("Tab: $name") }
                        }
                        selectedTab = 0
                    }
                }
            }
        }

        test("Forms") {
            column {
                +FormContainer().apply {
                    width = 400f.dip
                    orientation = Orientation.Vertical

                    +FormCheckbox(true).apply { label = "Checkbox"; key = "checkbox" }
                    +FormSeekBar(0.5f).apply { label = "Slider"; key = "slider" }
                    +FormInput("Text").apply { label = "Input"; key = "input" }

                    onSubmit = { log("Submitted: $it") }
                }
            }
        }

        test("Cards") {
            column {
                card {
                    width = 400f.dip
                    title = "Card"
                    content { text { text = "The content of a card." } }
                }
                card {
                    width = 400f.dip
                    title = "Collapsible card"
                    isCollapsible = true
                    onExpandStatusChange = { log("Expanded: $it") }
                    content {
                        text { text = "Tap the title to collapse it." }
                        textButton { text = "A button" }
                    }
                }
            }
        }

        test("Dialogs") {
            column {
                section("Dialogs") {
                    textButton {
                        text = "Message"
                        onActionUp = {
                            MessageDialog().apply {
                                title = "Message"
                                text = "A message dialog with a single button."
                                addButton { text = "OK"; onActionUp = { hide() } }
                            }.show()
                        }
                    }
                    textButton {
                        text = "Confirm"
                        onActionUp = {
                            ConfirmDialog().apply {
                                title = "Confirm"
                                text = "Do you want to continue?"
                                onConfirm = { log("Confirmed") }
                                onCancel = { log("Cancelled") }
                            }.show()
                        }
                    }
                    textButton {
                        text = "Prompt"
                        onActionUp = {
                            PromptDialog(IntegerTextInput(120, 30, 300)).apply {
                                title = "BPM"
                                text = "A prompt dialog asking for a number."
                                addButton { text = "OK"; onActionUp = { log("Entered: $value"); hide() } }
                                addButton { text = "Cancel"; onActionUp = { hide() } }
                            }.show()
                        }
                    }
                }
            }
        }

        test("Toasts") {
            // Shown right away, so a screenshot of the test has a toast.
            Toasts.show("A short message.", Toasts.Long, title = "A title")

            column {
                section("Toasts") {
                    textButton {
                        text = "Short"
                        onActionUp = { Toasts.show("A short message.") }
                    }
                    textButton {
                        text = "Long"
                        onActionUp = { Toasts.show("A longer message that stays on screen a bit more, and wraps when it doesn't fit in one line.", Toasts.Long) }
                    }
                    textButton {
                        text = "With a title"
                        onActionUp = { Toasts.show("A message with a title above it.", Toasts.Short, title = "A title") }
                    }
                    textButton {
                        // One at a time: the others wait their turn.
                        text = "Three in a row"
                        onActionUp = { (1..3).forEach { Toasts.show("Message $it of 3.") } }
                    }
                }
            }
        }

        test("Shapes") {
            column {
                section("Shapes") {
                    box { width = 80f.dip; height = 80f.dip; color = Color4.Red400 }
                    box { width = 80f.dip; height = 80f.dip; radius = CornerRadius(16f.dip); color = Color4.Green400 }
                    circle { width = 80f.dip; height = 80f.dip; color = Color4.Blue400 }
                    circle { width = 80f.dip; height = 80f.dip; endAngle = 270f; color = Color4.Yellow400 }
                    triangle { width = 80f.dip; height = 80f.dip; color = Color4.Purple400 }
                    // Skewed around its center, the bounds and the layout stay those of the box.
                    box { width = 160f.dip; height = 80f.dip; skewX = -15f; radius = CornerRadius(12f.dip); color = Color4.Orange400 }
                }

                section("Lines") {
                    container {
                        width = 200f.dip
                        height = 80f.dip
                        line { fromPoint = Vec2(0f, 0f); toPoint = Vec2(200f, 80f); lineWidth = 2f.dip }
                        line { fromPoint = Vec2(0f, 80f); toPoint = Vec2(200f, 0f); lineWidth = 6f.dip }
                    }
                }

                section("Gradients") {
                    for (angle in listOf(0f, 45f, 90f)) {
                        gradientBox {
                            width = 120f.dip
                            height = 80f.dip
                            gradientAngle = angle
                            colorStart = Color4.Pink400
                            colorEnd = Color4.Blue600
                        }
                    }
                }

                section("Borders") {
                    container {
                        width = 120f.dip
                        height = 80f.dip
                        radius = CornerRadius(12f.dip)
                        border = Border(2f.dip, Color4.White)
                    }
                }

                section("Arcs") {
                    arc { width = 80f.dip; height = 80f.dip; thickness = 8f.dip.toPixels(); color = Color4.Blue400 }
                    arc { width = 80f.dip; height = 80f.dip; thickness = 8f.dip.toPixels(); progress = 0.6f; color = Color4.Green400 }
                    arc { width = 80f.dip; height = 80f.dip; thickness = 12f.dip.toPixels(); progress = 0.6f; roundedCaps = true; color = Color4.Yellow400 }
                    arc { width = 80f.dip; height = 80f.dip; thickness = 4f.dip.toPixels(); startAngle = 180f; progress = -0.3f; roundedCaps = true; color = Color4.Pink400 }

                    // Fills and empties, with round caps.
                    val looping = arc { width = 80f.dip; height = 80f.dip; thickness = 10f.dip.toPixels(); roundedCaps = true; color = Color4.Orange400 }
                    var time = 0f
                    onEachFrame { clock ->
                        time = (time + clock.elapsedFrameTime) % 2f
                        looping.progress = if (time < 1f) time else 2f - time
                    }
                }
            }
        }

        test("Color scaling") {
            column {
                val factors = listOf(1.2f, 1f, 0.93f, 0.87f, 0.8f, 0.72f, 0.65f, 0.5f, 0.37f, 0.3f, 0.24f, 0.17f, 0.09f)

                fun Container.ramp(title: String, accent: Color4) = section(title) {
                    for (factor in factors) {
                        linearContainer {
                            orientation = Orientation.Vertical
                            spacing = 4f.dip
                            box {
                                width = 64f.dip
                                height = 64f.dip
                                radius = CornerRadius(8f.dip)
                                color = accent.scale(factor)
                            }
                            text { text = "$factor"; color = Color4(1f, 1f, 1f, 0.6f) }
                        }
                    }
                }

                val palette = com.reco1l.verktex.Verktex.theme.palette
                section("Theme palette") {
                    for ((name, color) in listOf(
                        "inset" to palette.inset,
                        "surface" to palette.surface,
                        "surfaceRaised" to palette.surfaceRaised,
                        "surfaceHigh" to palette.surfaceHigh,
                        "surfaceBright" to palette.surfaceBright,
                        "divider" to palette.divider,
                        "border" to palette.border,
                        "contentSubtle" to palette.contentSubtle,
                        "contentMuted" to palette.contentMuted,
                        "contentTertiary" to palette.contentTertiary,
                        "contentSecondary" to palette.contentSecondary,
                        "contentPrimary" to palette.contentPrimary,
                    )) {
                        linearContainer {
                            orientation = Orientation.Vertical
                            spacing = 4f.dip
                            box {
                                width = 64f.dip
                                height = 64f.dip
                                radius = CornerRadius(8f.dip)
                                this.color = color
                            }
                            text { text = name; this.color = Color4(1f, 1f, 1f, 0.6f) }
                        }
                    }
                }

                ramp("Theme accent", palette.accent)
                ramp("Default accent", com.reco1l.verktex.ui.Theme.DEFAULT_ACCENT_COLOR)
                for (hue in listOf(25f, 90f, 150f, 210f, 300f)) {
                    ramp("Pastel, hue $hue", com.reco1l.verktex.data.oklch(0.85f, 0.09f, hue))
                }
                ramp("Saturated red", Color4.Red500)
                ramp("Gray", Color4(0.8f, 0.8f, 0.8f))
            }
        }

        test("Borders and corners") {
            fun Container.panel(radius: CornerRadius, border: Border = Border.None) = container {
                width = 140f.dip
                height = 90f.dip
                background = Background.Color(Color4(0.2f, 0.22f, 0.3f))
                this.radius = radius
                this.border = border
            }

            val red = Color4.Red400
            val green = Color4.Green400
            val blue = Color4.Blue400
            val yellow = Color4.Yellow400

            column {
                section("Corners") {
                    panel(CornerRadius(30f.dip, 0f.dip, 30f.dip, 0f.dip))
                    panel(CornerRadius.top(30f.dip))
                    panel(CornerRadius(8f.dip, 30f.dip, 8f.dip, 0f.dip))
                    panel(CornerRadius(1000f.dip))
                }

                section("Sides") {
                    panel(CornerRadius(8f.dip), Border(6f.dip, top = red, right = green, bottom = blue, left = yellow))
                    panel(CornerRadius(30f.dip, 0f.dip, 30f.dip, 0f.dip), Border(6f.dip, top = red, right = green, bottom = blue, left = yellow))
                    panel(CornerRadius(8f.dip), Border(6f.dip, top = Color4.Transparent, right = Color4.Transparent, bottom = Color4.White, left = Color4.Transparent))
                }

                section("Fading sides") {
                    // Left and right fade from the top color to the bottom one, the gradient of a bezel.
                    panel(CornerRadius(8f.dip), Border(6f.dip, top = red, bottom = blue))
                    // Top and bottom fade from left to right.
                    panel(CornerRadius(8f.dip), Border(6f.dip, right = green, left = yellow))
                    // Only the left side fades, between the top and bottom colors.
                    panel(CornerRadius(8f.dip), Border(6f.dip, top = red, right = green, bottom = blue))
                }

                section("Themed") {
                    container {
                        width = 140f.dip
                        height = 90f.dip
                        background = Background.Color(themed { palette.surface })
                        radius = themed { CornerRadius.top(radius.xl) }
                        border = themed { buttonStyle.elevation.border }
                    }
                }
            }
        }

        test("Clipping") {
            // Content larger than its clipping container, drawn by several shaders: a gradient (color),
            // a triangle (quad), boxes and text (UI).
            fun Container.overflowingContent() {
                gradientBox {
                    width = 220f.dip
                    height = 160f.dip
                    x = (-30f).dip
                    y = (-30f).dip
                    gradientAngle = 45f
                    colorStart = Color4.Pink400
                    colorEnd = Color4.Blue600
                }
                triangle { width = 90f.dip; height = 90f.dip; x = 90f.dip; y = 40f.dip; color = Color4.Yellow400 }
                box { width = 60f.dip; height = 60f.dip; x = (-20f).dip; y = 60f.dip; color = Color4.Green400 }
                text { text = "Clipped text that overflows"; x = 8f.dip; y = 8f.dip }
            }

            fun Container.clipped(builder: Container.() -> Unit) = container {
                width = 160f.dip
                height = 100f.dip
                clipping = ClipMode.Bounds
                background = Background.Color(Color4(0.2f, 0.22f, 0.3f))
                overflowingContent()
                builder()
            }

            column {
                section("Scissor and rounded corners") {
                    clipped { }
                    clipped { radius = CornerRadius(24f.dip) }
                    clipped { radius = CornerRadius(40f.dip, 0f.dip, 40f.dip, 0f.dip) }
                }

                section("Transformed") {
                    clipped { rotationZ = 12f }
                    clipped { skewX = -15f; radius = CornerRadius(16f.dip) }
                    clipped { scale = 0.8f; rotationZ = -20f; radius = CornerRadius(1000f.dip) }
                }

                section("Padding and nesting") {
                    clipped {
                        clipping = ClipMode.Padding
                        padding = Padding(12f.dip)
                        radius = CornerRadius(24f.dip)
                    }

                    // The inner clip is applied by the shaders, the outer one by its bounding box.
                    clipped {
                        radius = CornerRadius(24f.dip)
                        container {
                            width = 100f.dip
                            height = 70f.dip
                            x = 30f.dip
                            y = 15f.dip
                            clipping = ClipMode.Bounds
                            radius = CornerRadius(16f.dip)
                            rotationZ = 10f
                            overflowingContent()
                        }
                    }
                }
            }
        }

        test("Paths") {
            // A wave and a loop crossing itself, in the colours of a slider body.
            fun wave() = Path.Builder().apply {
                for (i in 0..40) add(i * 6f, 40f + sin(i / 40f * 2f * PI.toFloat()) * 30f)
            }.build()

            fun loop() = Path.Builder().apply {
                for (i in 0..60) {
                    val t = i / 60f * 2f * PI.toFloat()
                    add(110f + cos(t) * 70f - t * 12f, 50f + sin(t) * 40f)
                }
            }.build()

            fun Container.path(path: Path, block: PathShape.() -> Unit = {}) = PathShape().apply {
                this.path = path
                halfWidth = 14f
                borderWidth = 2.5f
                hintWidth = 4f
                bodyColor = Color4.Pink400.alpha(0.7f)
                borderColor = Color4.White
                hintColor = Color4.Pink200.alpha(0.5f)
                block()
            }.also { this += it }

            fun Container.pathCell(block: Container.() -> Unit) = container {
                width = 260f.dip
                height = 110f.dip
                padding = Padding(12f.dip)
                block()
            }

            val snakingIn = mutableListOf<PathShape>()
            val snakingOut = mutableListOf<PathShape>()
            val snakingBoth = mutableListOf<PathShape>()

            column {
                section("Static") {
                    pathCell { path(wave()) }
                    pathCell { path(loop()) { hintWidth = 0f } }
                    pathCell { path(Path.of(20f, 40f)) }
                    pathCell { path(Path.of(0f, 40f, 200f, 40f)) { borderWidth = 14f } }
                }

                section("Gradient and shadow") {
                    pathCell {
                        path(wave()) {
                            hintWidth = 0f
                            shadowWidth = 3f
                            bodyColor = Color4.Pink600.alpha(0.7f)
                            bodyCenterColor = Color4.Pink200.alpha(0.7f)
                        }
                    }
                    pathCell {
                        path(loop()) {
                            hintWidth = 0f
                            shadowWidth = 3f
                            bodyColor = Color4.Blue600.alpha(0.7f)
                            bodyCenterColor = Color4.Blue200.alpha(0.7f)
                        }
                    }
                }

                section("Snaking") {
                    pathCell { snakingIn += path(wave()) }
                    pathCell { snakingOut += path(wave()) }
                    pathCell { snakingBoth += path(loop()) }
                }
            }

            var time = 0f
            onEachFrame { clock ->
                time = (time + clock.elapsedFrameTime) % 2f
                val progress = min(1f, time)

                snakingIn.forEach { it.endLength = it.length * progress }
                snakingOut.forEach { it.startLength = it.length * progress }
                snakingBoth.forEach {
                    it.startLength = it.length * max(0f, progress - 0.4f)
                    it.endLength = it.length * min(1f, progress + 0.2f)
                }
            }
        }

        test("Loading indicators") {
            column {
                section("Sizes") {
                    loadingIndicator { width = 24f.dip; height = 24f.dip }
                    loadingIndicator { width = 48f.dip; height = 48f.dip; thickness = 5f.dip }
                    loadingIndicator { width = 96f.dip; height = 96f.dip; thickness = 8f.dip; arcColor = Color4.Pink400 }
                }
            }
        }

        test("Transforms") {
            val target = container {
                anchor = Anchor.Center
                origin = Anchor.Center
                width = 100f.dip
                height = 100f.dip
                radius = CornerRadius(12f.dip)
                background = Background.Color(Color4.Pink400)
            }

            linearContainer {
                anchor = Anchor.BottomCenter
                origin = Anchor.BottomCenter
                y = (-16f).dip
                spacing = 8f.dip

                textButton {
                    text = "Scale"
                    onActionUp = {
                        target.transformSequence {
                            scaleTo(1.5f, 0.3f, Easing.OutBack)
                            scaleTo(1f, 0.3f, Easing.OutQuad)
                        }
                    }
                }
                textButton {
                    text = "Rotate"
                    onActionUp = { target.rotateTo(target.rotationZ + 90f, 0.4f, Easing.OutQuint) }
                }
                textButton {
                    text = "Move"
                    onActionUp = {
                        target.transformSequence {
                            moveToX(-100f, 0.3f, Easing.OutQuad)
                            moveToX(100f, 0.6f, Easing.InOutQuad)
                            moveToX(0f, 0.3f, Easing.InQuad)
                        }
                    }
                }
            }
        }

        test("Proxies") {
            // Each card owns a dot overlapping the next card, which draws over it unless the dot is proxied into
            // the layer above every card.
            val colors = listOf(Color4.Pink400, Color4(0.4f, 0.6f, 1f), Color4(0.4f, 0.85f, 0.6f), Color4(1f, 0.75f, 0.3f))
            val dots = mutableListOf<Component>()

            val cards = container {
                anchor = Anchor.Center
                origin = Anchor.Center

                colors.forEachIndexed { i, cardColor ->
                    container {
                        x = (i * 100f).dip
                        width = 120f.dip
                        height = 120f.dip
                        radius = CornerRadius(12f.dip)
                        background = Background.Color(cardColor)

                        dots += circle {
                            anchor = Anchor.CenterRight
                            origin = Anchor.Center
                            width = 36f.dip
                            height = 36f.dip
                            color = Color4.White
                        }
                    }
                }
            }

            val above = container {
                width = fill()
                height = fill()
            }
            var proxies = emptyList<Proxy>()

            fun setProxied(proxied: Boolean) {
                proxies.forEach { above -= it }
                proxies = if (proxied) dots.map { above.proxy(it) } else emptyList()
                log(if (proxied) "Dots drawn above every card" else "Dots drawn in their card")
            }
            setProxied(true)

            linearContainer {
                anchor = Anchor.BottomCenter
                origin = Anchor.BottomCenter
                y = (-16f).dip
                spacing = 8f.dip

                textButton {
                    text = "Toggle proxies"
                    onActionUp = { setProxied(proxies.isEmpty()) }
                }
                textButton {
                    text = "Fade cards"
                    onActionUp = { cards.fadeTo(if (cards.alpha > 0.5f) 0.3f else 1f, 0.3f) }
                }
            }
        }

        test("Hover and transitions") {
            column {
                section("Buttons") {
                    textButton {
                        text = "Grows on hover"
                        transitions {
                            idle { scaleTo(1f, 0.15f, Easing.OutQuad) }
                            hovered { scaleTo(1.08f, 0.15f, Easing.OutQuad) }
                            pressed { scaleTo(0.94f, 0.08f, Easing.OutQuad) }
                        }
                        onActionUp = { log("Clicked") }
                    }
                    textButton {
                        text = "Built-in feedback"
                        interaction.onHoverEnter = { log("Hover enter") }
                        interaction.onHoverExit = { log("Hover exit") }
                    }
                    textButton {
                        text = "Disabled"
                        isEnabled = false
                        transitions {
                            idle { fadeTo(1f, 0.2f) }
                            disabled { fadeTo(0.3f, 0.2f) }
                        }
                    }
                }

                section("Containers") {
                    for (i in 0 until 3) {
                        clickableContainer {
                            width = 96f.dip
                            height = 96f.dip
                            radius = CornerRadius(12f.dip)
                            background = Background.Color(Color4.Pink400)
                            transitions {
                                idle { rotateTo(0f, 0.3f, Easing.OutBack); colorTo(Color4.White, 0.2f) }
                                hovered { rotateTo(8f, 0.3f, Easing.OutBack); colorTo(Color4(0.7f, 0.7f, 1f), 0.2f) }
                            }
                            interaction.onStateChange = { log("Box $i: $it") }
                        }
                    }
                }
            }
        }

        test("Audio") {
            audioTest()
        }

        test("Embedded scenes") {
            allowsNavigation = true
            var depth = 0

            fun sceneAt(level: Int): Scene = object : Scene() {
                init {
                    // Drawn over the whole scene and not affected by its layout.
                    background = Background.Component(Box().apply {
                        color = BACKGROUND_COLORS[level % BACKGROUND_COLORS.size]
                    })

                    linearContainer {
                        orientation = Orientation.Vertical
                        anchor = Anchor.Center
                        origin = Anchor.Center
                        spacing = 8f.dip

                        text { text = "Scene $level" }
                        textButton {
                            text = "Push"
                            onActionUp = { com.reco1l.verktex.Verktex.pushScene(sceneAt(++depth)) }
                        }
                        textButton {
                            text = "Pop"
                            onActionUp = { com.reco1l.verktex.Verktex.popScene()?.let { depth-- } }
                        }
                    }
                }

                override fun onEnter() = log("Scene $level entered")
                override fun onLeave() = log("Scene $level left")
            }

            scene(sceneAt(0))
        }

        addContainerTests()
    }

    private val BACKGROUND_COLORS = arrayOf(
        Color4(0.2f, 0.25f, 0.4f),
        Color4(0.35f, 0.2f, 0.3f),
        Color4(0.2f, 0.35f, 0.25f),
    )
}
