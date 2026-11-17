cmake_minimum_required(VERSION 3.18.1)

project(verktex_native CXX)

# The Vulkan backend, see verktex/docs/vulkan-backend.md and vulkan/VKNative.kt. Its sources are
# shared with the desktop build, in natives/vulkan at the root of Verktex.
set(VULKAN_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/../../../../natives/vulkan)

add_library(verktex_vulkan SHARED
        ${VULKAN_SOURCES}/vk_context.cpp
        ${VULKAN_SOURCES}/vk_jni_common.cpp
        ${VULKAN_SOURCES}/vk_jni_android.cpp)

target_include_directories(verktex_vulkan PRIVATE ${VULKAN_SOURCES})
target_compile_features(verktex_vulkan PRIVATE cxx_std_17)
target_compile_definitions(verktex_vulkan PRIVATE VK_USE_PLATFORM_ANDROID_KHR)
target_compile_options(verktex_vulkan PRIVATE -Wall -Wextra -Wno-unused-parameter -Wno-missing-field-initializers)

# libvulkan.so exists from API 24, the minimum SDK.
target_link_libraries(verktex_vulkan vulkan android log)
