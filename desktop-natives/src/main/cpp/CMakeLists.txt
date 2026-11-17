cmake_minimum_required(VERSION 3.18.1)

project(verktex_native CXX)

# The Vulkan backend on desktop, see verktex/docs/vulkan-backend.md and desktop/vulkan/VKDesktopNative.kt.
# Its sources are shared with Android, in natives/vulkan at the root of Verktex. The build passes the
# folders of the Vulkan headers and of jni.h (both from the NDK, so no Vulkan SDK or JDK headers are
# needed).
set(VULKAN_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/../../../../natives/vulkan)

add_library(verktex_vulkan SHARED
        ${VULKAN_SOURCES}/vk_context.cpp
        ${VULKAN_SOURCES}/vk_jni_common.cpp
        ${VULKAN_SOURCES}/vk_jni_desktop.cpp)

target_include_directories(verktex_vulkan PRIVATE ${VULKAN_SOURCES} ${VULKAN_INCLUDE} ${JNI_INCLUDE})
target_compile_features(verktex_vulkan PRIVATE cxx_std_17)
target_compile_options(verktex_vulkan PRIVATE -Wall -Wextra -Wno-unused-parameter -Wno-missing-field-initializers)
set_target_properties(verktex_vulkan PROPERTIES CXX_VISIBILITY_PRESET hidden)

# The loader GLFW uses as well, so both share it. Its runtime name is enough: no development
# package is needed, and a system without it fails to load the library, falling back to OpenGL.
find_library(VULKAN_LOADER NAMES libvulkan.so.1 vulkan REQUIRED)
target_link_libraries(verktex_vulkan ${VULKAN_LOADER} -static-libstdc++ -static-libgcc -Wl,--exclude-libs,ALL)
