import java.io.ByteArrayOutputStream
import java.util.Properties

// What the desktop platform needs natively, the counterpart of :android-natives: the library of the
// Vulkan backend, built from natives/vulkan with CMake, and the SPIR-V of the built-in shaders. Both
// are resources, which the desktopMain of :shared depends on. Only Linux x64 is built so far; on other
// systems, or without CMake, a C++ compiler or the Vulkan loader, nothing is built and desktop stays on
// OpenGL. See docs/vulkan-backend.md.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val sdkDirectory: File? = (localProperties.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME"))?.let(::File)
val ndkDirectory: File? = sdkDirectory?.resolve("ndk/22.1.7171670")?.takeIf { it.isDirectory }

val os = System.getProperty("os.name").lowercase()
val arch = System.getProperty("os.arch").lowercase()
val isLinuxX64 = "linux" in os && (arch == "amd64" || arch == "x86_64")

fun findOnPath(name: String): File? = System.getenv("PATH").orEmpty().split(File.pathSeparator)
    .map { File(it, name) }.firstOrNull { it.canExecute() }

// The SDK's CMake (which comes with Ninja), or the system's.
val cmakeBin: File? = sdkDirectory?.resolve("cmake")?.listFiles()?.sortedDescending()
    ?.map { it.resolve("bin") }?.firstOrNull { it.resolve("cmake").canExecute() }
val cmakeExecutable: File? = cmakeBin?.resolve("cmake") ?: findOnPath("cmake")
val ninjaExecutable: File? = cmakeBin?.resolve("ninja")?.takeIf { it.canExecute() } ?: findOnPath("ninja")

/**
 * Builds the native library with CMake into [outputDirectory], at `natives/<platform>/`. Never fails
 * the build: without the tools it warns and outputs nothing, the backend is then unavailable.
 */
abstract class BuildVulkanLibrary : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val cmakeLists: RegularFileProperty

    @get:Input
    abstract val cmake: Property<String>

    @get:Input
    abstract val ninja: Property<String>

    @get:Input
    abstract val vulkanInclude: Property<String>

    @get:Input
    abstract val jniHeader: Property<String>

    @get:Input
    abstract val platform: Property<String>

    @get:Internal
    abstract val workDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val exec: ExecOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun build() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        fun skip(reason: String) = logger.warn("warning: the desktop Vulkan library isn't built, $reason. Desktop uses OpenGL.")

        if (platform.get().isEmpty()) return skip("only Linux x64 is supported so far")
        if (cmake.get().isEmpty()) return skip("CMake wasn't found (install it, or CMake from the Android SDK)")
        if (!File(vulkanInclude.get()).isDirectory || !File(jniHeader.get()).isFile) {
            return skip("the NDK 22.1.7171670 wasn't found, its Vulkan headers and jni.h are used")
        }

        val work = workDirectory.get().asFile
        // Only jni.h of the NDK's sysroot: the whole sysroot would replace the system's C library headers.
        val jniInclude = work.resolve("jni-include").apply { deleteRecursively(); mkdirs() }
        File(jniHeader.get()).copyTo(jniInclude.resolve("jni.h"))

        val cmakeDirectory = work.resolve("cmake")
        val configure = mutableListOf(
            cmake.get(), "-S", cmakeLists.get().asFile.parent, "-B", cmakeDirectory.path,
            "-DCMAKE_BUILD_TYPE=Release",
            "-DVULKAN_INCLUDE=${vulkanInclude.get()}",
            "-DJNI_INCLUDE=${jniInclude.path}",
        )
        if (ninja.get().isNotEmpty()) {
            configure += listOf("-G", "Ninja", "-DCMAKE_MAKE_PROGRAM=${ninja.get()}")
        }

        for (command in listOf(configure, listOf(cmake.get(), "--build", cmakeDirectory.path))) {
            val log = ByteArrayOutputStream()
            val result = exec.exec {
                commandLine(command)
                standardOutput = log
                errorOutput = log
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                logger.warn(log.toString())
                return skip("CMake failed (output above)")
            }
        }

        files.copy {
            from(cmakeDirectory.resolve("libverktex_vulkan.so"))
            into(output.resolve("natives/${platform.get()}"))
        }
    }
}

val buildVulkanLibrary = tasks.register<BuildVulkanLibrary>("buildVulkanLibrary") {
    sources.set(rootProject.layout.projectDirectory.dir("natives/vulkan"))
    cmakeLists.set(layout.projectDirectory.file("src/main/cpp/CMakeLists.txt"))
    cmake.set(cmakeExecutable?.path ?: "")
    ninja.set(ninjaExecutable?.path ?: "")
    vulkanInclude.set(ndkDirectory?.resolve("sources/third_party/vulkan/src/include")?.path ?: "")
    jniHeader.set(ndkDirectory?.resolve("toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/jni.h")?.path ?: "")
    platform.set(if (isLinuxX64) "linux-x64" else "")
    workDirectory.set(layout.buildDirectory.dir("vulkanLibrary"))
    outputDirectory.set(layout.buildDirectory.dir("generated/vulkanLibrary"))
}

// The built-in shaders translated and compiled to SPIR-V with the NDK's glslc, as on Android, as
// shaders/<name>.spv next to the GLSL ones :shared packages. Without glslc they're skipped with a warning.
val vulkanShaderTools: Configuration = configurations.create("vulkanShaderTools") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
    }
}

dependencies {
    vulkanShaderTools(dependencies.project(":shadertools"))
}

val shaderDirectory: Directory = rootProject.layout.projectDirectory.dir("shared/assets/shaders")
val shaderOutput = layout.buildDirectory.dir("generated/vulkanShaders")

val compileVulkanShaders = tasks.register<JavaExec>("compileVulkanShaders") {
    val glslc = ndkDirectory?.resolve("shader-tools/linux-x86_64/glslc")?.takeIf { it.canExecute() } ?: findOnPath("glslc")

    inputs.dir(shaderDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("glslc", glslc?.path ?: "")
    outputs.dir(shaderOutput)

    classpath = vulkanShaderTools
    mainClass.set("com.reco1l.verktex.shaders.CompileVulkanShadersKt")
    args(shaderDirectory.asFile.path, shaderOutput.get().dir("shaders").asFile.path, glslc?.path ?: "")
}

sourceSets.main {
    resources.srcDir(buildVulkanLibrary.flatMap { it.outputDirectory })
    resources.srcDir(files(shaderOutput).builtBy(compileVulkanShaders))
    // The translated GLSL the shader compiler leaves next to its output.
    resources.exclude("shaders-glsl450/**")
}
