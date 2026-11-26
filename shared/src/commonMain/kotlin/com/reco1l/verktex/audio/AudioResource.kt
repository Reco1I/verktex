package com.reco1l.verktex.audio

/**
 * Represents an audio resource that can be played.
 *
 * It is a chunk of audio data that depending on its type it can be played from memory or streamed
 * from a file.
 *
 * @see AudioSample
 * @see AudioStream
 */
sealed interface AudioResource {

    /**
     * Disposes this resource.
     */
    fun dispose()

    /**
     * Creates and returns a new [AudioPlayback] instance from this resource.
     */
    fun createPlayback(): AudioPlayback

}

/**
 * Represents an audio buffer that can be played from memory.
 */
abstract class AudioSample : AudioResource {

    /**
     * Represents the source of an audio sample.
     */
    interface Source {

        /**
         * Creates a new instance of [AudioSample] based on this source.
         */
        fun createSample(): AudioSample
    }
}

/**
 * Represents an audio buffer that can be streamed from a file.
 *
 * An AudioStream also acts as a [AudioPlayback] since streams can only have one playback instance.
 */
abstract class AudioStream : AudioResource, AudioPlayback {

    /**
     * Called when the stream reaches its end. It may be called from an audio thread.
     */
    abstract var onEnd: (() -> Unit)?

    /**
     * The frequency the stream plays at in Hz, usually derived from [speed], [pitch] and [adjustPitch]. Setting
     * it overrides them until one of them changes.
     */
    abstract var frequency: Float

    /**
     * Reads the current frequency spectrum, or null if the stream isn't playing.
     */
    abstract fun readFFT(): FloatArray?

    /**
     * The cutoff of a low-pass filter on the stream in Hz, which muffles it as it goes down. At or
     * above [MAX_LOW_PASS_CUTOFF], the default, there's no filter. It can be changed every frame,
     * e.g. animated.
     */
    open var lowPassCutoff: Float = MAX_LOW_PASS_CUTOFF

    /**
     * The cutoff of a high-pass filter on the stream in Hz, which thins it as it goes up. At 0, the
     * default, there's no filter. It can be changed every frame, e.g. animated.
     */
    open var highPassCutoff: Float = 0f


    override fun createPlayback(): AudioPlayback {
        return this
    }

    companion object {
        /**
         * The [lowPassCutoff] at and above which there's no low-pass filter.
         */
        const val MAX_LOW_PASS_CUTOFF = 22050f

        /**
         * The quality factor of the filters, a Butterworth response: flat, without resonance.
         */
        const val FILTER_Q = 0.70710677f
    }

    /**
     * Represents the source of an audio stream.
     */
    interface Source {
        /**
         * Creates a new instance of [AudioStream] based on this source.
         */
        fun createStream(): AudioStream
    }
}




