package com.reco1l.verktex.ui

import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.texture.FramesReference
import com.reco1l.verktex.texture.TextureReference
import com.reco1l.verktex.texture.reference
import com.reco1l.verktex.time.FrameClock
import com.reco1l.verktex.ui.container.UIContainer


inline fun UIContainer.animatedSprite(builder: UIAnimatedSprite.() -> Unit): UIAnimatedSprite {
    return UIAnimatedSprite().apply(builder).also(::plusAssign)
}

/**
 * Sprite that plays a sequence of texture regions as an animation.
 *
 * Its [animation] can be frames given directly or ones an application looks up its own way
 * ([FramesReference.Lookup]), which the sprite follows as they change, frame count included.
 */
open class UIAnimatedSprite(animation: FramesReference? = null) : UISprite() {

    constructor(frames: List<GraphicsTexture.Region>) : this(frames.reference)

    /**
     * Creates an animated sprite playing [animation] at [framesPerSecond]. When it is 0 the amount of
     * frames is used instead, so the whole animation takes one second, whatever amount it has.
     */
    constructor(animation: FramesReference?, framesPerSecond: Float) : this(animation) {
        if (framesPerSecond > 0f) {
            frameTime = 1f / framesPerSecond
        } else {
            isOneSecondLong = true
            fitToOneSecond()
        }
    }

    constructor(frames: List<GraphicsTexture.Region>, framesPerSecond: Float) : this(frames.reference, framesPerSecond)


    /**
     * The frames of the animation.
     *
     * Changing this value will restart the animation from the first frame.
     */
    var animation: FramesReference? = animation
        set(value) {
            if (field != value) {
                field = value
                elapsedSec = 0f
                frameIndex = 0
                onFramesChanged()
            }
        }

    /**
     * The frames [animation] resolves to now. Setting it is a shorthand for setting [animation] to
     * the frames themselves.
     */
    var frames: List<GraphicsTexture.Region>
        get() = animation?.resolve() ?: emptyList()
        set(value) {
            animation = value.reference
        }

    // Whether the frame time follows the amount of frames, so the animation takes one second.
    private var isOneSecondLong = false

    // The frames the frame time was last fitted to.
    private var fittedFrames: List<GraphicsTexture.Region>? = null

    // The sprite draws whichever frame is current, resolved again whenever the sprite is.
    private inner class CurrentFrame : TextureReference() {
        // Until the next update, the frames may have shrunk below the current index.
        override fun resolve() = frames.let { it.getOrNull(frameIndex.coerceAtMost(it.lastIndex)) }
    }

    init {
        texture = CurrentFrame()
    }


    /**
     * The duration of each frame in seconds.
     */
    var frameTime = DEFAULT_FRAME_TIME
        set(value) {
            require(value > 0f) { "The frame time must be greater than 0." }
            field = value
        }

    /**
     * Whether the animation should loop.
     */
    var isLoop = true

    /**
     * The elapsed time since the animation started.
     */
    var elapsedSec = 0f

    /**
     * Whether the animation is playing.
     */
    var isPlaying = true
        private set

    /**
     * The current frame index.
     */
    var frameIndex = 0
        private set


    /**
     * The duration of the animation in seconds.
     */
    val duration
        get() = frames.size * frameTime


    private fun fitToOneSecond() {
        val frames = frames
        fittedFrames = frames

        if (frames.isNotEmpty()) {
            frameTime = 1f / frames.size
        }
    }

    private fun onFramesChanged() {
        if (isOneSecondLong) {
            fitToOneSecond()
        }
    }


    override fun onUpdate(clock: FrameClock) {
        val frames = frames

        if (isOneSecondLong && frames !== fittedFrames) {
            fitToOneSecond()
        }

        if (frames.isNotEmpty()) {

            if (isPlaying) {
                elapsedSec += clock.elapsedFrameTime

                if (elapsedSec >= duration) {

                    if (isLoop) {
                        elapsedSec %= duration
                    } else {
                        elapsedSec = duration
                        isPlaying = false
                    }
                }
            }

            frameIndex = (elapsedSec / frameTime).toInt().coerceIn(0, frames.size - 1)
        }

        super.onUpdate(clock)
    }


    fun play() {
        isPlaying = true
    }

    fun pause() {
        isPlaying = false
    }

    fun stop() {
        isPlaying = false
        elapsedSec = 0f
        frameIndex = 0
    }


    companion object {

        const val DEFAULT_FRAME_TIME = 1f / 60f

    }

}
