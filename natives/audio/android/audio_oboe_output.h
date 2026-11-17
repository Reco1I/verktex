#pragma once

#include "audio_engine.h"

#include <oboe/Oboe.h>

#include <atomic>
#include <memory>
#include <mutex>

namespace verktex::audio {

// Plays the engine through Oboe: AAudio from Android 8.1, OpenSL ES before. A low latency exclusive
// stream (Oboe falls back to a shared one) of stereo float at the device's own rate, with a buffer of
// two bursts. Reopened on another device when the current one is disconnected.
class OboeOutput final : public AudioOutput,
                         public oboe::AudioStreamDataCallback,
                         public oboe::AudioStreamErrorCallback {
public:
    ~OboeOutput() override;

    bool start(Engine &engine) override;
    void stop() override;

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    bool open();
    void close();

    std::mutex lock;
    Engine *engine = nullptr;
    std::shared_ptr<oboe::AudioStream> stream;
    bool started = false;

    // Only the callback's: the latency is measured every few callbacks.
    int callbacksSinceLatency = 0;
};

}
