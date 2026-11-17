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

# The Oboe audio backend, see verktex/docs/audio-backends.md and oboe/OboeNative.kt: the native audio
# core of natives/audio, its Oboe output and its JNI functions. Oboe is built from source at the tag
# OBOE_VERSION (gradle/libs.versions.toml), so it links with the static STL: its Prefab package is
# built against c++_shared. OBOE_SOURCE_DIR can point to a checkout of it instead, to build offline.
set(AUDIO_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/../../../../natives/audio)
add_subdirectory(${AUDIO_SOURCES} ${CMAKE_CURRENT_BINARY_DIR}/audio)

if (OBOE_SOURCE_DIR)
    add_subdirectory(${OBOE_SOURCE_DIR} ${CMAKE_CURRENT_BINARY_DIR}/oboe)
else ()
    include(FetchContent)
    FetchContent_Declare(oboe
            GIT_REPOSITORY https://github.com/google/oboe.git
            GIT_TAG ${OBOE_VERSION}
            GIT_SHALLOW TRUE)
    FetchContent_MakeAvailable(oboe)
endif ()

add_library(verktex_audio SHARED
        ${AUDIO_SOURCES}/android/audio_oboe_output.cpp
        ${AUDIO_SOURCES}/android/audio_jni.cpp)

target_compile_options(verktex_audio PRIVATE -Wall -Wextra -Wno-unused-parameter)
target_link_libraries(verktex_audio verktex_audio_core oboe log)
