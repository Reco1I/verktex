package com.reco1l.verktex.android.oboe

import android.content.Context
import com.reco1l.verktex.Logger
import com.reco1l.verktex.audio.AudioBackend
import com.reco1l.verktex.audio.AudioDevice
import com.reco1l.verktex.audio.AudioDriver
import com.reco1l.verktex.audio.AudioSample
import com.reco1l.verktex.audio.AudioStream
import java.io.File
import java.util.Collections

/**
 * The Oboe audio driver of Android: the engine's native audio core, played through Oboe. See
 * `docs/audio-backends.md`. Created with [create], which fails where Oboe can't be started. Internal
 * paths are read from the [context]'s assets.
 */
class OboeAudioDriver private constructor(
    private val context: Context,
    internal val engine: Long
) : AudioDriver() {

    override val backend
        get() = AudioBackend.Oboe

    override val outputLatency: Double
        get() = if (isDisposed) 0.0 else OboeNative.outputLatency(engine)

    /**
     * The rate of the output in Hz.
     */
    val outputRate: Int
        get() = if (isDisposed) 0 else OboeNative.outputRate(engine)

    private val streams: MutableSet<OboeAudioStream> = Collections.synchronizedSet(mutableSetOf())

    @Volatile
    private var isDisposed = false

    @Volatile
    private var isOutputStopped = false


    override fun createSampleSource(path: String, isExternal: Boolean): AudioSample.Source {
        return object : AudioSample.Source {
            override fun createSample(): AudioSample = OboeAudioSample(this@OboeAudioDriver, read(path, isExternal))
        }
    }

    override fun createStreamSource(path: String, isExternal: Boolean): AudioStream.Source {
        return object : AudioStream.Source {
            override fun createStream(): AudioStream = OboeAudioStream(this@OboeAudioDriver, read(path, isExternal)).also(streams::add)
        }
    }

    private fun read(path: String, isExternal: Boolean): ByteArray {
        return try {
            if (isExternal) File(path).readBytes() else context.assets.open(path).use { it.readBytes() }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to read $path", e)
        }
    }

    internal fun onStreamDisposed(stream: OboeAudioStream) {
        streams.remove(stream)
    }


    /**
     * Called by the host when its activity stops: the output is closed unless a stream plays, in the
     * background then.
     */
    fun onHostStopped() {
        if (isDisposed || synchronized(streams) { streams.any { it.isPlaying } }) return
        OboeNative.stopOutput(engine)
        isOutputStopped = true
    }

    /**
     * Called by the host when its activity starts again.
     */
    fun onHostStarted() {
        if (isDisposed || !isOutputStopped) return
        isOutputStopped = false
        if (!OboeNative.startOutput(engine)) {
            Logger.w("OboeAudioDriver", "Couldn't start the output again")
        }
    }


    // Oboe plays on the device the system routes the stream to.
    override fun onEnumerateDevices(): List<AudioDevice> = listOf(OboeAudioDevice)

    override fun dispose() {
        if (isDisposed) return
        super.dispose()
        isDisposed = true
        OboeNative.destroyEngine(engine)
    }


    private object OboeAudioDevice : AudioDevice() {

        override val name = "Default"

        override val isDefault = true

        // The output was started with the driver.
        override fun onInitialize() = Unit
    }


    companion object {

        /**
         * Creates the driver and starts its output, null where the native library can't be loaded or the
         * output can't be started.
         */
        fun create(context: Context): OboeAudioDriver? {
            if (!OboeNative.isLoaded) return null

            val engine = OboeNative.createEngine()
            if (!OboeNative.startOutput(engine)) {
                Logger.w("OboeAudioDriver", "Couldn't start the Oboe output")
                OboeNative.destroyEngine(engine)
                return null
            }
            return OboeAudioDriver(context.applicationContext, engine)
        }
    }
}
