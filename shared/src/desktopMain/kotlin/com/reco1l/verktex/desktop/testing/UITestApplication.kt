package com.reco1l.verktex.desktop.testing

import com.reco1l.verktex.Verktex
import com.reco1l.verktex.desktop.DesktopGameHost
import com.reco1l.verktex.graphics.GPUBackend
import com.reco1l.verktex.settings.Settings
import com.reco1l.verktex.settings.VerktexSettings
import com.reco1l.verktex.testing.SimulatedSafeArea
import com.reco1l.verktex.testing.UITestScene
import com.reco1l.verktex.testing.UITestSuite
import com.reco1l.verktex.vulkan.resetVulkanCrashGuard
import java.io.File
import java.util.prefs.Preferences

/**
 * A desktop window to browse and try UI components in isolation, the counterpart of the Android
 * platform's UITestActivity.
 *
 * Projects extend it and return their suites from [createSuites] (usually with
 * [com.reco1l.verktex.testing.VerktexUITests] first).
 *
 * @param initialTest The test to open first, as `suite/test` or just the test name. Without it,
 * the last opened test is shown again.
 * @param screenshot When set, the frame shown [screenshotDelay] seconds after starting is saved
 * to this PNG and the window closes, e.g. for smoke checks.
 * @param screenshotDelay How long after starting the screenshot is taken.
 * @param vsync Whether frames wait for the display's refresh, see [DesktopGameHost].
 * @param backend The graphics backend to open with (a [GPUBackend] name, e.g. `Vulkan`), the
 * setting's otherwise.
 * @param safeArea The safe area to simulate, as a phone's (see [SimulatedSafeArea]). Without it,
 * the last one chosen is simulated again.
 */
abstract class UITestApplication(
    private val initialTest: String? = null,
    private val screenshot: File? = null,
    vsync: Boolean = false,
    private val screenshotDelay: Double = SCREENSHOT_DELAY_SECONDS,
    private val backend: String? = null,
    private val safeArea: SimulatedSafeArea? = null,
) : DesktopGameHost(title = "Verktex UI tests", vsync = vsync) {

    private val preferences = Preferences.userRoot().node("com/reco1l/verktex/uitests")


    init {
        // What's changed while trying components isn't kept, as on Android.
        Settings.isReadOnly = true
    }

    /**
     * Creates the suites shown in the browser. Called once the engine is initialized, so textures and
     * fonts can be loaded.
     */
    abstract fun createSuites(): List<UITestSuite>


    override fun onPlatformAttached() {
        val backend = backend ?: return
        Settings[VerktexSettings.GPUBackend] = backend

        // Asked for explicitly, it's tried even if it crashed while starting the last time.
        if (backend == GPUBackend.Vulkan.name) {
            resetVulkanCrashGuard()
        }
    }

    override fun onEngineInitialized() {}

    override fun onEngineStarted() {
        // Always shown while trying components.
        Settings[VerktexSettings.ShowPerformanceOverlay] = true

        val test = initialTest ?: preferences.get(KEY_LAST_TEST, null)
        val safeArea = safeArea
            ?: preferences.get(KEY_SAFE_AREA, null)?.let(SimulatedSafeArea::find)
            ?: SimulatedSafeArea.None

        Verktex.withTreeLock {
            Verktex.pushScene(UITestScene(
                suites = createSuites(),
                initialTest = test,
                onTestSelected = { id -> preferences.put(KEY_LAST_TEST, id) },
                initialSafeArea = safeArea,
                onSafeAreaSelected = { preferences.put(KEY_SAFE_AREA, it.name) },
            ))
        }

        if (screenshot != null) {
            captureScreenshot(screenshot, screenshotDelay)
        }
    }

    companion object {
        /**
         * How long after starting the screenshot is taken, so fonts and textures are loaded and the
         * test's entrance animations ended.
         */
        const val SCREENSHOT_DELAY_SECONDS = 1.5

        private const val KEY_LAST_TEST = "last_test"
        private const val KEY_SAFE_AREA = "safe_area"

        /**
         * Reads the value of `--safe-area`, a [SimulatedSafeArea]'s name or label, exiting with
         * the ones there are otherwise.
         */
        fun parseSafeArea(value: String?): SimulatedSafeArea {
            return value?.let(SimulatedSafeArea::find) ?: run {
                System.err.println("Unknown safe area: $value. One of: ${SimulatedSafeArea.entries.joinToString { it.name }}")
                kotlin.system.exitProcess(2)
            }
        }
    }
}
