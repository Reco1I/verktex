package com.reco1l.verktex.audio

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * The sound stores of the application, see [SoundStore]. Sounds go to the [default] store unless one is given:
 * its members are the default store's.
 */
object Sounds {

    private val stores = mutableMapOf<String, SoundStore>()
    private val lock = SynchronizedObject()

    /**
     * The store used when none is given.
     */
    val default = SoundStore("default")


    /**
     * The store named [name], created when it's first asked for.
     */
    fun store(name: String): SoundStore = synchronized(lock) {
        if (name == default.name) default else stores.getOrPut(name) { SoundStore(name) }
    }

    /**
     * Releases every audio resource of the store named [name] and forgets it.
     */
    fun remove(name: String) {
        val store = synchronized(lock) { stores.remove(name) } ?: return
        store.unloadAll()
    }

    //region Default store

    /**
     * The audio resources of the [default] store.
     */
    val resources: Map<String, AudioResource>
        get() = default.resources

    /**
     * Loads an audio stream into the [default] store, see [SoundStore.loadStream].
     */
    fun loadStream(key: String, path: String, isExternal: Boolean = true): AudioStream = default.loadStream(key, path, isExternal)

    /**
     * Loads an audio sample into the [default] store, see [SoundStore.loadSample].
     */
    fun loadSample(key: String, path: String, isExternal: Boolean = true): AudioPlayback = default.loadSample(key, path, isExternal)

    /**
     * Releases every audio resource of every store.
     */
    fun unloadAll() {
        default.unloadAll()
        synchronized(lock) { stores.values.toList() }.forEach { it.unloadAll() }
    }

    /**
     * Releases an audio resource of the [default] store.
     */
    fun unload(key: String) = default.unload(key)

    operator fun get(key: String): AudioPlayback? = default[key]

    //endregion
}
