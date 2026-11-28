package com.reco1l.verktex.testing

import com.reco1l.verktex.math.toFixed
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.pct
import com.reco1l.verktex.data.px
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.container.ScalingContainer
import com.reco1l.verktex.ui.container.LinearContainer
import com.reco1l.verktex.ui.container.RecyclerContainer
import com.reco1l.verktex.ui.container.RecyclableComponent
import com.reco1l.verktex.ui.container.clipContainer
import com.reco1l.verktex.ui.container.constraintContainer
import com.reco1l.verktex.ui.container.container
import com.reco1l.verktex.ui.container.gridContainer
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.ui.container.scrollableContainer
import com.reco1l.verktex.ui.text.Text
import com.reco1l.verktex.ui.text.text
import com.reco1l.verktex.ui.textButton
import kotlin.math.sin

private val FAINT = Color4(1f, 1f, 1f, 0.08f)

private val COLORS = listOf(Color4.Red400, Color4.Orange400, Color4.Yellow400, Color4.Green400, Color4.Blue400, Color4.Purple400, Color4.Pink400)

private fun colorAt(index: Int) = COLORS[index % COLORS.size]


/**
 * A colored block with a centered label, used to show how containers size and place children.
 */
private inline fun Container.block(label: String, tint: Color4, builder: Container.() -> Unit = {}) = container {
    background = Background.Color(tint)
    radius = CornerRadius(6f.dip)
    padding = Padding(10f.dip, 6f.dip)
    builder()

    text {
        anchor = Anchor.Center
        origin = Anchor.Center
        text = label
        color = Color4.Black
        fontSize = Verktex.theme.typography.caption.size
    }
}

/**
 * A horizontal linear container with a fixed width and a faint background, so its free space is visible.
 */
private inline fun Container.row(builder: LinearContainer.() -> Unit) = linearContainer {
    orientation = LinearContainer.Orientation.Horizontal
    width = 560f.dip
    padding = Padding(4f.dip)
    spacing = 4f.dip
    background = Background.Color(FAINT)
    builder()
}

/**
 * A vertical linear container with a fixed height and a faint background, so its free space is visible.
 */
private inline fun Container.columnBox(builder: LinearContainer.() -> Unit) = linearContainer {
    orientation = LinearContainer.Orientation.Vertical
    width = 100f.dip
    height = 280f.dip
    padding = Padding(4f.dip)
    spacing = 4f.dip
    background = Background.Color(FAINT)
    builder()
}


/**
 * Adds the container demos to a suite.
 */
fun UITestSuite.addContainerTests() {

    test("Linear: horizontal") {
        column {
            section("Fixed children wrap, the free space stays empty") {
                row {
                    block("80", colorAt(0)) { width = 80f.dip }
                    block("120", colorAt(1)) { width = 120f.dip }
                    block("Wrap", colorAt(2))
                }
            }

            section("A fill() child takes all the free space") {
                row {
                    block("80", colorAt(0)) { width = 80f.dip }
                    block("fill()", colorAt(3)) { width = fill() }
                    block("80", colorAt(0)) { width = 80f.dip }
                }
            }

            section("Fill children share the free space equally") {
                row {
                    block("fill()", colorAt(3)) { width = fill() }
                    block("fill()", colorAt(4)) { width = fill() }
                    block("fill()", colorAt(5)) { width = fill() }
                }
            }

            section("fill(f) takes a fraction of its equal share") {
                row {
                    block("fill()", colorAt(3)) { width = fill() }
                    block("fill(0.5)", colorAt(4)) { width = fill(0.5f) }
                }
            }

            section("Anchors place children along the cross axis") {
                row {
                    block("80 tall", colorAt(0)) { width = 80f.dip; height = 80f.dip }
                    block("Top", colorAt(1)) { anchor = Anchor.TopLeft; origin = Anchor.TopLeft }
                    block("Center", colorAt(2)) { anchor = Anchor.CenterLeft; origin = Anchor.CenterLeft }
                    block("Bottom", colorAt(3)) { anchor = Anchor.BottomLeft; origin = Anchor.BottomLeft }
                }
            }

            section("height = fill() stretches to the row height") {
                row {
                    block("80 tall", colorAt(0)) { width = 80f.dip; height = 80f.dip }
                    block("fill()", colorAt(3)) { width = 80f.dip; height = fill() }
                    block("Wrap", colorAt(2)) { width = 80f.dip }
                }
            }

            section("Hidden children take no space and no spacing") {
                orientation = LinearContainer.Orientation.Vertical

                lateinit var hidden: Container

                row {
                    block("fill()", colorAt(3)) { width = fill() }
                    hidden = block("Hidden?", colorAt(1)) { width = 120f.dip }
                    block("fill()", colorAt(4)) { width = fill() }
                }

                textButton {
                    text = "Toggle"
                    onActionUp = {
                        hidden.isVisible = !hidden.isVisible
                        log("Middle child visible: ${hidden.isVisible}")
                    }
                }
            }
        }
    }

    test("Linear: vertical") {
        column {
            section("Fixed, fill, shared fill, fraction, cross axis fill") {
                columnBox {
                    block("40", colorAt(0)) { height = 40f.dip }
                    block("60", colorAt(1)) { height = 60f.dip }
                    block("Wrap", colorAt(2))
                }

                columnBox {
                    block("40", colorAt(0)) { height = 40f.dip }
                    block("fill()", colorAt(3)) { height = fill() }
                    block("40", colorAt(0)) { height = 40f.dip }
                }

                columnBox {
                    block("fill()", colorAt(3)) { height = fill() }
                    block("fill()", colorAt(4)) { height = fill() }
                    block("fill()", colorAt(5)) { height = fill() }
                }

                columnBox {
                    block("fill()", colorAt(3)) { height = fill() }
                    block("fill(0.5)", colorAt(4)) { height = fill(0.5f) }
                }

                columnBox {
                    block("Wrap", colorAt(2))
                    block("w fill()", colorAt(3)) { width = fill() }
                    block("Wrap", colorAt(2))
                }
            }

            section("Spacing") {
                for (spacing in listOf(0f, 8f, 24f)) {
                    columnBox {
                        this.spacing = spacing.dip
                        repeat(3) { block("$spacing", colorAt(it)) { width = fill() } }
                    }
                }
            }
        }
    }

    test("Sizes and padding") {
        column {
            section("Fixed, fill fractions and wrap inside a 400dip container") {
                orientation = LinearContainer.Orientation.Vertical

                for ((label, size) in listOf(
                    "200dip" to 200f.dip,
                    "fill(0.25)" to fill(0.25f),
                    "fill(0.5)" to fill(0.5f),
                    "fill()" to fill(),
                )) {
                    container {
                        width = 400f.dip
                        background = Background.Color(FAINT)
                        block(label, colorAt(4)) { width = size }
                    }
                }

                container {
                    width = 400f.dip
                    background = Background.Color(FAINT)
                    block("Wrap content", colorAt(4))
                }
            }

            section("Padding, the filling child stays inside it") {
                for (padding in listOf(0f, 12f, 32f)) {
                    container {
                        width = 160f.dip
                        height = 100f.dip
                        background = Background.Color(FAINT)
                        this.padding = Padding(padding.dip)
                        block("${padding.toInt()}dip", colorAt(5)) { width = fill(); height = fill() }
                    }
                }
            }

            section("Clipping: disabled, bounds") {
                for (clip in listOf(ClipMode.Disabled, ClipMode.Bounds)) {
                    container {
                        width = 160f.dip
                        height = 100f.dip
                        background = Background.Color(FAINT)
                        clipping = clip

                        block("Overflowing", colorAt(6)) {
                            anchor = Anchor.Center
                            origin = Anchor.Center
                            width = 240f.dip
                            height = 40f.dip
                        }
                    }
                }
            }

            section("Clip container: a fraction of the height shown, from the bottom") {
                for (fraction in listOf(0.25f, 0.5f, 1f)) {
                    container {
                        width = 160f.dip
                        height = 100f.dip
                        background = Background.Color(FAINT)

                        clipContainer {
                            width = fill()
                            height = fill()
                            clipFactorY = fraction
                            clipAnchor = Anchor.BottomLeft

                            block("${(fraction * 100).toInt()}% shown", colorAt(3)) {
                                width = fill()
                                height = fill()
                            }
                        }
                    }
                }
            }
        }
    }

    test("Anchors and origins") {
        column {
            section("anchor = origin, on every point") {
                container {
                    width = 320f.dip
                    height = 180f.dip
                    background = Background.Color(FAINT)

                    for ((index, anchor) in ANCHORS.withIndex()) {
                        block("", colorAt(index)) {
                            this.anchor = anchor
                            origin = anchor
                            width = 24f.dip
                            height = 24f.dip
                        }
                    }
                }
            }

            section("anchor = Center, with every origin") {
                container {
                    width = 320f.dip
                    height = 180f.dip
                    background = Background.Color(FAINT)

                    for ((index, origin) in ANCHORS.withIndex()) {
                        block("", colorAt(index).alpha(0.6f)) {
                            anchor = Anchor.Center
                            this.origin = origin
                            width = 40f.dip
                            height = 40f.dip
                        }
                    }
                }
            }

            section("Offsets from an anchor") {
                container {
                    width = 320f.dip
                    height = 180f.dip
                    background = Background.Color(FAINT)

                    block("x 16, y 16", colorAt(0)) { anchor = Anchor.TopLeft; x = 16f.dip; y = 16f.dip }
                    block("x -16, y -16", colorAt(4)) {
                        anchor = Anchor.BottomRight
                        origin = Anchor.BottomRight
                        x = (-16f).dip
                        y = (-16f).dip
                    }
                }
            }
        }
    }

    test("Constraint container") {
        lateinit var target: Container

        constraintContainer {
            width = fill()
            height = fill()

            target = block("Target", colorAt(6)) {
                anchor = Anchor.Center
                origin = Anchor.Center
                width = 160f.dip
                height = 100f.dip
            }

            val above = block("Above target", colorAt(4)) {
                anchor = Anchor.TopCenter
                origin = Anchor.BottomCenter
                y = (-8f).dip
            }

            val right = block("Right of target", colorAt(3)) {
                anchor = Anchor.CenterRight
                origin = Anchor.CenterLeft
                x = 8f.dip
            }

            val corner = block("Corner", colorAt(2)) {
                anchor = Anchor.TopRight
                origin = Anchor.Center
            }

            val inside = block("Filling target", colorAt(0).alpha(0.5f)) {
                anchor = Anchor.BottomCenter
                origin = Anchor.BottomCenter
                width = fill()
            }

            val chained = block("Above 'Above'", colorAt(5)) {
                anchor = Anchor.TopCenter
                origin = Anchor.BottomCenter
                y = (-8f).dip
            }

            addConstraint(above, target)
            addConstraint(right, target)
            addConstraint(corner, target)
            addConstraint(inside, target)
            addConstraint(chained, above)
        }

        linearContainer {
            anchor = Anchor.BottomCenter
            origin = Anchor.BottomCenter
            y = (-16f).dip
            spacing = 8f.dip

            val placements = listOf(Anchor.CenterLeft, Anchor.Center, Anchor.CenterRight, Anchor.BottomCenter)
            var placement = 1

            textButton {
                text = "Move target"
                onActionUp = {
                    placement = (placement + 1) % placements.size
                    target.anchor = placements[placement]
                    target.origin = placements[placement]
                    log("Target at ${placements[placement]}")
                }
            }

            var isWide = false

            textButton {
                text = "Resize target"
                onActionUp = {
                    isWide = !isWide
                    target.width = if (isWide) 280f.dip else 160f.dip
                    target.height = if (isWide) 60f.dip else 100f.dip
                }
            }
        }
    }

    test("Scaling container") {
        lateinit var area: Container
        lateinit var scaling: ScalingContainer
        lateinit var factorText: Text

        area = container {
            anchor = Anchor.Center
            origin = Anchor.Center
            width = 0.8f.pct
            height = 0.7f.pct
            background = Background.Color(FAINT)

            scaling = object : ScalingContainer() {
                override fun onUpdate(clock: FrameClock) {
                    super.onUpdate(clock)

                    val label = "$mode, factor ${factor.toFixed(2)}"
                    if (factorText.text.toString() != label) {
                        factorText.text = label
                    }
                }
            }.also { +it }

            scaling.apply {
                referenceSize = Vec2(1280f, 720f)

                // Sized in reference pixels, as a HUD designed for 1280x720.
                for ((index, anchor) in ANCHORS.withIndex()) {
                    block("", colorAt(index)) {
                        this.anchor = anchor
                        origin = anchor
                        width = 120f.px
                        height = 120f.px
                    }
                }

                factorText = text {
                    anchor = Anchor.TopCenter
                    origin = Anchor.TopCenter
                    y = 140f.px
                    fontSize = 48f.px
                }
            }
        }

        linearContainer {
            anchor = Anchor.BottomCenter
            origin = Anchor.BottomCenter
            y = (-16f).dip
            spacing = 8f.dip

            textButton {
                text = "Change mode"
                onActionUp = {
                    val modes = ScalingContainer.Mode.entries
                    scaling.mode = modes[(scaling.mode.ordinal + 1) % modes.size]
                }
            }

            var isNarrow = false

            textButton {
                text = "Resize area"
                onActionUp = {
                    isNarrow = !isNarrow
                    area.width = if (isNarrow) 0.45f.pct else 0.8f.pct
                }
            }
        }
    }

    test("Blur background") {
        // Blocks moving behind the blurred cards, so a static blur (captured once) and a live one differ.
        val moving = mutableListOf<Container>()

        val blocks = object : Container() {
            private var time = 0f

            override fun onUpdate(clock: FrameClock) {
                super.onUpdate(clock)
                time += clock.elapsedFrameTime
                moving.forEachIndexed { index, block -> block.translationX = (sin(time + index * 0.7f) * 160f).px }
            }
        }.also { +it }

        blocks.apply {
            width = fill()
            height = fill()

            for (index in 0 until 8) {
                moving += block("Block $index", colorAt(index)) {
                    anchor = Anchor.TopCenter
                    origin = Anchor.TopCenter
                    y = (40f + index * 56f).dip
                    width = 140f.dip
                    height = 44f.dip
                }
            }
        }

        var isStatic = false
        fun blur() = Background.Blur(12f.dip, isStatic, Color4(1f, 1f, 1f, 0.1f))

        val cards = List(2) { index ->
            container {
                anchor = Anchor.Center
                origin = Anchor.Center
                x = (if (index == 0) -170f else 170f).dip
                width = 300f.dip
                height = 220f.dip
                radius = CornerRadius(if (index == 0) 24f.dip else 0f.dip)
                background = blur()

                text {
                    anchor = Anchor.Center
                    origin = Anchor.Center
                    text = if (index == 0) "Rounded" else "Square"
                }
            }
        }

        linearContainer {
            anchor = Anchor.BottomCenter
            origin = Anchor.BottomCenter
            y = (-16f).dip
            spacing = 8f.dip

            textButton {
                text = "Static: off"
                onActionUp = {
                    isStatic = !isStatic
                    text = "Static: ${if (isStatic) "on" else "off"}"
                    cards.forEach { it.background = blur() }
                }
            }

            textButton {
                text = "Capture again"
                onActionUp = { cards.forEach { (it.background as? Background.Blur)?.invalidate() } }
            }
        }
    }

    test("Scrollable") {
        lateinit var positionText: Text

        val vertical = scrollableContainer {
            scrollAxes = Axis.Y
            width = 260f.dip
            height = fill()
            background = Background.Color(FAINT)

            linearContainer {
                orientation = LinearContainer.Orientation.Vertical
                width = fill()
                padding = Padding(4f.dip)
                spacing = 4f.dip

                repeat(40) { block("Row ${it + 1}", colorAt(it)) { width = fill(); height = 40f.dip } }
            }
        }

        val horizontal = scrollableContainer {
            scrollAxes = Axis.X
            anchor = Anchor.TopRight
            origin = Anchor.TopRight
            width = 360f.dip
            height = 80f.dip
            background = Background.Color(FAINT)

            linearContainer {
                padding = Padding(4f.dip)
                spacing = 4f.dip
                height = fill()

                repeat(30) { block("${it + 1}", colorAt(it)) { width = 64f.dip; height = fill() } }
            }
        }

        val both = scrollableContainer {
            scrollAxes = Axis.Both
            anchor = Anchor.BottomRight
            origin = Anchor.BottomRight
            width = 360f.dip
            height = 180f.dip
            background = Background.Color(FAINT)

            container {
                width = 1200f.dip
                height = 800f.dip

                for (row in 0 until 10) {
                    for (column in 0 until 15) {
                        block("$column,$row", colorAt(row + column)) {
                            x = (column * 80f + 4f).dip
                            y = (row * 80f + 4f).dip
                            width = 72f.dip
                            height = 72f.dip
                        }
                    }
                }
            }
        }

        positionText = text {
            anchor = Anchor.TopRight
            origin = Anchor.TopRight
            y = 88f.dip
            fontSize = Verktex.theme.typography.caption.size
        }

        onEachFrame {
            positionText.text = "Vertical ${vertical.scrollY.toFixed(0)}  ·  Horizontal ${horizontal.scrollX.toFixed(0)}  ·  " +
                "Both ${both.scrollX.toFixed(0)}, ${both.scrollY.toFixed(0)}"
        }
    }

    test("Recycler") {
        val items = (1..10_000).toList()

        +RecyclerContainer<Int, RecyclerRow>().apply {
            width = fill()
            height = fill()
            spacing = 4f.dip
            padding = Padding(8f.dip)

            onCreateComponent = { RecyclerRow() }
            data = items
        }

        log("${items.size} items, only the visible ones have a component")
    }

    test("Grid: columns") {
        column {
            section("3 columns share the width, a row is as tall as its tallest child") {
                gridContainer {
                    width = 560f.dip
                    columns = 3
                    columnSpacing = 4f.dip
                    rowSpacing = 4f.dip
                    padding = Padding(4f.dip)
                    background = Background.Color(FAINT)

                    block("fill()", colorAt(0)) { width = fill() }
                    block("60 tall", colorAt(1)) { width = fill(); height = 60f.dip }
                    block("fill() x fill()", colorAt(2)) { width = fill(); height = fill() }
                    block("Wrap, centered", colorAt(3)) { anchor = Anchor.Center; origin = Anchor.Center }
                    block("fill()", colorAt(4)) { width = fill() }
                }
            }

            section("Hidden children take no cell") {
                orientation = LinearContainer.Orientation.Vertical

                lateinit var hidden: Container

                gridContainer {
                    width = 560f.dip
                    columns = 4
                    columnSpacing = 4f.dip
                    rowSpacing = 4f.dip
                    padding = Padding(4f.dip)
                    background = Background.Color(FAINT)

                    repeat(6) { index ->
                        val cell = block("$index", colorAt(index)) { width = fill() }
                        if (index == 1) hidden = cell
                    }
                }

                textButton {
                    text = "Toggle"
                    onActionUp = {
                        hidden.isVisible = !hidden.isVisible
                        log("Cell 1 visible: ${hidden.isVisible}")
                    }
                }
            }
        }
    }

    test("Grid: min column width") {
        scrollableContainer {
            width = fill()
            height = fill()
            scrollAxes = Axis.Y

            gridContainer {
                width = fill()
                minColumnWidth = 160f.dip
                columnSpacing = 8f.dip
                rowSpacing = 8f.dip
                padding = Padding(8f.dip)

                repeat(30) { index -> block("${index + 1}", colorAt(index)) { width = fill(); height = 80f.dip } }
            }
        }

        log("Columns of at least 160dip: resize the window to change their count")
    }

    test("Recycler: grid") {
        val items = (1..10_000).toList()

        +RecyclerContainer<Int, RecyclerRow>().apply {
            width = fill()
            height = fill()
            minColumnWidth = 160f.dip
            columnSpacing = 4f.dip
            spacing = 4f.dip
            padding = Padding(8f.dip)

            onCreateComponent = { RecyclerRow() }
            data = items
        }

        log("${items.size} items in columns of at least 160dip, only the visible rows have components")
    }

    test("Recycler: append") {
        val pageSize = 50

        +RecyclerContainer<Int, RecyclerRow>().apply {
            width = fill()
            height = fill()
            columns = 2
            columnSpacing = 4f.dip
            spacing = 4f.dip
            padding = Padding(8f.dip)
            endThreshold = 200f.dip

            onCreateComponent = { RecyclerRow() }
            data = (1..pageSize).toList()

            onScrolledToEnd = {
                val start = data.size + 1
                addData((start until start + pageSize).toList())
                log("Near the end, appended items $start to ${start + pageSize - 1}")
            }
        }
    }
}


private val ANCHORS = listOf(
    Anchor.TopLeft, Anchor.TopCenter, Anchor.TopRight,
    Anchor.CenterLeft, Anchor.Center, Anchor.CenterRight,
    Anchor.BottomLeft, Anchor.BottomCenter, Anchor.BottomRight
)


private class RecyclerRow : RecyclableComponent<Int>() {

    override val isRecyclable = true

    private val label = Text()


    init {
        width = fill()
        height = 44f.dip
        radius = CornerRadius(6f.dip)
        padding = Padding(12f.dip, 0f.dip)

        +label.apply {
            anchor = Anchor.CenterLeft
            origin = Anchor.CenterLeft
        }
    }


    override fun onBind(data: Int) {
        label.text = "Item $data"
        background = Background.Color(colorAt(data).alpha(0.3f))
    }
}
