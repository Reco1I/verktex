package com.reco1l.verktex.ui

import kotlin.jvm.JvmOverloads
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.math.ColorInterpolation
import com.reco1l.verktex.math.Easing

/**
 * Called when a transform or a step of a choreography has finished.
 */
fun interface OnTransformFinished {
    operator fun invoke(component: Component)
}

/**
 * A float a transform can animate that isn't one of the properties of [Component], e.g. the opacity of a highlight or
 * the fraction of a size, see [Component.animate]. The instance identifies the property: a transform on it replaces
 * the ones on the same instance that started before.
 */
interface AnimatableFloat {
    var value: Float
}

/**
 * A color a transform can animate that isn't [Component.color], e.g. a background or a border, see
 * [Component.animateColor]. All four channels are animated. The instance identifies the property, as with
 * [AnimatableFloat].
 */
interface AnimatableColor {
    var value: Color4
}

/**
 * Creates an [AnimatableFloat] reading and writing through [get] and [set].
 */
inline fun animatableFloat(crossinline get: () -> Float, crossinline set: (Float) -> Unit) = object : AnimatableFloat {
    override var value: Float
        get() = get()
        set(value) = set(value)
}

/**
 * Creates an [AnimatableColor] reading and writing through [get] and [set].
 */
inline fun animatableColor(crossinline get: () -> Color4, crossinline set: (Color4) -> Unit) = object : AnimatableColor {
    override var value: Color4
        get() = get()
        set(value) = set(value)
}

/**
 * A single timed change of a property of a [Component]. Created by the transform functions such as
 * [scaleTo] and advanced by [Component.update].
 *
 * Only the latest transform that started on a property applies: one starting replaces those that started before it on
 * the same property, which are removed without calling their callbacks. Retargeting a running animation is then just
 * adding a new transform, which starts from the current value.
 */
class Transform internal constructor(
    internal val target: Component,
    private val kind: Kind,
    internal val startTime: Float,
    private val duration: Float,
    private var easing: Easing,
    private val endX: Float = 0f,
    private val endY: Float = 0f,
    private val endColor: Color4 = Color4.White,
    private val floatProperty: AnimatableFloat? = null,
    private val colorProperty: AnimatableColor? = null
) {

    private var hasStarted = false
    private var startX = 0f
    private var startY = 0f
    private var startColor = Color4.White

    internal var onFinished: OnTransformFinished? = null
        private set

    internal val endTime
        get() = startTime + duration


    /**
     * Sets the callback called when this transform has finished.
     */
    fun after(onFinished: OnTransformFinished): Transform {
        this.onFinished = onFinished
        return this
    }

    /**
     * Sets the easing of this transform.
     */
    fun eased(easing: Easing): Transform {
        this.easing = easing
        return this
    }

    /**
     * Returns a sequence whose next transforms start when this one ends.
     */
    fun then() = TransformSequence(target, endTime)


    /**
     * Whether [other], which started after this one, replaces it: it changes every property this one does.
     */
    internal fun isReplacedBy(other: Transform): Boolean {
        val custom: Any? = floatProperty ?: colorProperty
        if (custom != null) {
            return custom === (other.floatProperty ?: other.colorProperty)
        }
        val properties = kind.properties
        return properties != 0 && properties and other.kind.properties == properties
    }

    /**
     * Applies this transform at the given time.
     *
     * @return Whether it has finished.
     */
    internal fun apply(time: Float): Boolean {
        if (time < startTime) {
            return false
        }

        if (!hasStarted) {
            captureStartValues()
            hasStarted = true
        }

        val hasEnded = duration <= 0f || time >= endTime
        val progress = if (hasEnded) 1f else easing.interpolate((time - startTime) / duration).coerceIn(0f, 1f)

        when (kind) {
            Kind.Delay -> Unit
            Kind.Scale -> {
                target.scaleX = lerp(startX, endX, progress)
                target.scaleY = lerp(startY, endY, progress)
            }
            Kind.ScaleX -> target.scaleX = lerp(startX, endX, progress)
            Kind.ScaleY -> target.scaleY = lerp(startX, endX, progress)
            Kind.Alpha -> target.alpha = lerp(startX, endX, progress)
            Kind.Rotation -> target.rotationZ = lerp(startX, endX, progress)
            Kind.TranslationX -> target.translationX = lerp(startX, endX, progress).px
            Kind.TranslationY -> target.translationY = lerp(startX, endX, progress).px
            Kind.Translation -> {
                target.translationX = lerp(startX, endX, progress).px
                target.translationY = lerp(startY, endY, progress).px
            }
            // Only the RGB channels are animated so it can run alongside an alpha transform.
            Kind.Color -> target.color = ColorInterpolation.colorAt(progress, startColor, endColor, 0f, 1f).alpha(target.alpha)
            Kind.CustomFloat -> floatProperty!!.value = lerp(startX, endX, progress)
            Kind.CustomColor -> colorProperty!!.value = ColorInterpolation.colorAt(progress, startColor, endColor, 0f, 1f)
        }

        return hasEnded
    }

    /**
     * Takes the values it starts from now if it already started, e.g. when it retargets a running animation: the
     * transform it replaces would otherwise move them further before this one is first applied.
     */
    internal fun startIfDue(time: Float) {
        if (!hasStarted && time >= startTime) {
            captureStartValues()
            hasStarted = true
        }
    }

    private fun captureStartValues() {
        when (kind) {
            Kind.Delay -> Unit
            Kind.Scale -> {
                startX = target.scaleX
                startY = target.scaleY
            }
            Kind.ScaleX -> startX = target.scaleX
            Kind.ScaleY -> startX = target.scaleY
            Kind.Alpha -> startX = target.alpha
            Kind.Rotation -> startX = target.rotationZ
            Kind.TranslationX -> startX = target.translationX.resolve(target.bounds.width)
            Kind.TranslationY -> startX = target.translationY.resolve(target.bounds.height)
            Kind.Translation -> {
                startX = target.translationX.resolve(target.bounds.width)
                startY = target.translationY.resolve(target.bounds.height)
            }
            Kind.Color -> startColor = target.color
            Kind.CustomFloat -> startX = floatProperty!!.value
            Kind.CustomColor -> startColor = colorProperty!!.value
        }
    }

    private fun lerp(start: Float, end: Float, progress: Float) = start + (end - start) * progress


    // The properties of the component each kind changes, as bits, to know which transforms replace which.
    internal enum class Kind(val properties: Int) {
        Delay(0),
        Scale(0b11),
        ScaleX(0b1),
        ScaleY(0b10),
        Alpha(0b100),
        Rotation(0b1000),
        TranslationX(0b10000),
        TranslationY(0b100000),
        Translation(0b110000),
        Color(0b1000000),
        CustomFloat(0),
        CustomColor(0)
    }
}


/**
 * The transforms running on a [Component].
 */
internal class TransformTracker {

    // The time of the engine clock, which restarts when no transforms are left. A custom clock is used as it is.
    private var accumulatedTime = 0f

    // Sorted by start time, those starting at the same time in the order they were added.
    private val transforms = ArrayList<Transform>()

    private val finished = ArrayList<Transform>()

    // The transforms added while recording, see recordAdded.
    private var recording: ArrayList<Transform>? = null


    /**
     * The current time for the transforms of [target], in seconds.
     */
    fun currentTime(target: Component) = target.activeClock?.time ?: accumulatedTime

    fun add(transform: Transform): Transform {
        var index = transforms.size
        while (index > 0 && transforms[index - 1].startTime > transform.startTime) {
            index--
        }
        transforms.add(index, transform)
        recording?.add(transform)
        transform.startIfDue(currentTime(transform.target))
        return transform
    }

    val hasTransforms
        get() = transforms.isNotEmpty()

    fun clear() {
        transforms.clear()
    }

    /**
     * Runs [block] and returns the transforms it added.
     */
    fun recordAdded(block: () -> Unit): List<Transform> {
        val previous = recording
        val added = ArrayList<Transform>()
        recording = added
        try {
            block()
        } finally {
            recording = previous
        }
        previous?.addAll(added)
        return added
    }

    /**
     * Removes [cancelled] if they're still running, without calling their callbacks.
     */
    fun cancel(cancelled: List<Transform>) {
        if (cancelled.isNotEmpty()) {
            transforms.removeAll(cancelled.toSet())
        }
    }

    fun update(target: Component, elapsedTime: Float) {
        if (transforms.isEmpty()) {
            accumulatedTime = 0f
            return
        }

        accumulatedTime += elapsedTime

        val time = currentTime(target)

        // Callbacks can add transforms starting right away, which are applied in the same frame. Bounded in case
        // callbacks keep adding transforms that finish at once.
        for (pass in 0 until MAX_PASSES) {
            // In start order, so the latest one to start wins where they overlap, and one that ended
            // before the next on its property started applies its end value first, for the next to
            // start from it.
            for (i in transforms.indices) {
                val transform = transforms[i]
                if (transform.apply(time)) {
                    finished.add(transform)
                }
            }

            // Before the finished ones are removed, which still replace those started before them.
            removeReplaced(time)

            if (finished.isEmpty()) {
                return
            }

            // Replaced ones are gone already, they had not reached their end when replaced.
            finished.retainAll(transforms.toSet())
            transforms.removeAll(finished.toSet())

            // Called once the list is consistent, they can add or clear transforms.
            val count = transforms.size
            for (i in finished.indices) {
                finished[i].onFinished?.invoke(target)
            }
            finished.clear()

            if (transforms.size <= count) {
                return
            }
        }
    }

    // Removes the started transforms that a transform started after them replaces.
    private fun removeReplaced(time: Float) {
        var i = transforms.size - 2
        while (i >= 0) {
            val transform = transforms[i]

            if (transform.startTime <= time) {
                for (j in i + 1 until transforms.size) {
                    val other = transforms[j]
                    // One that ended before the other started just finished, its callback is called.
                    if (other.startTime <= time && other.startTime < transform.endTime && transform.isReplacedBy(other)) {
                        transforms.removeAt(i)
                        break
                    }
                }
            }
            i--
        }
    }

    private companion object {
        const val MAX_PASSES = 4
    }
}


/**
 * A group of transforms on the same [Component]. Transforms added one after another run at the same
 * time, use [then] or [delay] to make the next ones wait.
 */
class TransformSequence internal constructor(private val target: Component, private var cursor: Float) {

    private var last: Transform? = null


    private fun add(
        kind: Transform.Kind,
        duration: Float,
        easing: Easing,
        x: Float = 0f,
        y: Float = 0f,
        color: Color4 = Color4.White,
        floatProperty: AnimatableFloat? = null,
        colorProperty: AnimatableColor? = null
    ): TransformSequence {
        val transform = Transform(target, kind, cursor, duration, easing, x, y, color, floatProperty, colorProperty)
        target.transformTracker().add(transform)

        val currentLast = last
        if (currentLast == null || transform.endTime >= currentLast.endTime) {
            last = transform
        }
        return this
    }


    fun scaleTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
        add(Transform.Kind.Scale, duration, easing, value, value)

    fun scaleXTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
        add(Transform.Kind.ScaleX, duration, easing, value)

    fun scaleYTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
        add(Transform.Kind.ScaleY, duration, easing, value)

    fun fadeTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
        add(Transform.Kind.Alpha, duration, easing, value)

    fun fadeIn(duration: Float = 0f, easing: Easing = Easing.None) = fadeTo(1f, duration, easing)

    fun fadeOut(duration: Float = 0f, easing: Easing = Easing.None) = fadeTo(0f, duration, easing)

    fun moveTo(x: Float, y: Float, duration: Float = 0f, easing: Easing = Easing.None) =
        add(Transform.Kind.Translation, duration, easing, x, y)

    fun moveToX(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
        add(Transform.Kind.TranslationX, duration, easing, value)

    fun moveToY(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
        add(Transform.Kind.TranslationY, duration, easing, value)

    fun rotateTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
        add(Transform.Kind.Rotation, duration, easing, value)

    fun colorTo(color: Color4, duration: Float = 0f, easing: Easing = Easing.None) =
        add(Transform.Kind.Color, duration, easing, color = color)

    fun animate(property: AnimatableFloat, value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
        add(Transform.Kind.CustomFloat, duration, easing, value, floatProperty = property)

    fun animateColor(property: AnimatableColor, color: Color4, duration: Float = 0f, easing: Easing = Easing.None) =
        add(Transform.Kind.CustomColor, duration, easing, color = color, colorProperty = property)

    /**
     * Makes the next transforms start [duration] seconds after the current position of the sequence.
     */
    fun delay(duration: Float): TransformSequence {
        add(Transform.Kind.Delay, duration, Easing.None)
        cursor += duration
        return this
    }

    /**
     * Makes the next transforms start once all the previous ones have ended.
     */
    fun then(): TransformSequence {
        last?.let { cursor = it.endTime }
        return this
    }

    /**
     * Sets the callback called when the last transform of this sequence to end has finished.
     */
    fun after(onFinished: OnTransformFinished): TransformSequence {
        if (last == null) {
            add(Transform.Kind.Delay, 0f, Easing.None)
        }
        last!!.after(onFinished)
        return this
    }
}


private fun Component.addTransform(
    kind: Transform.Kind,
    duration: Float,
    easing: Easing,
    x: Float = 0f,
    y: Float = 0f,
    color: Color4 = Color4.White,
    floatProperty: AnimatableFloat? = null,
    colorProperty: AnimatableColor? = null
): Transform {
    val tracker = transformTracker()
    return tracker.add(Transform(this, kind, tracker.currentTime(this), duration, easing, x, y, color, floatProperty, colorProperty))
}

/**
 * Creates a group of transforms starting after [delay] seconds, see [TransformSequence]. A negative delay
 * makes them start in the past, as if they were already running.
 */
inline fun Component.transformSequence(delay: Float = 0f, block: TransformSequence.() -> Unit): TransformSequence {
    return newTransformSequence(delay).apply(block)
}

@PublishedApi
internal fun Component.newTransformSequence(delay: Float): TransformSequence {
    return TransformSequence(this, transformTracker().currentTime(this) + delay)
}

/**
 * Animates [Component.scale] to [value], in seconds.
 */
@JvmOverloads
fun Component.scaleTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    addTransform(Transform.Kind.Scale, duration, easing, value, value)

/**
 * Animates [Component.scaleX] to [value], in seconds.
 */
@JvmOverloads
fun Component.scaleXTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    addTransform(Transform.Kind.ScaleX, duration, easing, value)

/**
 * Animates [Component.scaleY] to [value], in seconds.
 */
@JvmOverloads
fun Component.scaleYTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    addTransform(Transform.Kind.ScaleY, duration, easing, value)

/**
 * Animates [Component.alpha] to [value], in seconds.
 */
@JvmOverloads
fun Component.fadeTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    addTransform(Transform.Kind.Alpha, duration, easing, value)

/**
 * Animates [Component.alpha] to 1, in seconds.
 */
@JvmOverloads
fun Component.fadeIn(duration: Float = 0f, easing: Easing = Easing.None) = fadeTo(1f, duration, easing)

/**
 * Animates [Component.alpha] to 0, in seconds.
 */
@JvmOverloads
fun Component.fadeOut(duration: Float = 0f, easing: Easing = Easing.None) = fadeTo(0f, duration, easing)

/**
 * Animates [Component.translationX] and [Component.translationY] to the given pixel values, in seconds.
 */
@JvmOverloads
fun Component.moveTo(x: Float, y: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    addTransform(Transform.Kind.Translation, duration, easing, x, y)

/**
 * Animates [Component.translationX] to the given pixel value, in seconds.
 */
@JvmOverloads
fun Component.moveToX(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    addTransform(Transform.Kind.TranslationX, duration, easing, value)

/**
 * Animates [Component.translationY] to the given pixel value, in seconds.
 */
@JvmOverloads
fun Component.moveToY(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    addTransform(Transform.Kind.TranslationY, duration, easing, value)

/**
 * Animates [Component.rotationZ] to [value] degrees, in seconds.
 */
@JvmOverloads
fun Component.rotateTo(value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    addTransform(Transform.Kind.Rotation, duration, easing, value)

/**
 * Animates the RGB channels of [Component.color] to [color], in seconds.
 */
@JvmOverloads
fun Component.colorTo(color: Color4, duration: Float = 0f, easing: Easing = Easing.None) =
    addTransform(Transform.Kind.Color, duration, easing, color = color)

/**
 * Animates a [property] that isn't one of the component's own to [value], in seconds, on the clock of this component.
 */
@JvmOverloads
fun Component.animate(property: AnimatableFloat, value: Float, duration: Float = 0f, easing: Easing = Easing.None) =
    addTransform(Transform.Kind.CustomFloat, duration, easing, value, floatProperty = property)

/**
 * Animates a color [property] that isn't [Component.color] to [color], all four channels, in seconds.
 */
@JvmOverloads
fun Component.animateColor(property: AnimatableColor, color: Color4, duration: Float = 0f, easing: Easing = Easing.None) =
    addTransform(Transform.Kind.CustomColor, duration, easing, color = color, colorProperty = property)

/**
 * Does nothing for [duration] seconds. Useful to run code later through [Transform.after].
 */
fun Component.delay(duration: Float) = addTransform(Transform.Kind.Delay, duration, Easing.None)

/**
 * Stops all transforms of this component, leaving its properties at their current values. Their
 * [Transform.after] callbacks are not called.
 */
fun Component.clearTransforms() {
    transforms?.clear()
}


/**
 * The transforms one animation of a component started, e.g. expanding or collapsing it. Running it again cancels those
 * still running or waiting, without calling their callbacks, and leaves the other transforms of the component alone:
 *
 * ```
 * private val expansion = transitionSlot()
 *
 * var isExpanded by Delegates.observable(false) { _, _, expanded ->
 *     expansion.run { rotateTo(if (expanded) 0f else 180f, 0.2f, Easing.OutQuint) }
 * }
 * ```
 */
class TransitionSlot internal constructor(private val target: Component) {

    private var running: List<Transform> = emptyList()

    /**
     * Cancels what the previous run started and runs [block], whose transforms this slot then tracks.
     */
    fun run(block: Component.() -> Unit) {
        val tracker = target.transformTracker()
        tracker.cancel(running)
        running = tracker.recordAdded { target.block() }
    }

    /**
     * Cancels what the last run started, leaving the properties at their current values.
     */
    fun cancel() {
        target.transforms?.cancel(running)
        running = emptyList()
    }
}

/**
 * Creates a [TransitionSlot] for an animation of this component.
 */
fun Component.transitionSlot() = TransitionSlot(this)
