package com.reco1l.verktex.android.oboe

import com.reco1l.verktex.audio.AudioStream

/**
 * A stream of the Oboe driver, decoded ahead while it plays. Its frequency, speed and pitch behave as
 * the BASS stream's: the frequency is the sample rate times the pitch (and the speed with
 * [adjustPitch]), the speed changes the tempo keeping the pitch otherwise.
 */
class OboeAudioStream internal constructor(private val driver: OboeAudioDriver, bytes: ByteArray) : AudioStream() {

    @Volatile
    private var handle: Long = OboeNative.createStream(driver.engine, bytes)

    private val baseFrequency: Int

    init {
        if (handle == 0L) throw IllegalStateException("Failed to decode the stream")
        baseFrequency = OboeNative.streamSampleRate(handle)
        OboeNative.setStreamEndListener(handle, Runnable { onEnd?.invoke() })
    }


    override val isPlaying: Boolean
        get() = handle.let { it != 0L && OboeNative.status(it) == OboeNative.STATUS_PLAYING }

    override val isPaused: Boolean
        get() = handle.let { it != 0L && OboeNative.status(it) == OboeNative.STATUS_PAUSED }

    override val isStalled: Boolean
        get() = handle.let { it != 0L && OboeNative.status(it) == OboeNative.STATUS_STALLED }

    override val length: Double
        get() = handle.let { if (it == 0L) 0.0 else OboeNative.length(it) }


    // The position being heard: unlike BASS's, it accounts for the latency of the output.
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

    override var pitch = 1f
        set(value) {
            if (field == value) return
            field = value
            updateFrequency()
        }

    override var adjustPitch = false
        set(value) {
            if (field == value) return
            field = value
            updateFrequency()
        }

    override var speed = 1f
        set(value) {
            if (field == value) return
            field = value
            updateFrequency()
        }

    override var looping = false
        set(value) {
            if (field == value) return
            field = value
            handle.let { if (it != 0L) OboeNative.setLooping(it, value) }
        }

    // As BASS_ATTRIB_TEMPO_FREQ: overrides the speed and the pitch until one of them changes.
    override var frequency = 0f
        set(value) {
            field = value
            handle.let {
                if (it == 0L) return
                OboeNative.setStreamFrequency(it, value)
                OboeNative.setStreamTempo(it, 1f)
            }
        }

    override var onEnd: (() -> Unit)? = null


    init {
        frequency = baseFrequency.toFloat()
    }


    private fun updateFrequency() {
        var newFrequency = baseFrequency * pitch

        if (adjustPitch) {
            newFrequency *= speed
        }

        frequency = newFrequency
        handle.let { if (it != 0L) OboeNative.setStreamTempo(it, if (adjustPitch) 1f else speed) }
    }


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

    override fun dispose() {
        val released = handle
        handle = 0L
        if (released == 0L) return

        OboeNative.setStreamEndListener(released, null)
        OboeNative.release(driver.engine, released)
        driver.onStreamDisposed(this)
    }

    //endregion


    override fun readFFT(): FloatArray? {
        val stream = handle
        if (stream == 0L) return null

        val spectrum = FloatArray(OboeNative.FFT_BANDS)
        return if (OboeNative.readStreamFFT(stream, spectrum)) spectrum else null
    }
}
