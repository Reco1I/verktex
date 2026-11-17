// The desktop part of the Vulkan backend's JNI surface, see VKDesktopNative.kt: the context with the
// instance extensions GLFW asks for, and the surface GLFW creates from its instance.

#include "vk_jni.h"

using namespace verktex::jni;

#define VKDESKTOP(name) Java_com_reco1l_verktex_desktop_vulkan_VKDesktopNative_##name

extern "C" {

JNIEXPORT jlong JNICALL VKDESKTOP(createContext)(JNIEnv *env, jclass, jboolean enableValidation, jobjectArray instanceExtensions) {
    jsize count = env->GetArrayLength(instanceExtensions);
    std::vector<std::string> names;
    names.reserve(count);
    for (jsize i = 0; i < count; i++) {
        auto name = static_cast<jstring>(env->GetObjectArrayElement(instanceExtensions, i));
        const char *chars = env->GetStringUTFChars(name, nullptr);
        names.emplace_back(chars);
        env->ReleaseStringUTFChars(name, chars);
        env->DeleteLocalRef(name);
    }

    std::vector<const char *> extensions;
    extensions.reserve(names.size());
    for (auto &name : names) extensions.push_back(name.c_str());

    return createContext(enableValidation == JNI_TRUE, extensions);
}

JNIEXPORT jlong JNICALL VKDESKTOP(instance)(JNIEnv *, jclass, jlong handle) {
    return reinterpret_cast<jlong>(context(handle)->instanceHandle());
}

JNIEXPORT jint JNICALL VKDESKTOP(setSurface)(JNIEnv *, jclass, jlong handle, jlong surface) {
    return context(handle)->setSurface(fromHandle<VkSurfaceKHR>(surface), lastError);
}

JNIEXPORT void JNICALL VKDESKTOP(setVsync)(JNIEnv *, jclass, jlong handle, jboolean enabled) {
    context(handle)->setVsync(enabled == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL VKDESKTOP(readPixels)(JNIEnv *env, jclass, jlong handle, jobject pixels, jint width, jint height) {
    auto *data = static_cast<uint8_t *>(env->GetDirectBufferAddress(pixels));
    if (data == nullptr || env->GetDirectBufferCapacity(pixels) < static_cast<jlong>(width) * height * 4) {
        return JNI_FALSE;
    }
    return context(handle)->readPixels(data, static_cast<uint32_t>(width), static_cast<uint32_t>(height)) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
