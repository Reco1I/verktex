package com.reco1l.verktex.texture

import com.reco1l.verktex.Logger
import com.reco1l.verktex.Platform
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.graphics.Texture
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.math.min

/**
 * A group of textures stored by key, with atlases of its own: the textures of a store never share an atlas with
 * another store's, so a store is unloaded all at once ([unloadAll]) by releasing its atlases, e.g. the textures
 * of a skin when another one is chosen.
 *
 * Stores are obtained from [Textures], whose [Textures.default] store is used wherever none is given.
 */
class TextureStore internal constructor(

    /**
     * The name the store is obtained with, see [Textures.store].
     */
    val name: String

) {

    private val atlases = mutableListOf<Texture>()
    private val standaloneTextures = mutableListOf<Texture>()
    private val textureRegions = mutableMapOf<String, Texture.Region>()

    // Textures are loaded from any thread (e.g. while a play loads) while the update thread looks them up.
    private val lock = SynchronizedObject()


    /**
     * Forgets every texture without releasing it, for when the graphics context they belong to was destroyed
     * along with them.
     */
    internal fun forget() = synchronized(lock) {
        atlases.clear()
        standaloneTextures.clear()
        textureRegions.clear()
        Textures.onChanged()
    }

    //region Load and Unload

    /**
     * Loads a texture from the specified file path and stores it in a free texture atlas of the store.
     *
     * @param key A unique key to associate with the loaded texture.
     * @param path The file path of the texture to load.
     * @param isInternal A boolean indicating whether the file path is internal to the application.
     * @param density How many pixels of the image make one pixel of the size it's shown at, see
     * [Texture.Region.density], e.g. 2 for an `@2x` image.
     * @return A [Texture.Region] representing the loaded texture.
     */
    fun load(
        key: String,
        path: String,
        isInternal: Boolean = false,
        destinationAtlas: Texture? = null,
        density: Float = 1f
    ): Texture.Region? {

        val textureSource = Platform.provider.createTextureSource(path, !isInternal)
            ?: run {
                Logger.w(TAG, "Failed to create texture source for path: $path")
                return null
            }

        return load(key, textureSource, destinationAtlas = destinationAtlas, density = density)
    }

    /**
     * Loads a texture from an already created source and stores it under the specified key, replacing any
     * previous region with the same key.
     *
     * @param key A unique key to associate with the loaded texture.
     * @param source The texture source to upload.
     * @param standalone Whether the source gets its own texture instead of being packed into an atlas. Sources
     * larger than the atlas size are always standalone.
     * @param destinationAtlas The atlas to store the source in, if any.
     * @param density How many pixels of the source make one pixel of the size it's shown at, see
     * [Texture.Region.density].
     * @return A [Texture.Region] representing the loaded texture, or null if the source exceeds the
     * maximum texture size.
     */
    fun load(
        key: String,
        source: Texture.Source,
        standalone: Boolean = false,
        destinationAtlas: Texture? = null,
        density: Float = 1f
    ): Texture.Region? = synchronized(lock) {

        val existingRegion = textureRegions.remove(key)
        if (existingRegion != null) {
            Logger.w(TAG, "Texture with key '$key' already exists in '$name'. Overwriting...")
            release(existingRegion)
            Textures.onChanged()
        }

        // A source fits an atlas with the gutter around it, so one exactly as large as an atlas (e.g. a
        // 2048 pixels wide background) doesn't.
        val region = if (destinationAtlas == null && (standalone || !fitsEmptyAtlas(source))) {
            storeStandalone(source, density) ?: run {
                Logger.w(TAG, "Texture '$key' (${source.width}x${source.height}) exceeds the maximum texture size.")
                return null
            }
        } else {
            storeOnTextureAtlas(source, destinationAtlas, density)
        }
        textureRegions[key] = region
        Textures.onChanged()
        region
    }

    /**
     * Unloads a texture region associated with the specified key and removes it from its texture atlas.
     *
     * @param key The key associated with the texture region to unload.
     */
    fun unload(key: String): Unit = synchronized(lock) {
        val region = textureRegions.remove(key) ?: run {
            Logger.w(TAG, "Texture region with key '$key' not found in '$name'. Cannot unload.")
            return
        }

        release(region)
        Textures.onChanged()
    }

    /**
     * Unloads a texture region and removes it from its texture atlas.
     */
    fun unload(region: Texture.Region): Unit = synchronized(lock) {
        val key = textureRegions.entries.find { it.value == region }?.key ?: run {
            Logger.w(TAG, "Texture region not found in '$name'. Cannot unload.")
            return
        }
        unload(key)
    }

    /**
     * Unloads the texture regions that match the specified predicate and removes them from their texture atlases.
     * The atlases are kept for the textures loaded next.
     */
    fun unloadAll(predicate: (key: String, region: Texture.Region) -> Boolean) = synchronized(lock) {
        val keysToUnload = textureRegions.filter { predicate(it.key, it.value) }.keys.toList()

        for (key in keysToUnload) {
            unload(key)
        }
    }

    /**
     * Unloads every texture of the store at once, releasing its atlases and standalone textures.
     */
    fun unloadAll() = synchronized(lock) {
        val textures = atlases + standaloneTextures

        for (texture in textures) {
            texture.removeAllRegions()
        }

        // Regions stored in an atlas given to load (destinationAtlas), which the store doesn't own.
        for (region in textureRegions.values) {
            if (!region.isReleased) {
                region.texture.removeRegion(region)
            }
        }

        atlases.clear()
        standaloneTextures.clear()
        textureRegions.clear()
        Textures.onChanged()

        if (textures.isNotEmpty()) {
            // The GPU delete must happen on the draw worker (GL thread). The textures stay registered until then,
            // so a context loss in between still marks them and the old names are not deleted in the new context.
            Verktex.scheduleOnDraw {
                for (texture in textures) {
                    texture.dispose()
                    Verktex.graphics.unregisterResource(texture)
                }
            }
        }
    }

    /**
     * Detaches [region] from its texture, and disposes the texture if it is a standalone one.
     */
    private fun release(region: Texture.Region) {
        val texture = region.texture

        texture.removeRegion(region)

        if (standaloneTextures.remove(texture)) {
            Verktex.scheduleOnDraw {
                texture.dispose()
                Verktex.graphics.unregisterResource(texture)
            }
        }
    }

    //endregion

    //region Operators

    operator fun contains(key: String): Boolean = synchronized(lock) {
        textureRegions.containsKey(key)
    }

    operator fun get(key: String): Texture.Region? = synchronized(lock) {
        textureRegions[key]
    }

    //endregion


    /**
     * Returns the keys of every texture currently in the store.
     */
    fun getTextureKeys(): Set<String> = synchronized(lock) {
        textureRegions.keys.toSet()
    }


    private fun atlasSize(): Float = min(2048f, Verktex.graphics.device.capabilities.maxTextureSize)

    private fun fitsEmptyAtlas(source: Texture.Source): Boolean {
        val largest = atlasSize() - 2 * ATLAS_REGION_PADDING
        return source.width <= largest && source.height <= largest
    }

    /**
     * Stores a [Texture.Source] larger than an atlas on a texture of exactly its own size.
     * Returns null when the source exceeds the device's maximum texture size.
     */
    private fun storeStandalone(source: Texture.Source, density: Float): Texture.Region? {
        val maxSize = Verktex.graphics.device.capabilities.maxTextureSize

        if (source.width > maxSize || source.height > maxSize) {
            return null
        }

        val texture = Verktex.graphics.createTexture(width = source.width, height = source.height)
        // The region fills the texture, there's no room for a gutter and clamping to the edge does its job.
        texture.regionPadding = 0
        val region = texture.addSource(source, density)

        if (region == null) {
            texture.dispose()
            Verktex.graphics.unregisterResource(texture)
            return null
        }

        standaloneTextures += texture
        return region
    }

    private fun createNewAtlas(): Texture {
        val preferedSize = atlasSize()

        val newAtlas = Verktex.graphics.createTexture(
            width = preferedSize,
            height = preferedSize
        )
        newAtlas.regionPadding = ATLAS_REGION_PADDING

        atlases += newAtlas
        return newAtlas
    }

    /**
     * Stores a [Texture.Source] in an atlas of the store: in the free space of one, otherwise in one where it fits
     * once its regions are packed again (the space removed ones left), otherwise in a new one.
     */
    private fun storeOnTextureAtlas(source: Texture.Source, destinationAtlas: Texture?, density: Float): Texture.Region {

        if (destinationAtlas != null) {
            return destinationAtlas.addSource(source, density)
                ?: throw IllegalArgumentException("The specified destination atlas cannot accommodate the source.")
        }

        for (atlas in atlases) {
            if (atlas.fitsSource(source)) {
                return atlas.addSource(source, density) ?: continue
            }
        }

        for (atlas in atlases) {
            val repacked = atlas.addSourceRepacking(source, density) ?: continue

            if (repacked.replaced.isNotEmpty()) {
                for (entry in textureRegions.entries) {
                    repacked.replaced[entry.value]?.let { entry.setValue(it) }
                }
            }
            return repacked.region
        }

        val newAtlas = createNewAtlas()

        return newAtlas.addSource(source, density) ?: throw IllegalStateException("Failed to allocate texture source on new atlas.")
    }


    private companion object {
        const val TAG = "TextureStore"

        // The gutter around each region of an atlas, see docs/texture-blending.md.
        const val ATLAS_REGION_PADDING = 1
    }
}
