package com.reco1l.verktex.texture

import com.reco1l.verktex.graphics.Texture

/**
 * Refers to the frames an animated component plays: frames given directly, or ones an application
 * looks up its own way ([Lookup]), whose amount may change along with them.
 *
 * @see TextureReference
 */
abstract class FramesReference {

    /**
     * The frames this reference points to now, empty if there are none.
     */
    abstract fun resolve(): List<Texture.Region>


    /**
     * Frames given directly, they never change.
     */
    data class Frames(val frames: List<Texture.Region>) : FramesReference() {
        override fun resolve() = frames
    }

    /**
     * Frames looked up again only once [revision] changed, so they can be resolved every frame. The
     * same list is returned until then.
     */
    abstract class Lookup : FramesReference() {

        private var resolvedRevision = -1
        private var frames: List<Texture.Region> = emptyList()

        /**
         * Changes whenever the frames [lookup] returns may have changed. By default it's
         * [Textures.revision], references that depend on more than the store go up with that too.
         */
        protected open val revision: Int
            get() = Textures.revision

        /**
         * Looks the frames up.
         */
        protected abstract fun lookup(): List<Texture.Region>

        final override fun resolve(): List<Texture.Region> {
            if (resolvedRevision != revision) {
                val frames = lookup()

                // Keep the previous list when nothing changed, so it can be told apart by identity.
                if (frames != this.frames) {
                    this.frames = frames
                }
                // Taken after the lookup, which may load the frames and change the revision itself.
                resolvedRevision = revision
            }
            return frames
        }
    }
}

/**
 * Refers to these frames directly.
 */
val List<Texture.Region>.reference: FramesReference.Frames
    get() = FramesReference.Frames(this)
