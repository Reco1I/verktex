package com.reco1l.verktex.android.oboe

import com.reco1l.verktex.Logger

/**
 * The JNI functions of `libverktex_audio` (`natives/audio/android/audio_jni.cpp`, built by
 * `:android-natives`): the engine's native audio core and its Oboe output. Handles are native
 * pointers, never used after they're released.
 */
internal object OboeNative {

    /**
     * Whether the library could be loaded, it's loaded once.
     */
    val isLoaded: Boolean by lazy {
        try {
            System.loadLibrary("verktex_audio")
            true
        } catch (e: UnsatisfiedLinkError) {
            Logger.e("OboeNative", "Couldn't load the native audio library", e)
            false
        }
    }

    // The values of the native Status, the ones of BASS_ACTIVE_*.
    const val STATUS_STOPPED = 0
    const val STATUS_PLAYING = 1
    const val STATUS_STALLED = 2
    const val STATUS_PAUSED = 3

    /**
     * The number of bands [readStreamFFT] reads.
     */
    const val FFT_BANDS = 512


    //region Engine

    @JvmStatic external fun createEngine(): Long
    @JvmStatic external fun destroyEngine(engine: Long)
    @JvmStatic external fun startOutput(engine: Long): Boolean
    @JvmStatic external fun stopOutput(engine: Long)
    @JvmStatic external fun outputLatency(engine: Long): Double
    @JvmStatic external fun outputRate(engine: Long): Int

    //endregion

    //region Samples

    /**
     * Decodes a whole file, 0 if it can't be decoded.
     */
    @JvmStatic external fun decodeSample(bytes: ByteArray): Long
    @JvmStatic external fun freeSample(sample: Long)
    @JvmStatic external fun createSamplePlayback(engine: Long, sample: Long): Long
    @JvmStatic external fun setSamplePlaybackRate(playback: Long, rate: Float)

    //endregion

    //region Streams

    /**
     * Creates a stream of a file, 0 if it can't be decoded.
     */
    @JvmStatic external fun createStream(engine: Long, bytes: ByteArray): Long
    @JvmStatic external fun streamSampleRate(stream: Long): Int
    @JvmStatic external fun setStreamFrequency(stream: Long, hz: Float)
    @JvmStatic external fun setStreamTempo(stream: Long, tempo: Float)
    @JvmStatic external fun setStreamLowPassCutoff(stream: Long, hz: Float)
    @JvmStatic external fun setStreamHighPassCutoff(stream: Long, hz: Float)
    @JvmStatic external fun readStreamFFT(stream: Long, output: FloatArray): Boolean

    /**
     * Sets what runs, on a thread of the engine, when the stream reaches its end.
     */
    @JvmStatic external fun setStreamEndListener(stream: Long, listener: Runnable?)

    //endregion

    //region Playbacks (samples' and streams)

    @JvmStatic external fun release(engine: Long, playback: Long)
    @JvmStatic external fun status(playback: Long): Int
    @JvmStatic external fun length(playback: Long): Double
    @JvmStatic external fun position(playback: Long): Double
    @JvmStatic external fun setPosition(playback: Long, seconds: Double)
    @JvmStatic external fun volume(playback: Long): Float
    @JvmStatic external fun setVolume(playback: Long, volume: Float)
    @JvmStatic external fun setLooping(playback: Long, looping: Boolean)
    @JvmStatic external fun play(playback: Long, restart: Boolean)
    @JvmStatic external fun pause(playback: Long)
    @JvmStatic external fun stop(playback: Long)

    //endregion
}
