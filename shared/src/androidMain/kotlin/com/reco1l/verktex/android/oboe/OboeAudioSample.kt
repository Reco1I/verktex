package com.reco1l.verktex.android.oboe

import com.reco1l.verktex.Logger
import com.reco1l.verktex.audio.AudioPlayback
import com.reco1l.verktex.audio.AudioSample

/**
 * A sample of the Oboe driver, decoded whole. Its playbacks are released with it, as BASS frees the
 * channels of a sample with it.
 */
class OboeAudioSample internal constructor(private val driver: OboeAudioDriver, bytes: ByteArray) : AudioSample() {

    private val handle: Long = OboeNative.decodeSample(bytes)

    private val playbacks = mutableListOf<Playback>()

    @Volatile
    private var isDisposed = false

    init {
        if (handle == 0L) throw IllegalStateException("Failed to decode the sample")
    }


    override fun createPlayback(): AudioPlayback {
        synchronized(playbacks) {
            check(!isDisposed) { "The sample was disposed" }
            return Playback(OboeNative.createSamplePlayback(driver.engine, handle)).also(playbacks::add)
        }
    }

    override fun dispose() {
        synchronized(playbacks) {
            if (isDisposed) return
            isDisposed = true
            playbacks.forEach { it.release() }
            playbacks.clear()
        }
        OboeNative.freeSample(handle)
    }


    inner class Playback internal constructor(handle: Long) : AudioPlayback {

        @Volatile
        private var handle = handle

        internal fun release() {
            val released = handle
            handle = 0L
            if (released != 0L) OboeNative.release(driver.engine, released)
        }

        override val isPlaying: Boolean
            get() = handle.let { it != 0L && OboeNative.status(it) == OboeNative.STATUS_PLAYING }

        override val isPaused: Boolean
            get() = handle.let { it != 0L && OboeNative.status(it) == OboeNative.STATUS_PAUSED }

        override val isStalled: Boolean
            get() = handle.let { it != 0L && OboeNative.status(it) == OboeNative.STATUS_STALLED }

        override val length: Double
            get() = handle.let { if (it == 0L) 0.0 else OboeNative.length(it) }


        //region Settings

        override var position: Double
            get() = handle.let { if (it == 0L) 0.0 else OboeNative.position(it) }
            set(value) {
                if (value < 0.0) throw IllegalArgumentException("Position cannot be negative")
                handle.let { if (it != 0L) OboeNative.setPosition(it, value) }
            }

        override var volume: Float
            get() = handle.let { if (it == 0L) 0f else OboeNative.volume(it) }
            set(value) {
                handle.let { if (it != 0L) OboeNative.setVolume(it, value) }
            }

        // As with BASS, the pitch is the rate the sample plays at.
        override var pitch = 1f
            set(value) {
                if (field == value) return
                field = value
                handle.let { if (it != 0L) OboeNative.setSamplePlaybackRate(it, value) }
            }

        override var looping = false
            set(value) {
                if (field == value) return
                field = value
                handle.let { if (it != 0L) OboeNative.setLooping(it, value) }
            }

        //region Unsupported

        override var speed = 1f
            @Suppress("SetterBackingFieldAssignment")
            set(value) {
                Logger.w("OboeAudioSample", "Speed is always 1 for audio samples, as with BASS")
            }

        override var adjustPitch = true
            @Suppress("SetterBackingFieldAssignment")
            set(value) {
                Logger.w("OboeAudioSample", "adjustPitch is always true for audio samples, as with BASS")
            }

        //endregion

        //endregion


        //region Controls

        override fun play(restart: Boolean) {
            if (isPlaying && !restart) return
            handle.let { if (it != 0L) OboeNative.play(it, restart) }
        }

        override fun pause() {
            if (!isPlaying) return
            handle.let { if (it != 0L) OboeNative.pause(it) }
        }

        override fun stop() {
            handle.let { if (it != 0L) OboeNative.stop(it) }
        }

        //endregion
    }
}
