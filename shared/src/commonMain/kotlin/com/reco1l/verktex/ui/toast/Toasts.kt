package com.reco1l.verktex.ui.toast

import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.ui.container.Container
import com.reco1l.verktex.ui.fadeOut
import com.reco1l.verktex.ui.transformSequence
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shows messages as [Toast]s at the bottom of the window, above the scene and the other overlays,
 * one at a time: those shown while one is on screen wait their turn, in order, and a message
 * already shown or waiting isn't queued again. They can be shown from any thread, and the user can
 * dismiss them, with their close button or swiping them away, which shows the next one.
 *
 * Nothing is shown until the engine is initialized, so code that shows toasts also runs without it
 * (e.g. in tests).
 */
object Toasts {

    /**
     * How long a short message stays on screen.
     */
    val Short = 2000.milliseconds

    /**
     * How long a long message stays on screen.
     */
    val Long = 3500.milliseconds

    private const val FADE_DURATION = 0.2f

    private val container = Container().apply {
        width = fill()
        anchor = Anchor.BottomCenter
        origin = Anchor.BottomCenter
        padding = Padding(bottom = 24f.dip)
    }

    private class Message(val text: CharSequence, val title: CharSequence, val duration: Duration) {
        fun isSameAs(other: Message) = text.toString() == other.text.toString() && title.toString() == other.title.toString()
    }

    // Changed under the tree lock, as the components.
    private val queue = ArrayDeque<Message>()

    private var current: Toast? = null
    private var currentMessage: Message? = null


    /**
     * Shows [text] for [Short].
     */
    @JvmStatic
    fun show(text: CharSequence) = show(text, Short)

    /**
     * Shows [text] for [Long].
     */
    @JvmStatic
    fun showLong(text: CharSequence) = show(text, Long)

    /**
     * Shows [text], with [title] above it if not empty, for [duration] once the messages before it
     * were shown, see [Short] and [Long].
     */
    @JvmStatic
    @JvmOverloads
    fun show(text: CharSequence, duration: Duration, title: CharSequence = "") = update {
        val message = Message(text, title, duration)

        if (currentMessage?.isSameAs(message) == true || queue.any { it.isSameAs(message) }) {
            return@update
        }

        queue.addLast(message)
        if (current == null) {
            showNext()
        }
    }

    private fun showNext() {
        val message = queue.removeFirstOrNull()
        currentMessage = message

        if (message == null) {
            current = null
            container.removeSelf()
            return
        }

        // Toasts stay above what was added since, e.g. a dialog.
        container.removeSelf()
        Verktex.addOverlay(container)

        val toast = Toast().apply {
            text = message.text
            title = message.title
            alpha = 0f
            isDismissible = true
        }
        toast.onDismiss = { hide(toast) }

        current = toast
        container += toast

        toast.transformSequence { fadeIn(FADE_DURATION, Easing.OutQuad) }
            .then()
            .delay(message.duration.inWholeMilliseconds / 1000f)
            .fadeOut(FADE_DURATION)
            .after { finish(toast) }
    }

    // Dismissed by the user before its time: the next one follows once it faded out.
    private fun hide(toast: Toast) {
        if (current !== toast) {
            return
        }
        toast.fadeOut(FADE_DURATION, Easing.OutQuad).after { finish(toast) }
    }

    private fun finish(toast: Toast) {
        // Already gone, e.g. dismissed before its time ran out.
        if (current !== toast) {
            return
        }
        container -= toast
        showNext()
    }

    /**
     * Changes the components from any thread, holding the tree lock as the engine's workers do.
     */
    private fun update(block: () -> Unit) {
        if (!Verktex.isInitialized) return
        Verktex.withTreeLock(block)
    }
}
