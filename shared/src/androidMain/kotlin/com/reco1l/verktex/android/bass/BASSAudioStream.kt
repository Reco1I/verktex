package com.reco1l.verktex.android.bass

import com.reco1l.verktex.audio.AudioStream
import com.un4seen.bass.BASS
import com.un4seen.bass.BASS_FX
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BASSAudioStream(
    /**
     * The channel ID of the audio stream.
     */
    val id: Int
) : AudioStream() {

    override val isPlaying: Boolean
        get() = BASS.BASS_ChannelIsActive(id) == BASS.BASS_ACTIVE_PLAYING

    override val isPaused: Boolean
        get() = BASS.BASS_ChannelIsActive(id) == BASS.BASS_ACTIVE_PAUSED

    override val isStalled: Boolean
        get() = BASS.BASS_ChannelIsActive(id) == BASS.BASS_ACTIVE_STALLED

    override val length: Double
        get() {
            val length = BASS.BASS_ChannelGetLength(id, BASS.BASS_POS_BYTE)
            if (length == -1L) return 0.0
            return BASS.BASS_ChannelBytes2Seconds(id, length)
        }


    override var position: Double
        get() {
            val pos = BASS.BASS_ChannelGetPosition(id, BASS.BASS_POS_BYTE)
            if (pos == -1L) return 0.0

            return BASS.BASS_ChannelBytes2Seconds(id, pos)
        }
        set(value) {
            if (value < 0.0) throw IllegalArgumentException("Position cannot be negative")

            val pos = BASS.BASS_ChannelSeconds2Bytes(id, value)

            // While changing the speed the output position isn't the one that has to be moved.
            BASS.BASS_ChannelSetPosition(id, pos, if (speed == 1f) BASS.BASS_POS_BYTE else BASS.BASS_POS_DECODE)
        }

    override var volume: Float
        get() {
            BASS.BASS_ChannelGetAttribute(id, BASS.BASS_ATTRIB_VOL, FLOAT_VALUE_FETCHER)
            return FLOAT_VALUE_FETCHER.value
        }
        set(value) {
            BASS.BASS_ChannelSetAttribute(id, BASS.BASS_ATTRIB_VOL, value)
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
            BASS.BASS_ChannelFlags(id, if (value) BASS.BASS_SAMPLE_LOOP else 0, BASS.BASS_SAMPLE_LOOP)
        }


    override var frequency = 0f
        set(value) {
            field = value
            BASS.BASS_ChannelSetAttribute(id, BASS_FX.BASS_ATTRIB_TEMPO_FREQ, value)
            BASS.BASS_ChannelSetAttribute(id, BASS_FX.BASS_ATTRIB_TEMPO, 0f)
        }

    override var onEnd: (() -> Unit)? = null

    override var lowPassCutoff = MAX_LOW_PASS_CUTOFF
        set(value) {
            if (field == value) return
            field = value
            lowPassFX = applyFilter(lowPassFX, BASS_FX.BASS_BFX_BQF_LOWPASS, value, value < MAX_LOW_PASS_CUTOFF)
        }

    override var highPassCutoff = 0f
        set(value) {
            if (field == value) return
            field = value
            highPassFX = applyFilter(highPassFX, BASS_FX.BASS_BFX_BQF_HIGHPASS, value, value > 0f)
        }


    private val baseFrequency: Int

    // The handles of the filters' effects, 0 without them.
    private var lowPassFX = 0
    private var highPassFX = 0
    private val filterParameters = BASS_FX.BASS_BFX_BQF()

    private val spectrumBuffer by lazy {
        ByteBuffer.allocateDirect(FFT_SIZE shl 1).order(ByteOrder.nativeOrder())
    }


    init {
        BASS.BASS_ChannelGetInfo(id, CHANNEL_INFO_FETCHER)
        baseFrequency = CHANNEL_INFO_FETCHER.freq
        frequency = baseFrequency.toFloat()

        BASS.BASS_ChannelSetSync(id, BASS.BASS_SYNC_END, 0, { _, _, _, _ -> onEnd?.invoke() }, 0)
    }


    private fun updateFrequency() {
        var newFrequency = baseFrequency * pitch

        if (adjustPitch) {
            newFrequency *= speed
        }

        frequency = newFrequency
        BASS.BASS_ChannelSetAttribute(id, BASS_FX.BASS_ATTRIB_TEMPO, if (adjustPitch) 0f else (speed - 1f) * 100f)
    }


    /**
     * Sets, updates or removes the biquad filter [fx] of [type] at [cutoff], returning its handle.
     */
    private fun applyFilter(fx: Int, type: Int, cutoff: Float, isEnabled: Boolean): Int {
        if (!isEnabled) {
            if (fx != 0) BASS.BASS_ChannelRemoveFX(id, fx)
            return 0
        }

        val handle = if (fx != 0) fx else BASS.BASS_ChannelSetFX(id, BASS_FX.BASS_FX_BFX_BQF, 0)
        if (handle == 0) return 0

        // Animated every frame, so the parameters are reused.
        filterParameters.lFilter = type
        // Below the Nyquist frequency, as BASS_FX requires.
        filterParameters.fCenter = cutoff.coerceIn(1f, baseFrequency / 2f - 1f)
        filterParameters.fQ = FILTER_Q
        filterParameters.lChannel = BASS_FX.BASS_BFX_CHANALL
        BASS.BASS_FXSetParameters(handle, filterParameters)
        return handle
    }


    //region Controls

    override fun play(restart: Boolean) {
        if (isPlaying && !restart) return
        BASS.BASS_ChannelPlay(id, restart)
    }

    override fun pause() {
        if (!isPlaying) return
        BASS.BASS_ChannelPause(id)
    }

    override fun stop() {
        BASS.BASS_ChannelStop(id)
    }

    override fun dispose() {
        BASS.BASS_ChannelStop(id)
        BASS.BASS_ChannelFree(id)
    }

    //endregion


    override fun readFFT(): FloatArray? {
        if (!isPlaying) return null

        spectrumBuffer.clear()
        BASS.BASS_ChannelGetData(id, spectrumBuffer, BASS.BASS_DATA_FFT1024)

        val spectrum = FloatArray(FFT_SIZE shr 1)
        spectrumBuffer.asFloatBuffer().get(spectrum)
        return spectrum
    }


    companion object {
        private const val FFT_SIZE = 1024

        private val CHANNEL_INFO_FETCHER = BASS.BASS_CHANNELINFO()
        private val FLOAT_VALUE_FETCHER = BASS.FloatValue()
    }

}