package com.reco1l.verktex.vulkan

// Not built for desktop yet: the backend isn't used there, see docs/vulkan-backend.md.
internal actual fun loadVulkanLibrary() {
    throw UnsatisfiedLinkError("The Vulkan backend isn't available on desktop yet.")
}
