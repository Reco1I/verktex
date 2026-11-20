package com.reco1l.verktex.desktop

import com.reco1l.verktex.Logger
import com.reco1l.verktex.fonts.Font
import com.reco1l.verktex.fonts.FontFamily
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A [FontFamily.Source] over a font installed on the system.
 *
 * FreeType can't synthesise weights or italics, so every weight and style is looked up as a file of
 * its own: through fontconfig on Linux, and among the files of the system's UI font on Windows and
 * macOS. A weight or style without a file uses the regular one, which draws it on its `wght` axis
 * when it's a variable font (e.g. macOS's SF Pro).
 */
class DesktopSystemFontSource private constructor(
    private val family: String?,
    private val regular: DesktopFontSource,
    private val regularFile: File,
) : FontFamily.Source {

    // By file, weights resolving to the same file share it.
    private val sources = mutableMapOf(regularFile to regular)

    private val files = mutableMapOf<Pair<Font.Weight, Boolean>, File?>()

    private var warnedFallback = false


    override fun onCreateFont(descriptor: Font.Descriptor): Font {
        val isItalic = descriptor.style == Font.Style.Italic

        val file = files.getOrPut(descriptor.weight to isItalic) { find(family, descriptor.weight, isItalic) }
        val source = file?.let { sources.getOrPut(it) { load(it) ?: regular } } ?: regular

        if (source === regular && ((!regular.isVariable && descriptor.weight != Font.Weight.Normal) || isItalic) && !warnedFallback) {
            warnedFallback = true
            Logger.w(TAG, "No system font file for ${descriptor.weight}${if (isItalic) " italic" else ""}, the regular one is used.")
        }

        return source.onCreateFont(descriptor)
    }


    companion object {

        private const val TAG = "DesktopSystemFontSource"

        /**
         * Creates the source of the system font [family], or the default UI font when null.
         *
         * @return The source, or null when the regular font wasn't found.
         */
        fun create(family: String?): DesktopSystemFontSource? {
            val file = find(family, Font.Weight.Normal, false) ?: return null
            val source = load(file) ?: return null
            Logger.i(TAG, "System font ${family ?: "default"} is ${file.path}")
            return DesktopSystemFontSource(family, source, file)
        }


        private fun load(file: File): DesktopFontSource? = try {
            DesktopFontSource(file.readBytes(), warnsSynthesis = false)
        } catch (e: Exception) {
            Logger.e(TAG, "Couldn't load the font ${file.path}", e)
            null
        }

        private fun find(family: String?, weight: Font.Weight, isItalic: Boolean): File? {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val file = when {
                os.contains("win") -> findWindows(family, weight, isItalic)
                os.contains("mac") -> findMac(family)
                else -> findFontconfig(family, weight, isItalic)
            }
            return file?.takeIf { it.isFile }
        }

        private fun findFontconfig(family: String?, weight: Font.Weight, isItalic: Boolean): File? {
            // fontconfig's weights for the nearest named weight.
            val fcWeight = when (weight.value) {
                in 0..150 -> 0
                in 151..250 -> 40
                in 251..350 -> 50
                in 351..450 -> 80
                in 451..550 -> 100
                in 551..650 -> 180
                in 651..750 -> 200
                in 751..850 -> 205
                else -> 210
            }
            val pattern = "${family ?: "sans-serif"}:weight=$fcWeight:slant=${if (isItalic) 100 else 0}"

            return try {
                val process = ProcessBuilder("fc-match", "-f", "%{file}", pattern)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText().trim()
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroy()
                    return null
                }
                if (process.exitValue() != 0 || output.isEmpty()) null else File(output)
            } catch (e: Exception) {
                Logger.w(TAG, "fontconfig isn't available: ${e.message}")
                null
            }
        }

        private fun findWindows(family: String?, weight: Font.Weight, isItalic: Boolean): File? {
            val fonts = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")

            if (family != null) {
                // Without fontconfig only the regular file of a family can be guessed from its name.
                return File(fonts, family.replace(" ", "").lowercase() + ".ttf")
            }

            val name = when (weight.value) {
                in 0..350 -> if (isItalic) "seguili" else "segoeuil"
                in 351..550 -> if (isItalic) "segoeuii" else "segoeui"
                in 551..650 -> if (isItalic) "seguisbi" else "seguisb"
                in 651..750 -> if (isItalic) "segoeuiz" else "segoeuib"
                else -> if (isItalic) "seguibli" else "seguibl"
            }
            return File(fonts, "$name.ttf")
        }

        private fun findMac(family: String?): File? {
            val candidates = buildList {
                if (family != null) {
                    add("/System/Library/Fonts/$family.ttf")
                    add("/System/Library/Fonts/$family.ttc")
                    add("/Library/Fonts/$family.ttf")
                }
                // Only the regular instance of each, weights use it.
                add("/System/Library/Fonts/SFNS.ttf")
                add("/System/Library/Fonts/Helvetica.ttc")
            }
            return candidates.map(::File).firstOrNull { it.isFile }
        }
    }
}
