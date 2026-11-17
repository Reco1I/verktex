#include "audio_oboe_output.h"

#include <android/log.h>

#define LOG_TAG "VerktexAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace verktex::audio {

namespace {

// How often the latency is measured, in callbacks: a few times a second.
constexpr int kLatencyInterval = 64;

}

OboeOutput::~OboeOutput() {
    stop();
}

bool OboeOutput::start(Engine &target) {
    std::lock_guard<std::mutex> guard(lock);
    engine = &target;
    started = true;
    if (stream) return true;
    return open();
}

void OboeOutput::stop() {
    std::lock_guard<std::mutex> guard(lock);
    started = false;
    close();
}

bool OboeOutput::open() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setUsage(oboe::Usage::Game)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK) {
        LOGW("Couldn't open the output: %s", oboe::convertToText(result));
        stream.reset();
        return false;
    }

    engine->setOutputRate(stream->getSampleRate());

    // Two bursts: one being played, one being written.
    stream->setBufferSizeInFrames(stream->getFramesPerBurst() * 2);

    result = stream->requestStart();
    if (result != oboe::Result::OK) {
        LOGW("Couldn't start the output: %s", oboe::convertToText(result));
        stream->close();
        stream.reset();
        return false;
    }

    LOGI("Output opened: %s, %s, %d Hz, bursts of %d frames, a buffer of %d",
         stream->getAudioApi() == oboe::AudioApi::AAudio ? "AAudio" : "OpenSL ES",
         stream->getSharingMode() == oboe::SharingMode::Exclusive ? "exclusive" : "shared",
         stream->getSampleRate(), stream->getFramesPerBurst(), stream->getBufferSizeInFrames());
    callbacksSinceLatency = kLatencyInterval;
    return true;
}

void OboeOutput::close() {
    if (!stream) return;
    stream->requestStop();
    stream->close();
    stream.reset();
}

oboe::DataCallbackResult OboeOutput::onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {
    engine->render(static_cast<float *>(audioData), numFrames);

    // From the timestamps of the frames presented, once they're available.
    if (++callbacksSinceLatency >= kLatencyInterval) {
        callbacksSinceLatency = 0;
        auto latency = audioStream->calculateLatencyMillis();
        if (latency) engine->setOutputLatency(latency.value() / 1000.0);
    }
    return oboe::DataCallbackResult::Continue;
}

void OboeOutput::onErrorAfterClose(oboe::AudioStream *, oboe::Result error) {
    // Oboe calls it from a thread of its own, where the stream can be opened again: on the device that
    // is now the default one, e.g. the speaker once headphones are unplugged.
    LOGW("The output was closed: %s, reopening it", oboe::convertToText(error));

    std::lock_guard<std::mutex> guard(lock);
    stream.reset();
    if (started) open();
}

}
