#pragma once

// What the Vulkan backend's core (vk_context.cpp) takes from the platform it's built for: the Vulkan
// headers with the platform's window system, and logging. Android builds with
// VK_USE_PLATFORM_ANDROID_KHR, see android-natives; desktop gets its surfaces from GLFW, so it needs
// no window system of its own.

#include <vulkan/vulkan.h>

#ifdef VK_USE_PLATFORM_ANDROID_KHR

#include <vulkan/vulkan_android.h>
#include <android/hardware_buffer.h>
#include <android/log.h>
#include <android/native_window.h>

#define VERKTEX_LOG_TAG "VerktexVulkan"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, VERKTEX_LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, VERKTEX_LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, VERKTEX_LOG_TAG, __VA_ARGS__)

#else

#include <cstdio>

#define VERKTEX_LOG(level, ...) (std::fprintf(stderr, "VerktexVulkan " level ": " __VA_ARGS__), std::fputc('\n', stderr))
#define LOGI(...) VERKTEX_LOG("I", __VA_ARGS__)
#define LOGW(...) VERKTEX_LOG("W", __VA_ARGS__)
#define LOGE(...) VERKTEX_LOG("E", __VA_ARGS__)

#endif
