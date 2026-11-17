package com.reco1l.verktex.vulkan

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// Built by :desktop-natives into the resources, at natives/<platform>/. A library can't be loaded
// from a jar, it's copied to a temporary folder first. Only Linux x64 is built so far.
internal actual fun loadVulkanLibrary() {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val platform = when {
        "linux" in os && (arch == "amd64" || arch == "x86_64") -> "linux-x64"
        else -> throw UnsatisfiedLinkError("The Vulkan backend isn't built for $os ($arch) yet.")
    }

    val resource = "/natives/$platform/libverktex_vulkan.so"
    val input = VKNative::class.java.getResourceAsStream(resource)
        ?: throw UnsatisfiedLinkError("$resource isn't packaged, it wasn't built (see :desktop-natives).")

    val directory = Files.createTempDirectory("verktex-vulkan").toFile()
    val library = File(directory, "libverktex_vulkan.so")
    input.use { Files.copy(it, library.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    directory.deleteOnExit()
    library.deleteOnExit()

    System.load(library.absolutePath)
}
