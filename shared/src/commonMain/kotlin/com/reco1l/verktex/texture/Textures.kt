package com.reco1l.verktex.texture

import com.reco1l.verktex.graphics.Texture
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * The texture stores of the application, see [TextureStore]. Textures go to the [default] store unless one is
 * given: its members are the default store's, so `Textures.load(key, path)` stores a texture there.
 */
object Textures {

    private val stores = mutableMapOf<String, TextureStore>()
    private val lock = SynchronizedObject()

    /**
     * The store used when none is given.
     */
    val default = TextureStore("default")


    /**
     * Goes up every time a region is stored, replaced or removed in any store, so a [TextureReference.Lookup]
     * looks its region up again only after a store changed.
     */
    var revision = 0
        private set

    internal fun onChanged() {
        revision++
    }


    /**
     * The store named [name], created when it's first asked for.
     */
    fun store(name: String): TextureStore = synchronized(lock) {
        if (name == default.name) default else stores.getOrPut(name) { TextureStore(name) }
    }

    /**
     * Unloads every texture of the store named [name] and forgets it.
     */
    fun remove(name: String) {
        val store = synchronized(lock) { stores.remove(name) } ?: return
        store.unloadAll()
    }

    /**
     * Forgets every texture of every store without releasing it, for when the graphics context they belong to
     * was destroyed along with them.
     */
    internal fun clear() {
        default.forget()
        synchronized(lock) { stores.values.toList() }.forEach { it.forget() }
    }

    //region Default store

    /**
     * Loads a texture from a file into the [default] store, see [TextureStore.load].
     */
    fun load(
        key: String,
        path: String,
        isInternal: Boolean = false,
        destinationAtlas: Texture? = null,
        density: Float = 1f
    ): Texture.Region? = default.load(key, path, isInternal, destinationAtlas, density)

    /**
     * Loads a texture from a source into the [default] store, see [TextureStore.load].
     */
    fun load(
        key: String,
        source: Texture.Source,
        standalone: Boolean = false,
        destinationAtlas: Texture? = null,
        density: Float = 1f
    ): Texture.Region? = default.load(key, source, standalone, destinationAtlas, density)

    /**
     * Unloads a texture of the [default] store, see [TextureStore.unload].
     */
    fun unload(key: String) = default.unload(key)

    /**
     * Unloads a texture of the [default] store, see [TextureStore.unload].
     */
    fun unload(region: Texture.Region) = default.unload(region)

    /**
     * Unloads the textures of the [default] store that match the predicate, see [TextureStore.unloadAll].
     */
    fun unloadAll(predicate: (key: String, region: Texture.Region) -> Boolean) = default.unloadAll(predicate)

    operator fun contains(key: String): Boolean = key in default

    operator fun get(key: String): Texture.Region? = default[key]

    /**
     * Returns the keys of every texture of the [default] store.
     */
    fun getTextureKeys(): Set<String> = default.getTextureKeys()

    //endregion
}
