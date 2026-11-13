package com.reco1l.verktex.texture

import com.reco1l.verktex.graphics.GraphicsTexture

/**
 * Refers to the texture region a component draws: a region given directly, one stored in [Textures]
 * under a key ([storedTexture]), or one an application looks up its own way ([Lookup]).
 */
abstract class TextureReference {

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
     * A region looked up again only once [revision] changed, so it can be resolved every frame.
     */
    abstract class Lookup : TextureReference() {

        private var resolvedRevision = -1
        private var region: GraphicsTexture.Region? = null

        /**
         * Changes whenever the region [lookup] returns may have changed. By default it's
         * [Textures.revision], references that depend on more than the store go up with that too.
         */
        protected open val revision: Int
            get() = Textures.revision

        /**
         * Looks the region up.
         */
        protected abstract fun lookup(): GraphicsTexture.Region?

        final override fun resolve(): GraphicsTexture.Region? {
            if (resolvedRevision != revision) {
                region = lookup()
                // Taken after the lookup, which may load the region and change the revision itself.
                resolvedRevision = revision
            }
            return region
        }
    }

    /**
     * The region stored in [Textures] under [key], followed as it's loaded, replaced or unloaded.
     */
    data class Stored(val key: String) : Lookup() {
        override fun lookup() = Textures[key]
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
