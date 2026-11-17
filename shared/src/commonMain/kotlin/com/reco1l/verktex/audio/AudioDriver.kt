package com.reco1l.verktex.audio

import com.reco1l.verktex.Logger

/**
 * Represents an audio driver that can be used to play audio.
 */
abstract class AudioDriver {

    /**
     * List of all available audio devices.
     */
    var devices: List<AudioDevice> = listOf()
        private set

    /**
     * The currently active [AudioDevice] for playback.
     */
    var activeDevice: AudioDevice? = null
        set(value) {
            if (field == value) return
            field?.onDeactivate()
            field = value

            if (value != null) {
                if (!value.isInitialized) {
                    value.initialize()
                }
                value.onActivate()
            }
            onDeviceChanged()
        }


    /**
     * The library this driver plays audio with.
     */
    abstract val backend: AudioBackend

    /**
     * The time in seconds between a frame being played and it being heard, as far as the driver knows
     * it, 0 where it doesn't. Positions of streams don't account for it, on any driver.
     */
    open val outputLatency: Double
        get() = 0.0


    /**
     * Creates the source of a sample from a file.
     *
     * @param path The path to the file.
     * @param isExternal Whether the path is external or internal, see [com.reco1l.verktex.Provider.exists].
     */
    abstract fun createSampleSource(path: String, isExternal: Boolean): AudioSample.Source?

    /**
     * Creates the source of a stream from a file.
     *
     * @param path The path to the file.
     * @param isExternal Whether the path is external or internal, see [com.reco1l.verktex.Provider.exists].
     */
    abstract fun createStreamSource(path: String, isExternal: Boolean): AudioStream.Source?

    /**
     * Creates the source of a stream that plays from a URL while it's downloaded, or null where the
     * driver can't.
     */
    open fun createUrlStreamSource(url: String): AudioStream.Source? = null


    /**
     * Called when the active device has changed.
     */
    protected open fun onDeviceChanged() {}


    /**
     * Enumerates all available audio devices.
     */
    fun enumerateDevices(): List<AudioDevice> {
        val current = activeDevice

        // Enumerating again keeps the device in use: the one of the same name is replaced by it.
        devices = onEnumerateDevices().map { device ->
            if (current != null && device::class == current::class && device.name == current.name) current else device
        }

        if (devices.isEmpty()) {
            Logger.w("AudioBackend", "No audio devices found! Working without sound.")
        }

        activeDevice = current?.takeIf { it in devices }
            ?: devices.firstOrNull { it.isDefault }
            ?: devices.firstOrNull()
        return devices
    }


    /**
     * Called when devices are enumerated after calling [enumerateDevices].
     */
    abstract fun onEnumerateDevices(): List<AudioDevice>

    /**
     * Releases the audio backend, no audio can be played afterwards.
     */
    open fun dispose() {
        activeDevice = null
    }

}