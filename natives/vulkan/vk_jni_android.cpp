// The Android part of the Vulkan backend's JNI surface, see VKAndroidNative.kt: the context with
// Android's surfaces, its window and the video frames, imported from hardware buffers.

#include "vk_jni.h"

#include <android/native_window_jni.h>
#include <dlfcn.h>

using namespace verktex::jni;

namespace {

// From API 26, the minimum SDK is 24: looked up at runtime.
using FromHardwareBuffer = AHardwareBuffer *(*)(JNIEnv *, jobject);

FromHardwareBuffer fromHardwareBuffer() {
    static auto function = reinterpret_cast<FromHardwareBuffer>(
            dlsym(dlopen("libandroid.so", RTLD_NOW), "AHardwareBuffer_fromHardwareBuffer"));
    return function;
}

} // namespace

#define VKANDROID(name) Java_com_reco1l_verktex_android_vulkan_VKAndroidNative_##name

extern "C" {

JNIEXPORT jlong JNICALL VKANDROID(createContext)(JNIEnv *, jclass, jboolean enableValidation) {
    return createContext(enableValidation == JNI_TRUE, {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME});
}

JNIEXPORT jint JNICALL VKANDROID(setSurface)(JNIEnv *env, jclass, jlong handle, jobject surface) {
    ANativeWindow *window = surface != nullptr ? ANativeWindow_fromSurface(env, surface) : nullptr;
    return context(handle)->setWindow(window, lastError);
}

JNIEXPORT jboolean JNICALL VKANDROID(supportsExternalTextures)(JNIEnv *, jclass, jlong handle) {
    return context(handle)->supportsExternalTextures() && fromHardwareBuffer() != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL VKANDROID(importHardwareBuffer)(JNIEnv *env, jclass, jlong handle, jobject hardwareBuffer) {
    auto from = fromHardwareBuffer();
    if (from == nullptr) {
        return 0;
    }
    // The import takes its own reference, the Java object can be closed afterwards.
    return reinterpret_cast<jlong>(context(handle)->importHardwareBuffer(from(env, hardwareBuffer)));
}

JNIEXPORT jlong JNICALL VKANDROID(textureYcbcrSampler)(JNIEnv *, jclass, jlong, jlong texture) {
    return reinterpret_cast<jlong>(reinterpret_cast<verktex::Texture *>(texture)->ycbcr);
}

} // extern "C"
