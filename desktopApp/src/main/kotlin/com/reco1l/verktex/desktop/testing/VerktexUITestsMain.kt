package com.reco1l.verktex.desktop.testing

import com.reco1l.verktex.testing.VerktexUITests
import java.io.File
import kotlin.system.exitProcess

/**
 * Runs Verktex's own UI tests on desktop:
 *
 * ```
 * ./gradlew :desktopApp:run --args="--test 'Verktex/<test>' --screenshot build/test.png"
 * ```
 *
 * `--test` opens a test first (`suite/test` or the test name), `--screenshot` saves a frame after a
 * moment and exits, `--vsync` caps the frame rate to the display's refresh rate, `--backend` opens with
 * a graphics backend (e.g. `Vulkan`).
 */
fun main(args: Array<String>) {
    var test: String? = null
    var screenshot: File? = null
    var vsync = false
    var backend: String? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--test" -> test = args.getOrNull(++i)
            "--screenshot" -> screenshot = args.getOrNull(++i)?.let(::File)
            "--vsync" -> vsync = true
            "--backend" -> backend = args.getOrNull(++i)
            else -> {
                System.err.println("Unknown argument: ${args[i]}. Usage: [--test <suite/test>] [--screenshot <file.png>] [--vsync] [--backend <name>]")
                exitProcess(2)
            }
        }
        i++
    }

    object : UITestApplication(test, screenshot, vsync, backend = backend) {
        override fun createSuites() = listOf(VerktexUITests)
    }.run()
}
