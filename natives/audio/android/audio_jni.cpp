// The JNI functions of com.reco1l.verktex.android.oboe.OboeNative, over the audio core and its Oboe
// output. Handles are pointers: of a NativeAudio (the engine), a SampleHandle (a decoded sample) or a
// Playback, which Kotlin never uses after releasing them.

#include "audio_engine.h"
#include "audio_oboe_output.h"

#include <jni.h>
#include <pthread.h>

#include <memory>

using namespace verktex::audio;

namespace {

struct NativeAudio {
    Engine engine;
    OboeOutput output;
};

struct SampleHandle {
    std::shared_ptr<const SampleData> data;
};

JavaVM *javaVm = nullptr;
pthread_key_t detachKey;

// The JNIEnv of the current thread, attached to the JVM until it exits (the service thread calls end
// listeners).
JNIEnv *currentEnv() {
    JNIEnv *env = nullptr;
    if (javaVm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) return env;
#ifdef __ANDROID__
    jint attached = javaVm->AttachCurrentThread(&env, nullptr);
#else
    // The JDK's jni.h, only to check this file off Android.
    jint attached = javaVm->AttachCurrentThread(reinterpret_cast<void **>(&env), nullptr);
#endif
    if (attached != JNI_OK) return nullptr;
    pthread_setspecific(detachKey, env);
    return env;
}

// A global reference, deleted with the last copy of the listener holding it.
struct GlobalRef {
    jobject object;

    explicit GlobalRef(JNIEnv *env, jobject local) : object(env->NewGlobalRef(local)) {}

    ~GlobalRef() {
        if (JNIEnv *env = currentEnv()) env->DeleteGlobalRef(object);
    }
};

FileData toFileData(JNIEnv *env, jbyteArray bytes) {
    auto data = std::make_shared<std::vector<uint8_t>>(size_t(env->GetArrayLength(bytes)));
    env->GetByteArrayRegion(bytes, 0, jsize(data->size()), reinterpret_cast<jbyte *>(data->data()));
    return data;
}

inline NativeAudio *audio(jlong handle) { return reinterpret_cast<NativeAudio *>(handle); }
// Playbacks cross JNI as pointers to their base class.
inline jlong toHandle(Playback *playback) { return reinterpret_cast<jlong>(playback); }
inline Playback *playback(jlong handle) { return reinterpret_cast<Playback *>(handle); }
inline StreamPlayback *stream(jlong handle) { return static_cast<StreamPlayback *>(playback(handle)); }
inline SamplePlayback *samplePlayback(jlong handle) { return static_cast<SamplePlayback *>(playback(handle)); }
inline SampleHandle *sample(jlong handle) { return reinterpret_cast<SampleHandle *>(handle); }

}

#define JNI_FUNCTION(returns, name) extern "C" JNIEXPORT returns JNICALL Java_com_reco1l_verktex_android_oboe_OboeNative_##name

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    javaVm = vm;
    pthread_key_create(&detachKey, [](void *) { javaVm->DetachCurrentThread(); });
    return JNI_VERSION_1_6;
}

//region Engine

JNI_FUNCTION(jlong, createEngine)(JNIEnv *, jclass) {
    return reinterpret_cast<jlong>(new NativeAudio());
}

JNI_FUNCTION(void, destroyEngine)(JNIEnv *, jclass, jlong handle) {
    auto *native = audio(handle);
    native->output.stop();
    delete native;
}

JNI_FUNCTION(jboolean, startOutput)(JNIEnv *, jclass, jlong handle) {
    auto *native = audio(handle);
    return native->output.start(native->engine);
}

JNI_FUNCTION(void, stopOutput)(JNIEnv *, jclass, jlong handle) {
    audio(handle)->output.stop();
}

JNI_FUNCTION(jdouble, outputLatency)(JNIEnv *, jclass, jlong handle) {
    return audio(handle)->engine.getOutputLatency();
}

JNI_FUNCTION(jint, outputRate)(JNIEnv *, jclass, jlong handle) {
    return audio(handle)->engine.getOutputRate();
}

//endregion

//region Samples

JNI_FUNCTION(jlong, decodeSample)(JNIEnv *env, jclass, jbyteArray bytes) {
    auto data = Engine::decodeSample(toFileData(env, bytes));
    if (!data) return 0;
    return reinterpret_cast<jlong>(new SampleHandle{std::move(data)});
}

JNI_FUNCTION(void, freeSample)(JNIEnv *, jclass, jlong handle) {
    // Its playbacks keep the audio until they're released.
    delete sample(handle);
}

JNI_FUNCTION(jlong, createSamplePlayback)(JNIEnv *, jclass, jlong engine, jlong handle) {
    return toHandle(audio(engine)->engine.createSamplePlayback(sample(handle)->data));
}

JNI_FUNCTION(void, setSamplePlaybackRate)(JNIEnv *, jclass, jlong handle, jfloat rate) {
    samplePlayback(handle)->setRate(rate);
}

//endregion

//region Streams

JNI_FUNCTION(jlong, createStream)(JNIEnv *env, jclass, jlong engine, jbyteArray bytes) {
    return toHandle(audio(engine)->engine.createStream(toFileData(env, bytes)));
}

JNI_FUNCTION(jint, streamSampleRate)(JNIEnv *, jclass, jlong handle) {
    return stream(handle)->sampleRate();
}

JNI_FUNCTION(void, setStreamFrequency)(JNIEnv *, jclass, jlong handle, jfloat hz) {
    stream(handle)->setFrequency(hz);
}

JNI_FUNCTION(void, setStreamTempo)(JNIEnv *, jclass, jlong handle, jfloat tempo) {
    stream(handle)->setTempo(tempo);
}

JNI_FUNCTION(void, setStreamLowPassCutoff)(JNIEnv *, jclass, jlong handle, jfloat hz) {
    stream(handle)->setLowPassCutoff(hz);
}

JNI_FUNCTION(void, setStreamHighPassCutoff)(JNIEnv *, jclass, jlong handle, jfloat hz) {
    stream(handle)->setHighPassCutoff(hz);
}

JNI_FUNCTION(jboolean, readStreamFFT)(JNIEnv *env, jclass, jlong handle, jfloatArray output) {
    float magnitudes[StreamPlayback::kFftSize / 2];
    if (!stream(handle)->readFFT(magnitudes)) return false;
    env->SetFloatArrayRegion(output, 0, StreamPlayback::kFftSize / 2, magnitudes);
    return true;
}

JNI_FUNCTION(void, setStreamEndListener)(JNIEnv *env, jclass, jlong handle, jobject listener) {
    if (!listener) {
        stream(handle)->setEndListener(nullptr);
        return;
    }

    auto ref = std::make_shared<GlobalRef>(env, listener);
    stream(handle)->setEndListener([ref] {
        JNIEnv *threadEnv = currentEnv();
        if (!threadEnv) return;

        jclass runnable = threadEnv->GetObjectClass(ref->object);
        jmethodID run = threadEnv->GetMethodID(runnable, "run", "()V");
        threadEnv->CallVoidMethod(ref->object, run);
        if (threadEnv->ExceptionCheck()) {
            threadEnv->ExceptionDescribe();
            threadEnv->ExceptionClear();
        }
        threadEnv->DeleteLocalRef(runnable);
    });
}

//endregion

//region Playbacks

JNI_FUNCTION(void, release)(JNIEnv *, jclass, jlong engine, jlong handle) {
    audio(engine)->engine.release(playback(handle));
}

JNI_FUNCTION(jint, status)(JNIEnv *, jclass, jlong handle) {
    return jint(playback(handle)->status());
}

JNI_FUNCTION(jdouble, length)(JNIEnv *, jclass, jlong handle) {
    return playback(handle)->length();
}

JNI_FUNCTION(jdouble, position)(JNIEnv *, jclass, jlong handle) {
    return playback(handle)->position();
}

JNI_FUNCTION(void, setPosition)(JNIEnv *, jclass, jlong handle, jdouble seconds) {
    playback(handle)->setPosition(seconds);
}

JNI_FUNCTION(jfloat, volume)(JNIEnv *, jclass, jlong handle) {
    return playback(handle)->volume();
}

JNI_FUNCTION(void, setVolume)(JNIEnv *, jclass, jlong handle, jfloat volume) {
    playback(handle)->setVolume(volume);
}

JNI_FUNCTION(void, setLooping)(JNIEnv *, jclass, jlong handle, jboolean looping) {
    playback(handle)->setLooping(looping);
}

JNI_FUNCTION(void, play)(JNIEnv *, jclass, jlong handle, jboolean restart) {
    playback(handle)->play(restart);
}

JNI_FUNCTION(void, pause)(JNIEnv *, jclass, jlong handle) {
    playback(handle)->pause();
}

JNI_FUNCTION(void, stop)(JNIEnv *, jclass, jlong handle) {
    playback(handle)->stop();
}

//endregion
