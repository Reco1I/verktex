package com.reco1l.verktex.ui

import com.reco1l.verktex.ui.container.LinearContainer
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.ui.text.Text
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.horizontalPadding
import com.reco1l.verktex.data.px
import com.reco1l.verktex.data.wrap
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.container.clickableContainer
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.ui.shape.Box

fun Container.card(block: Card.() -> Unit) = Card().apply(block).also { +it }

open class Card : LinearContainer() {

    /**
     * The title to show in the card's title bar. If `null` or empty, the title bar will not be shown.
     */
    var title: CharSequence? = null
        set(value) {
            field = value
            titleText.text = value ?: ""
        }

    /**
     * Whether the card can be collapsed or not. If true, the title bar will be clickable and the
     * card can be collapsed or expanded by clicking on it. If false, the card will always be expanded
     * and the title bar will not be clickable.
     *
     * Note that if [isCollapsible] is false, [isExpanded] will always return true and [collapse] will
     * have no effect.
     */
    var isCollapsible = false
        set(value) {
            if (field == value) return
            field = value

            // No animation for now, just hide the title bar if the card is not collapsible.
            if (value) {
                titleBar.height = themed { controlStyle.minimumHeight }
                titleBar.isVisible = true
            } else {
                titleBar.height = 0f.px
                titleBar.isVisible = false
                isExpanded = true
            }
            animateExpansion()
        }

    /**
     * Whether the card is currently expanded or collapsed.
     */
    var isExpanded: Boolean = true
        get() = if (isCollapsible) field else true
        private set(value) {
            if (field == value) return
            field = value
            animateExpansion()
            if (isCollapsible) {
                onExpandStatusChange?.invoke(value)
            }
        }

    /**
     * A function that is called when this [Card] is collapsed or expanded.
     *
     * This function will be called only if [isCollapsible] is true. If [isCollapsible] is false, this
     * function will never be called.
     */
    var onExpandStatusChange: ((isExpanded: Boolean) -> Unit)? = null

    //region Components

    private val titleBar = clickableContainer {
        width = fill()

        height = themed { controlStyle.minimumHeight }
        padding = themed { horizontalPadding(gap.md) }

        onActionUp = {
            if (isCollapsible) {
                isExpanded = !isExpanded
            }
        }
    }

    private val titleText = Text().apply {
        anchor = Anchor.CenterLeft
        origin = Anchor.CenterLeft

        fontSettings = themed { typography.caption }
    }

    private val chevron = Icon(FAIcon.ChevronDown).apply {
        anchor = Anchor.CenterRight
        origin = Anchor.CenterRight
        rotationOrigin = Anchor.Center

        iconSize = themed { typography.caption.size }
    }

    val content = linearContainer {
        width = fill()
        height = wrap()
        // Hides the overflowing children while collapsing.
        clipping = ClipMode.Bounds

        padding = themed { Padding(gap.md) }
        orientation = Orientation.Vertical
    }

    //endregion


    init {
        orientation = Orientation.Vertical
        background = Background.Color(themed { palette.surface })
        radius = themed { CornerRadius(radius.md) }
        border = themed { cardStyle.elevation.border }
        shadow = themed { cardStyle.elevation.shadow }

        titleBar += titleText
        titleBar += chevron

        // Divider
        titleBar += Box().apply {
            width = fill()
            height = 2f.px
            anchor = Anchor.BottomCenter
            origin = Anchor.BottomCenter
            color = themed { palette.divider }
        }
    }


    private val expansion = transitionSlot()

    private val chevronRotation = chevron.transitionSlot()

    private val contentHeightFraction = animatableFloat(
        get = { (content.height as? Dimension.ContentSizeFraction)?.factor ?: 1f },
        set = { content.height = wrap(percentage = it) }
    )

    private fun animateExpansion() {
        val expanded = isExpanded

        expansion.run { animate(contentHeightFraction, if (expanded) 1f else 0f, 0.2f, Easing.OutQuint) }
        chevronRotation.run { rotateTo(if (expanded) 0f else 180f, 0.2f, Easing.OutQuint) }
    }


    /**
     * Builds the content of the card using the internal content container.
     */
    fun content(block: LinearContainer.() -> Unit) {
        content.block()
    }

    /**
     * Collapses the card content.
     */
    fun collapse() {
        isExpanded = false
    }

    /**
     * Expands the card content.
     */
    fun expand() {
        isExpanded = true
    }

}