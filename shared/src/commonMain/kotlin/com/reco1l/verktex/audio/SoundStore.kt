package com.reco1l.verktex.audio

import com.reco1l.verktex.Logger
import com.reco1l.verktex.Verktex
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * A group of audio resources stored by key, released all at once with [unloadAll], e.g. the sounds of a skin
 * when another one is chosen.
 *
 * Stores are obtained from [Sounds], whose [Sounds.default] store is used wherever none is given.
 */
class SoundStore internal constructor(

    /**
     * The name the store is obtained with, see [Sounds.store].
     */
    val name: String

) {

    private val lock = SynchronizedObject()

    /**
     * The audio resources loaded in the store.
     */
    val resources: Map<String, AudioResource>
        field = mutableMapOf<String, AudioResource>()


    /**
     * Loads an audio stream from the given path.
     *
     * @param key The unique identifier for the audio stream.
     * @param path The path to the audio stream file.
     * @param isExternal Whether the path is external or internal, in some platforms this may affect
     */
    fun loadStream(key: String, path: String, isExternal: Boolean = true): AudioStream {

        val source = Verktex.audio.createStreamSource(path, isExternal)
            ?: throw IllegalArgumentException("Invalid path: $path")

        val resource = source.createStream()
        put(key, resource)
        return resource
    }

    /**
     * Loads an audio sample from the given path. Differently from [loadStream] this returns a new
     * [AudioPlayback] instance of the sample upon creation instead of the [AudioSample].
     *
     * @param key The unique identifier for the audio sample.
     * @param path The path to the audio sample file.
     * @param isExternal Whether the path is external or internal, in some platforms this may affect
     */
    fun loadSample(key: String, path: String, isExternal: Boolean = true): AudioPlayback {
        return loadSampleResource(key, path, isExternal)?.createPlayback()
            ?: throw IllegalArgumentException("Invalid path: $path")
    }

    /**
     * Loads an audio sample from the given path and returns it, null if it can't be opened or decoded.
     */
    fun loadSampleResource(key: String, path: String, isExternal: Boolean = true): AudioSample? {
        val resource = try {
            Verktex.audio.createSampleSource(path, isExternal)?.createSample()
        } catch (e: IllegalStateException) {
            Logger.w(TAG, "Failed to load the sample $path: ${e.message}")
            null
        } ?: return null

        put(key, resource)
        return resource
    }

    private fun put(key: String, resource: AudioResource) = synchronized(lock) {
        val oldResource = resources[key]
        if (oldResource != null) {
            Logger.i(TAG, "Overriding the audio resource \"$key\" of '$name' with a ${resource::class.simpleName}.")
            oldResource.dispose()
        }

        resources[key] = resource
    }

    /**
     * Releases every audio resource of the store.
     */
    fun unloadAll() = synchronized(lock) {
        resources.values.forEach { it.dispose() }
        resources.clear()
    }

    /**
     * Releases the audio resource stored under [key].
     */
    fun unload(key: String) = synchronized(lock) {
        resources.remove(key)?.dispose()
        Unit
    }

    operator fun contains(key: String) = synchronized(lock) { key in resources }

    /**
     * The sample stored under [key], null if there's none.
     */
    fun getSample(key: String): AudioSample? = synchronized(lock) { resources[key] as? AudioSample }

    /**
     * A new playback of the resource stored under [key], null if there's none.
     */
    operator fun get(key: String): AudioPlayback? {
        val resource = synchronized(lock) { resources[key] } ?: return null
        return resource.createPlayback()
    }


    private companion object {
        const val TAG = "SoundStore"
    }
}
