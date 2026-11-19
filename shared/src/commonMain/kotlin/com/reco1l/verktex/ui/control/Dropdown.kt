package com.reco1l.verktex.ui.control

import kotlin.concurrent.Volatile
import com.reco1l.verktex.data.Background
import com.reco1l.verktex.data.CornerRadius
import com.reco1l.verktex.*
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.ui.Component
import com.reco1l.verktex.ui.TextButton
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.container.IgnoreParentPaddingContainer
import com.reco1l.verktex.ui.container.LinearContainer
import com.reco1l.verktex.ui.container.ScrollableContainer
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.HoverEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.interpolateFloat
import com.reco1l.verktex.ui.fadeTo
import com.reco1l.verktex.ui.scaleTo
import com.reco1l.verktex.ui.transitionSlot
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.ui.Button
import com.reco1l.verktex.ui.container.LinearContainer.Orientation
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.data.wrap
import com.reco1l.verktex.time.FrameClock
import kotlin.math.*
import com.reco1l.verktex.ui.themed

class Dropdown(var trigger: Component) : ScrollableContainer() {

    /**
     * Whether the dropdown menu is currently expanded or not.
     */
    val isExpanded
        get() = host != null

    /**
     * A callback that is invoked when the dropdown menu is expanded.
     */
    var onExpand: (() -> Unit)? = null

    /**
     * A callback that is invoked when the dropdown menu is collapsed.
     */
    var onCollapse: (() -> Unit)? = null


    // The container the menu is shown in: the overlay host if there's one (e.g. the stage of the UI
    // test browser), otherwise the scene.
    private var host: Container? = null

    @Volatile
    private var isRemovalScheduled = false

    // The expansion or collapse animation, either cancels the other.
    private val expansion = transitionSlot()

    private var menuX = 0f
    private var menuY = 0f

    // Whether the menu jumps to its position on the next update instead of moving there.
    private var snapPosition = false


    private val optionsContainer: LinearContainer

    // Covers the host with its padding (e.g. a scene's safe area) and without padding of its own,
    // so the menu is positioned in the host's space.
    private val wrapper = object : IgnoreParentPaddingContainer() {

        init {
            keepsParentPadding = false
        }


        // The fingers that pressed the backdrop or the menu, only their events are handled, so
        // components outside of the host still get the moves and releases of their own presses.
        private val pressedPointers = mutableSetOf<Int>()

        override fun onInputEvent(event: InputEvent): Boolean {

            // While closing the menu must not block the scene below.
            if (!isExpanded) {
                pressedPointers.clear()
                return false
            }

            if (event is PointerEvent) {

                // The menu is modal, presses outside of it must not reach the scene below.
                if (event.action == PointerEvent.Action.Down) {
                    pressedPointers += event.pointerId
                    super.onInputEvent(event)
                    return true
                }

                if (event.pointerId !in pressedPointers) {
                    return false
                }

                if (event.action != PointerEvent.Action.Move) {
                    pressedPointers -= event.pointerId
                }

                if (!super.onInputEvent(event) && !this@Dropdown.containsScenePoint(event.x, event.y)) {
                    hide()
                }

                return true
            }

            // Nothing below is hovered either.
            if (event is HoverEvent) {
                super.onInputEvent(event)
                return true
            }

            return super.onInputEvent(event)
        }

    }



    init {
        width = Dimension.WrapContent
        height = Dimension.WrapContent
        scrollAxes = Axis.Y
        clipping = ClipMode.Bounds

        radius = themed { CornerRadius(radius.md) }
        background = Background.Color(themed { palette.surface })

        scaleOrigin = Anchor.TopCenter
        alpha = 0f
        scale = 0f

        optionsContainer = linearContainer {
            width = fill()
            orientation = Orientation.Vertical
            spacing = 4f.dip
            padding = Padding(4f.dip)
        }

        wrapper += this
    }

    override fun onUpdate(clock: FrameClock) {

        val host = host

        if (host != null) {
            // In the space of the host, which the menu is positioned in.
            val (triggerSceneLeft, triggerSceneTop) = trigger.toSceneSpace(0f, 0f)
            val (triggerSceneRight, triggerSceneBottom) = trigger.toSceneSpace(trigger.bounds.width, trigger.bounds.height)
            val (triggerTopLeftX, triggerTopLeftY) = host.toLocalSpaceFromScene(triggerSceneLeft, triggerSceneTop)
            val (triggerBottomRightX, triggerBottomRightY) = host.toLocalSpaceFromScene(triggerSceneRight, triggerSceneBottom)

            // The menu is at least as wide as the trigger, so it reads as coming out of it.
            minWidth = (triggerBottomRightX - triggerTopLeftX).px

            val spaceBelow = host.bounds.height - triggerBottomRightY
            val expandUpwards = triggerTopLeftY > spaceBelow && spaceBelow < optionsContainer.measuredSize.y

            // Height of the dropdown menu is limited to the available space below or above the trigger,
            // depending on the expansion direction.
            height = min(optionsContainer.measuredSize.y, if (expandUpwards) triggerTopLeftY else spaceBelow).px

            // Position of the dropdown menu is clamped to the host bounds, so it doesn't go off.
            // Essentially it will always be aligned to the trigger, but if the trigger is too close
            // to the right edge of the host, the dropdown will be shifted to the left to fit within
            // it.
            val targetX = triggerTopLeftX + min(0f, host.bounds.width - triggerTopLeftX - optionsContainer.bounds.width)

            val targetY = if (expandUpwards) {
                triggerTopLeftY - min(optionsContainer.bounds.height, triggerTopLeftY)
            } else {
                triggerBottomRightY
            }

            // Bounds are only updated on the draw worker, so the position is tracked separately. It
            // follows the trigger, which can move while the menu is open, so it's smoothed towards it.
            if (snapPosition) {
                snapPosition = false
                menuX = targetX
                menuY = targetY
            }
            menuX = clock.interpolateFloat(0.2f, menuX, targetX)
            menuY = clock.interpolateFloat(0.2f, menuY, targetY)

            x = menuX.px
            y = menuY.px

            scaleOrigin = if (expandUpwards) Anchor.BottomCenter else Anchor.TopCenter
        }

        super.onUpdate(clock)
    }

    private fun scheduleRemoval() {
        // Scheduled once, the update worker runs more often than the draw worker.
        if (isRemovalScheduled) {
            return
        }
        isRemovalScheduled = true

        Verktex.scheduleOnDraw {
            // It might have been shown again meanwhile.
            if (!isExpanded) {
                wrapper.removeSelf()
            }
            isRemovalScheduled = false
        }
    }

    //region Buttons

    fun addButton(block: TextButton.() -> Unit): TextButton {
        val button = TextButton().apply {
            width = Dimension.FillAvailable
            variant = Button.Variant.Ghost
            alignment = Anchor.CenterLeft
            block()
        }

        optionsContainer += button
        return button
    }

    fun clearButtons() {
        optionsContainer.clearChildren()
    }

    fun forEachButton(action: (TextButton) -> Unit) {
        optionsContainer.forEach { action(it as TextButton) }
    }

    //endregion

    //region Visibility

    fun show() {
        val wasExpanded = isExpanded
        // Above the overlay it was opened from, or the scene, which an overlay can stop drawing.
        val host = Verktex.overlayHost ?: if (Verktex.isInOverlay(trigger)) Verktex.overlays else Verktex.scene

        this.host = host

        wrapper.removeSelf()
        host += wrapper

        if (!wasExpanded) {
            // Where the trigger is once laid out, if it wasn't still visible from a previous show.
            snapPosition = alpha == 0f

            expansion.run {
                fadeTo(1f, 0.2f, Easing.OutQuint)
                scaleTo(1f, 0.4f, Easing.OutBounce)
            }
            onExpand?.invoke()
        }
    }

    fun hide() {
        if (host == null) {
            return
        }
        host = null

        expansion.run {
            scaleTo(0f, 0.2f, Easing.OutExpo)
            fadeTo(0f, 0.2f, Easing.OutQuint).after { scheduleRemoval() }
        }
        onCollapse?.invoke()
    }

    //endregion

}