package com.reco1l.verktex.audio

/**
 * The libraries an [AudioDriver] can play audio with. The engine starts with the one of
 * [com.reco1l.verktex.settings.VerktexSettings.AudioBackend], see `docs/audio-backends.md`.
 */
enum class AudioBackend(

    /**
     * The name shown to users, e.g. in the performance overlay.
     */
    val displayName: String,
) {

    /**
     * BASS (https://www.un4seen.com/), on every platform.
     */
    BASS("BASS"),

    /**
     * Oboe (https://github.com/google/oboe) over the engine's native audio core, on Android only,
     * falling back to [BASS] where it can't be started. Experimental: users opt in.
     */
    Oboe("Oboe");


    companion object {

        /**
         * The backend named [name], or [BASS] for any other name.
         */
        fun fromName(name: String?): AudioBackend {
            return entries.firstOrNull { it.name == name } ?: BASS
        }
    }
}
