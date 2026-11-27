package com.reco1l.verktex.ui

import com.reco1l.verktex.time.monotonicNanos
import com.reco1l.verktex.PerformanceStats
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.extendIntoSafeArea
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.container.Container

/**
 * A screen of the application, the root of the components shown while it's the current one. Its
 * content keeps the safe area through its padding, [extendIntoSafeArea] by default: a scene can add
 * padding of its own (`Padding(…).extendIntoSafeArea()`) or let its content cover the safe area
 * (`Padding.Zero`). Something drawn behind the content over the whole scene, padding aside, is its
 * [background], e.g. a [com.reco1l.verktex.data.Background.Component].
 */
open class Scene : Container() {

    init {
        width = fill()
        height = fill()
        padding = extendIntoSafeArea()
    }


    /**
     * Whether this scene is a child of another component instead of a root of the engine, e.g. a
     * scene shown inside the stage of the UI test browser. Its parent lays it out then.
     */
    val isEmbedded
        get() = parent != null

    /**
     * The safe area the content keeps while the scene is embedded, as left, top, right and bottom,
     * set by its parent. E.g. the UI test browser sets the window's so a scene looks as in the game.
     * A root scene uses the window's instead.
     */
    var embeddedSafeArea = Vec4.Zero

    /**
     * The safe area of the scene, as left, top, right and bottom: the window's for a root scene, or
     * [embeddedSafeArea] while embedded. A root scene's is shifted up while the on-screen keyboard
     * would cover the focused input, which can make its top negative.
     */
    val safeArea: Vec4
        get() = when {
            isEmbedded -> embeddedSafeArea
            keepsWindowSafeArea -> {
                val safeArea = Verktex.graphics.window.insets.safeArea
                val imeShift = Verktex.imeShift
                Vec4(safeArea.x, safeArea.y - imeShift, safeArea.z, safeArea.w + imeShift)
            }
            else -> Vec4.Zero
        }

    /**
     * Whether the content of a root scene keeps the window's safe area. The UI test browser doesn't
     * while it simulates one for its stage, see [com.reco1l.verktex.testing.SimulatedSafeArea].
     */
    protected open val keepsWindowSafeArea: Boolean
        get() = true

    // The safe area of the last frame, measuring again when it changes.
    private var lastSafeArea: Vec4? = null


    // Laid out before anything of the scene is drawn: a component background (e.g. a tree drawn behind the content,
    // Background.Component) is drawn before onDraw, and would otherwise show the previous frame's layout, with
    // what moved since at its old place for a frame.
    override fun onPrepareDraw() {

        // The padding keeps the content in the safe area, the background is laid out over the whole
        // scene. A change of the safe area (or of the keyboard's shift) is a change of the padding.
        val safeArea = safeArea
        if (safeArea != lastSafeArea) {
            lastSafeArea = safeArea
            invalidateMeasure()
        }

        if (isEmbedded) {
            return
        }

        val layoutStartNanos = monotonicNanos()
        onPerformLayout()
        PerformanceStats.addLayoutTime(monotonicNanos() - layoutStartNanos)
    }

    /**
     * Called when the scene should process layout invalidations.
     */
    open fun onPerformLayout() {

        val windowWidth = Verktex.graphics.window.windowWidth.toFloat()
        val windowHeight = Verktex.graphics.window.windowHeight.toFloat()

        val constraints = LayoutConstraints(
            minWidth = 0f,
            maxWidth = windowWidth,
            minHeight = 0f,
            maxHeight = windowHeight
        )

        measure(constraints)

        val bounds = Vec4(
            x = 0f,
            y = 0f,
            z = windowWidth,
            w = windowHeight
        )

        layout(bounds)
    }


    /**
     * Called when this scene becomes the current one, either pushed or shown again after the scene
     * above it was popped.
     */
    open fun onEnter() {}

    /**
     * Called when this scene stops being the current one, either popped or covered by a new scene.
     */
    open fun onLeave() {}

    /**
     * Called when the game's window gains or loses the focus (e.g. the notification shade is pulled
     * on Android, or another window is selected on desktop), on every scene in the stack, not only
     * the current one. It's called with the tree locked.
     */
    open fun onGameWindowFocusChange(hasFocus: Boolean) {}


    /**
     * A special scene that represents an empty or default state. This scene is used as a placeholder
     * when there are no other scenes in the stack.
     *
     * It does not perform any drawing or updating operations and cannot get children added to it.
     * It serves as a base case for the scene stack, ensuring that there is always at least one scene
     * present.
     */
    companion object EmptyScene : Scene() {
        override fun onDraw(renderer: Renderer) {}
        override fun onUpdate(clock: FrameClock) {}
    }
}


/**
 * The scene this component is in, the closest one if scenes are nested, e.g. a scene embedded in
 * the stage of the UI test browser.
 */
internal fun Component.findScene(): Scene? {
    var current = parent
    while (current != null) {
        if (current is Scene) return current
        current = current.parent
    }
    return null
}
