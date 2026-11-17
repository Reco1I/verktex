#pragma once

// What the JNI files of the Vulkan backend share: vk_jni_common.cpp and each platform's.

#include "vk_context.h"

#include <jni.h>

#include <string>
#include <vector>

namespace verktex::jni {

using verktex::Context;

// The error of the last call that failed, read with VKNative.lastError(). Only the draw thread calls
// in after the context is created.
extern std::string lastError;

inline Context *context(jlong handle) {
    return reinterpret_cast<Context *>(handle);
}

// Non-dispatchable handles are pointers on 64-bit ABIs and uint64_t on 32-bit ones.
template<typename T>
jlong toHandle(T value) {
    return static_cast<jlong>((uint64_t) value);
}

template<typename T>
T fromHandle(jlong value) {
    return (T) static_cast<uint64_t>(value);
}

// Creates and initializes a context, 0 on failure (see lastError).
jlong createContext(bool enableValidation, const std::vector<const char *> &surfaceExtensions);

} // namespace verktex::jni
