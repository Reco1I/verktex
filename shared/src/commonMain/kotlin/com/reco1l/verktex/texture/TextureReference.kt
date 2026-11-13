package com.reco1l.verktex.texture

import com.reco1l.verktex.graphics.GraphicsTexture

/**
 * Refers to the texture region a component draws, either given directly or stored in [Textures]
 * under a key, see [storedTexture].
 */
sealed class TextureReference {

    /**
     * The region this reference points to now, or null if there is none.
     */
    abstract fun resolve(): GraphicsTexture.Region?


    /**
     * A region given directly, it never changes.
     */
    data class Region(val region: GraphicsTexture.Region) : TextureReference() {
        override fun resolve(): GraphicsTexture.Region = region
    }

    /**
     * The region stored in [Textures] under [key], followed as it's loaded, replaced or unloaded.
     */
    class Stored(val key: String) : TextureReference() {

        // The store is read again only once it changed, see Textures.revision.
        private var revision = -1
        private var region: GraphicsTexture.Region? = null

        override fun resolve(): GraphicsTexture.Region? {
            val current = Textures.revision

            if (revision != current) {
                revision = current
                region = Textures[key]
            }
            return region
        }

        override fun equals(other: Any?) = other is Stored && other.key == key

        override fun hashCode() = key.hashCode()

        override fun toString() = "Stored($key)"
    }
}

/**
 * Refers to the region stored in [Textures] under [key], following it as it's replaced.
 */
fun storedTexture(key: String): TextureReference.Stored = TextureReference.Stored(key)

/**
 * Refers to this region directly.
 */
val GraphicsTexture.Region.reference: TextureReference.Region
    get() = TextureReference.Region(this)
