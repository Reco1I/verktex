package com.reco1l.verktex.desktop

import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.GameHost
import com.reco1l.verktex.Logger
import com.reco1l.verktex.PlatformApplication
import com.reco1l.verktex.Platform
import com.reco1l.verktex.desktop.bass.BASSAudioDriver
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.desktop.gl.GLContext
import com.reco1l.verktex.desktop.gl.GLDevice
import com.reco1l.verktex.desktop.gl.GLRenderer
import com.reco1l.verktex.desktop.vulkan.DesktopVKContext
import com.reco1l.verktex.graphics.GPUBackend
import com.reco1l.verktex.graphics.Renderer
import com.reco1l.verktex.settings.Settings
import com.reco1l.verktex.settings.VerktexSettings
import com.reco1l.verktex.vulkan.resetVulkanCrashGuard
import com.reco1l.verktex.vulkan.startVulkanRenderer
import com.reco1l.verktex.fonts.Fonts
import com.reco1l.verktex.input.Input
import com.reco1l.verktex.input.KeyboardEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.ui.ITextInput
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.stb.STBImageWrite
import java.io.File

/**
 * The base of desktop game hosts, the counterpart of the Android platform's AndroidGameHost: it
 * opens a window, initialises the engine and turns GLFW's events into the engine's input.
 *
 * [run] must be called from the main thread (the first one on macOS, see `-XstartOnFirstThread`)
 * and returns once the window is closed.
 *
 * The graphics backend is the `GPUBackend` setting ([VerktexSettings.GPUBackend]): Vulkan
 * when it's picked and works, OpenGL 3.3 otherwise, see `docs/vulkan-backend.md`.
 *
 * @param title The window's title.
 * @param applicationName The name of the folders the application keeps its files in (see
 * [Platform.storage]), the title by default. It's separate so that a title with characters folder
 * names had better avoid can name them differently.
 * @param vsync Whether frames wait for the display's refresh, see [DesktopGPUContext.vsync].
 * Off by default, so the frame rate isn't capped to the display's.
 */
abstract class DesktopGameHost(
    private val title: String = "Verktex",
    private val applicationName: String = title,
    private val initialWidth: Int = 1280,
    private val initialHeight: Int = 720,
    private val vsync: Boolean = false,
) : GameHost {

    /**
     * The graphics context (OpenGL or Vulkan) and its window, once [run] created them.
     */
    lateinit var context: DesktopGPUContext
        private set

    private val handle: Long
        get() = context.window.handle

    // The window's size in screen coordinates, the cursor's are in those. The framebuffer can be
    // larger (e.g. on Retina displays), the engine works in framebuffer pixels.
    private var windowSizeWidth = 1
    private var windowSizeHeight = 1

    private var pressedButtons = 0

    @Volatile
    private var textInput: ITextInput? = null


    /**
     * Opens the window and runs the engine until the window is closed.
     */
    fun run() {
        Logger.implementation = DesktopLogger()
        GLFWErrorCallback.createPrint(System.err).set()

        // On Wayland, GLFW draws the window's decorations (title bar, buttons) through libdecor,
        // whose GTK plugin refuses to run off the process's first thread, which Java's main never
        // is. GNOME doesn't draw them for the app either, so the window had none. Through XWayland,
        // the compositor draws them.
        if (System.getenv("WAYLAND_DISPLAY") != null && System.getenv("DISPLAY") != null &&
            glfwPlatformSupported(GLFW_PLATFORM_X11)) {
            glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11)
        }

        if (!glfwInit()) {
            throw IllegalStateException("Couldn't initialise GLFW.")
        }

        try {
            // Attached before the window is created, the settings (which pick the backend) are in its
            // storage. The window opens on the primary monitor and scales to it.
            val monitor = glfwGetPrimaryMonitor()
            val scaleX = FloatArray(1)
            val scaleY = FloatArray(1)
            if (monitor != 0L) glfwGetMonitorContentScale(monitor, scaleX, scaleY)

            val mode = if (monitor != 0L) glfwGetVideoMode(monitor) else null
            Platform.attach(
                density = scaleX[0].takeIf { it > 0f } ?: 1f,
                width = mode?.width() ?: initialWidth,
                height = mode?.height() ?: initialHeight,
                applicationName = applicationName,
                application = platformApplication,
                onExit = ::close,
            )
            onPlatformAttached()

            val renderer = createRenderer()
            context.vsync = vsync

            Verktex.initialize(
                host = this,
                renderer = renderer,
                audio = BASSAudioDriver(Platform.provider as DesktopProvider)
            )

            loadBuiltInFonts()
            onEngineInitialized()

            installCallbacks()
            glfwShowWindow(handle)

            Verktex.start()
            onEngineStarted()

            while (!glfwWindowShouldClose(handle)) {
                glfwWaitEventsTimeout(0.25)
            }

            // Stops the workers and destroys the context, which takes the window with it.
            Verktex.dispose()
        } finally {
            glfwTerminate()
            glfwSetErrorCallback(null)?.free()
        }
    }

    /**
     * The application this host runs, see [Platform.application].
     */
    open val platformApplication: PlatformApplication
        get() = PlatformApplication()

    /**
     * Called once the platform is attached, before the graphics backend is picked from the settings
     * and the window is created, e.g. to change the backend.
     */
    open fun onPlatformAttached() {}

    // Vulkan when it's picked, it falls back to OpenGL where it can't start.
    private fun createRenderer(): Renderer {
        // Picking Vulkan again retries it, even if it crashed while starting the last time.
        Settings.observe(VerktexSettings.GPUBackend) { name ->
            if (name == GPUBackend.Vulkan.name) resetVulkanCrashGuard()
        }

        if (GPUBackend.fromName(Settings[VerktexSettings.GPUBackend]) == GPUBackend.Vulkan) {
            val vulkan = startVulkanRenderer(createContext = {
                DesktopVKContext.create(title, initialWidth, initialHeight, enableValidation = platformApplication.isDebug)
            })
            if (vulkan != null) {
                context = vulkan.context as DesktopVKContext
                return vulkan
            }
        }

        val gl = GLContext.create(title, initialWidth, initialHeight)
        Logger.i("Verktex", "Using the OpenGL backend.")
        context = gl
        return GLRenderer(gl, GLDevice())
    }

    /**
     * Asks the window to close, from any thread. [run] returns once the engine stopped.
     */
    fun close() {
        glfwSetWindowShouldClose(handle, true)
        glfwPostEmptyEvent()
    }


    private fun loadBuiltInFonts() {
        fun load(key: String, file: String) =
            Fonts.load(key, "fonts/$file", isInternal = true)
                ?: throw IllegalStateException("Failed to load built-in font: $file")

        BuiltIn.Fonts.Default = load("nunito", "nunito-variable-wght.ttf")
        BuiltIn.Fonts.Fredoka = load("fredoka", "fredoka-variable-wdth-wght.ttf")
        BuiltIn.Fonts.Anta = load("anta", "anta-regular-static.ttf")
        BuiltIn.Fonts.System = Fonts.loadSystem("system") ?: run {
            Logger.w("Verktex", "The system font isn't available, using the default font instead.")
            BuiltIn.Fonts.Default
        }
        BuiltIn.Fonts.FontAwesomeIconRegular = load("fa-regular", "fa-regular.otf")
        BuiltIn.Fonts.FontAwesomeIconSolid = load("fa-solid", "fa-solid.otf")
        BuiltIn.Fonts.FontAwesomeIconBrands = load("fa-brands", "fa-brands.otf")
    }

    private fun installCallbacks() {
        val window = context.window

        val width = IntArray(1)
        val height = IntArray(1)
        glfwGetWindowSize(handle, width, height)
        windowSizeWidth = width[0].coerceAtLeast(1)
        windowSizeHeight = height[0].coerceAtLeast(1)

        glfwSetWindowSizeCallback(handle) { _, w, h ->
            windowSizeWidth = w.coerceAtLeast(1)
            windowSizeHeight = h.coerceAtLeast(1)
        }

        glfwSetFramebufferSizeCallback(handle) { _, w, h ->
            window.windowWidth = w
            window.windowHeight = h
        }

        glfwSetWindowPosCallback(handle) { _, _, _ -> window.updateRefreshRate() }

        glfwSetWindowFocusCallback(handle) { _, focused -> Verktex.onGameWindowFocusChange(focused) }

        // Minimised there's no framebuffer, the workers are paused instead of spinning.
        glfwSetWindowIconifyCallback(handle) { _, iconified ->
            if (iconified) Verktex.pause() else Verktex.resume()
        }

        glfwSetMouseButtonCallback(handle) { _, button, action, _ ->
            val inputButton = when (button) {
                GLFW_MOUSE_BUTTON_LEFT -> PointerEvent.Button.Left
                GLFW_MOUSE_BUTTON_RIGHT -> PointerEvent.Button.Right
                else -> return@glfwSetMouseButtonCallback
            }

            when (action) {
                GLFW_PRESS -> {
                    pressedButtons++
                    dispatchPointer(PointerEvent.Action.Down, inputButton)
                }
                GLFW_RELEASE -> {
                    pressedButtons = (pressedButtons - 1).coerceAtLeast(0)
                    dispatchPointer(PointerEvent.Action.Up, inputButton)
                }
            }
        }

        // The mouse only moves the pointer while a button is held, like a touch on Android. It
        // hovers all the time.
        glfwSetCursorPosCallback(handle) { _, _, _ ->
            val (x, y) = cursorPosition()
            Input.dispatchHover(x, y)

            if (pressedButtons > 0) {
                dispatchPointer(PointerEvent.Action.Move, PointerEvent.Button.Unknown)
            }
        }

        glfwSetCursorEnterCallback(handle) { _, entered ->
            if (!entered) {
                Input.dispatchHoverExit()
            }
        }

        glfwSetScrollCallback(handle) { _, deltaX, deltaY ->
            // Positive is up and left, as Android's scroll axes.
            val (x, y) = cursorPosition()
            Input.dispatchScrollEvent(pointerId = 0, x = x, y = y, deltaX = deltaX.toFloat(), deltaY = deltaY.toFloat())
        }

        glfwSetCharCallback(handle) { _, codePoint ->
            val input = textInput ?: return@glfwSetCharCallback
            Verktex.withTreeLock { TextInputEditor.insert(input, String(Character.toChars(codePoint))) }
        }

        glfwSetKeyCallback(handle) { _, key, _, action, mods ->
            if (action != GLFW_RELEASE && handleTextEditingKey(key, mods)) {
                return@glfwSetKeyCallback
            }

            Input.dispatchKeyboardEvent(
                // A held key repeats as more presses, as on Android.
                action = if (action == GLFW_RELEASE) KeyboardEvent.Action.Up else KeyboardEvent.Action.Down,
                key = glfwKeyToInputKey(key),
                shiftPressed = mods and GLFW_MOD_SHIFT != 0,
                ctrlPressed = mods and GLFW_MOD_CONTROL != 0,
                altPressed = mods and GLFW_MOD_ALT != 0,
                metaPressed = mods and GLFW_MOD_SUPER != 0
            )
        }
    }

    // The keys editing the attached text input, which go to it instead of the engine, as keys go
    // to the hidden EditText on Android while it's focused.
    private fun handleTextEditingKey(key: Int, mods: Int): Boolean {
        val input = textInput ?: return false
        val shortcut = mods and (GLFW_MOD_CONTROL or GLFW_MOD_SUPER) != 0

        when {
            key == GLFW_KEY_BACKSPACE -> Verktex.withTreeLock { TextInputEditor.deleteLast(input) }
            key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER -> Verktex.withTreeLock { input.onImeAction() }
            key == GLFW_KEY_V && shortcut -> {
                // Pasted on one line, as a single line input on Android.
                val text = glfwGetClipboardString(handle)?.replace(Regex("[\r\n]+"), " ") ?: return true
                Verktex.withTreeLock { TextInputEditor.insert(input, text) }
            }
            else -> return false
        }
        return true
    }

    // The cursor's position in framebuffer pixels.
    private fun cursorPosition(): Pair<Float, Float> {
        val x = DoubleArray(1)
        val y = DoubleArray(1)
        glfwGetCursorPos(handle, x, y)

        // Screen coordinates to framebuffer pixels.
        val window = context.window
        val scaleX = window.windowWidth.toFloat() / windowSizeWidth
        val scaleY = window.windowHeight.toFloat() / windowSizeHeight
        return x[0].toFloat() * scaleX to y[0].toFloat() * scaleY
    }

    private fun dispatchPointer(action: PointerEvent.Action, button: PointerEvent.Button) {
        val (x, y) = cursorPosition()

        Input.dispatchPointerEvent(
            action = action,
            button = button,
            pointerId = 0,
            x = x,
            y = y,
            time = (glfwGetTime() * 1000).toLong()
        )
    }

    /**
     * Saves the frame shown [delaySeconds] after this call to [file] as a PNG and closes the window,
     * e.g. for smoke checks.
     */
    fun captureScreenshot(file: File, delaySeconds: Double) {
        val start = glfwGetTime()

        // Called on the draw thread, the frame is complete in the back buffer before it's presented.
        context.onBeforePresent = callback@{
            if (glfwGetTime() - start < delaySeconds) return@callback
            context.onBeforePresent = null

            val window = context.window
            val width = window.windowWidth
            val height = window.windowHeight

            val pixels = context.readPixels()
            if (pixels == null) {
                Logger.e("DesktopGameHost", "Couldn't read the frame back for the screenshot.")
                close()
                return@callback
            }

            file.absoluteFile.parentFile?.mkdirs()
            if (STBImageWrite.stbi_write_png(file.absolutePath, width, height, 4, pixels, width * 4)) {
                Logger.i("DesktopGameHost", "Saved a screenshot to ${file.absolutePath}")
            } else {
                Logger.e("DesktopGameHost", "Couldn't write the screenshot to ${file.absolutePath}")
            }
            close()
        }
    }


    override fun attachTextInput(textInput: ITextInput) {
        this.textInput = textInput
    }

    override fun detachTextInput() {
        textInput = null
    }
}
