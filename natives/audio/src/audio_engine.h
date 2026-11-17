#pragma once

#include "audio_decoder.h"
#include "audio_spsc_queue.h"

#include <array>
#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

// The native audio core of Verktex: decodes, mixes, resamples and stretches audio for an AudioOutput,
// see docs/audio-backends.md. Platform independent, the output (Oboe on Android) is the platform's.
//
// Threads: the application's threads call the public functions, the output's callback calls
// Engine::render, and a service thread of the engine decodes streams, calls their end listeners and
// deletes what was released. The callback never locks, allocates or waits.
//
// Playbacks are owned by the engine: from their creation to their release they're in the mixer, which
// skips the idle ones, and once released the callback hands them to the service thread to delete.

namespace verktex::audio {

class Engine;

// The values of BASS_ACTIVE_*, so the drivers' statuses read the same.
enum class Status : int {
    Stopped = 0,
    Playing = 1,
    Stalled = 2,
    Paused = 3,
};

// Where the engine's mixed audio goes. The output calls Engine::render from its own thread.
class AudioOutput {
public:
    virtual ~AudioOutput() = default;

    // Opens the device and starts calling Engine::render, false if it can't.
    virtual bool start(Engine &engine) = 0;

    // Stops calling Engine::render, until start is called again.
    virtual void stop() = 0;
};

// A playback of a sample or a stream.
class Playback {
public:
    virtual ~Playback() = default;

    Status status() const { return Status(statusValue.load(std::memory_order_acquire)); }

    // In seconds, of the source.
    virtual double position() const;
    void setPosition(double seconds);
    double length() const { return sourceRate > 0 ? double(sourceLength) / sourceRate : 0; }
    int sampleRate() const { return sourceRate; }

    float volume() const { return targetVolume.load(std::memory_order_relaxed); }
    void setVolume(float volume) { targetVolume.store(volume, std::memory_order_relaxed); }

    bool looping() const { return isLooping.load(std::memory_order_relaxed); }
    void setLooping(bool looping) { isLooping.store(looping, std::memory_order_relaxed); }

    // Starts or continues playing, from the start if `restart` or once it ended.
    void play(bool restart);
    void pause();
    // Stops and moves to the start.
    void stop();

protected:
    friend class Engine;

    Playback(Engine &engine, int sampleRate, int64_t lengthFrames);

    // Adds `count` frames of this playback to `output` (interleaved stereo), at the output's rate.
    // The mixer calls it whatever the status, to apply seeks.
    virtual void render(float *output, int count, int outputRate) = 0;

    // Moves to `frame` of the source, from the application's threads.
    virtual void seekTo(int64_t frame);

    // From the callback: the end was reached, unless a seek came since `serial`.
    void markEnded(uint32_t serial);

    // Moves the gain towards the volume over a few milliseconds, to avoid clicks.
    float nextGain(float step);

    Engine &engine;
    const int sourceRate;
    const int64_t sourceLength;

    std::atomic<int> statusValue{int(Status::Stopped)};
    std::atomic<float> targetVolume{1.f};
    std::atomic<bool> isLooping{false};
    std::atomic<bool> isAtEnd{false};
    std::atomic<bool> endedPending{false};
    std::atomic<bool> released{false};

    // Seeks are posted: the callback applies the latest.
    std::atomic<uint32_t> seekSerial{0};
    std::atomic<int64_t> seekFrame{0};
    std::atomic<double> publishedPosition{0};

    // Only the callback's.
    float gain = -1.f;
};

// A decoded sample, shared by its playbacks.
struct SampleData {
    std::vector<float> frames;
    int sampleRate = 0;
    int64_t length = 0;
};

// A playback of a sample, resampled by the callback.
class SamplePlayback final : public Playback {
public:
    SamplePlayback(Engine &engine, std::shared_ptr<const SampleData> data);

    // How fast the sample plays, changing its pitch: 1 plays it at its own rate.
    void setRate(float rate) { playbackRate.store(rate, std::memory_order_relaxed); }
    float rate() const { return playbackRate.load(std::memory_order_relaxed); }

protected:
    void render(float *output, int count, int outputRate) override;

private:
    std::shared_ptr<const SampleData> data;
    std::atomic<float> playbackRate{1.f};

    // Only the callback's.
    double cursor = 0;
    uint32_t appliedSeek = 0;
};

// A stream, decoded ahead by the engine's service thread: decoder → time stretch → a buffer of
// chunks at the source's rate, which the callback resamples to the frequency, so a change of frequency
// is heard right away.
class StreamPlayback final : public Playback {
public:
    StreamPlayback(Engine &engine, std::unique_ptr<Decoder> decoder);
    ~StreamPlayback() override;

    // The source position being heard: the latency of the output and the buffers are accounted for.
    double position() const override;

    // The rate in Hz the stretched audio plays at, changing its pitch: sampleRate() plays it as is.
    void setFrequency(float hz) { frequency.store(hz, std::memory_order_relaxed); }
    float getFrequency() const { return frequency.load(std::memory_order_relaxed); }

    // How fast the stream plays keeping its pitch: 1 plays it as is. Applied with a short glitch while
    // playing.
    void setTempo(float value) { tempo.store(value, std::memory_order_relaxed); }
    float getTempo() const { return tempo.load(std::memory_order_relaxed); }

    // Called by the service thread when the stream reaches its end.
    void setEndListener(std::function<void()> listener);

    // The magnitudes of the 512 bands of a 1024 point FFT of the latest audio played (Hann window,
    // a full scale sine reads about 1), false if it isn't playing.
    bool readFFT(float *magnitudes);

    // Decodes until the buffer holds what it should. The service thread calls it, and seeks.
    void produce();

    static constexpr int kFftSize = 1024;

protected:
    void render(float *output, int count, int outputRate) override;
    void seekTo(int64_t frame) override;

private:
    static constexpr int kChunkFrames = 256;
    static constexpr uint64_t kChunkCount = 64;
    static constexpr double kBufferSeconds = 0.1;

    // Stretched audio at the source's rate. The last chunk of the stream is empty.
    struct Chunk {
        std::array<float, kChunkFrames * 2> frames{};
        int count = 0;
        // The source frame of the first frame, and the source frames per frame (the tempo).
        double sourceStart = 0;
        double sourceStep = 0;
        uint32_t generation = 0;
        bool last = false;
    };

    // A frame of the resampler's window.
    struct WindowFrame {
        float left = 0, right = 0;
        double source = 0, step = 0;
        bool valid = false;
    };

    // The callback's: takes the next stretched frame from the chunks, false when there's none yet
    // (`ended` tells whether there won't be any).
    bool pullFrame(WindowFrame &frame, uint32_t currentGeneration, bool &ended);

    // The producer's, under producerLock.
    void produceLocked();
    void restart(int64_t frame);
    bool fillStretched(size_t frames);
    void stretchBlock();
    void appendDecoded(int64_t count);
    int64_t decode(float *output, int64_t count);
    void dropWritten();

    std::unique_ptr<Decoder> decoder;
    std::atomic<float> frequency;
    std::atomic<float> tempo{1.f};

    std::mutex producerLock;
    // The decoder reached the end of the file, no more stretched audio will come, and every chunk was written.
    bool decoderEnded = false;
    bool sourceDone = false;
    bool finished = false;
    int64_t decodePosition = 0;
    // The stretched audio not yet in chunks, from `stretchedRead`, and the source frame of each frame.
    std::vector<float> stretched;
    std::vector<double> stretchedSource;
    size_t stretchedRead = 0;
    float stretchTempo = 1.f;
    double stretchAccumulator = 0;
    double stretchSourceNext = 0;
    struct Stretcher;
    std::unique_ptr<Stretcher> stretcher;
    std::vector<float> decodeBuffer;

    // The chunks, written by the producer and read by the callback. A seek starts a new generation,
    // the callback drops the chunks of the previous ones.
    std::vector<Chunk> chunks;
    std::atomic<uint64_t> chunkWrite{0};
    std::atomic<uint64_t> chunkRead{0};
    uint64_t generationStartWrite = 0;
    std::atomic<uint32_t> generation{0};
    std::atomic<double> seekSeconds{0};

    std::mutex listenerLock;
    std::function<void()> endListener;

    // Only the callback's.
    int chunkOffset = 0;
    uint32_t consumedGeneration = 0;
    bool primed = false;
    double lastHeard = 0;
    std::array<WindowFrame, 4> window{};
    bool windowFilled = false;
    double fraction = 0;
    std::array<float, kFftSize> fftHistory{};
    std::atomic<uint32_t> fftWrite{0};

    // The source position heard, published by the callback with the time and the speed, for
    // position() to extrapolate between callbacks.
    std::atomic<uint32_t> clockVersion{0};
    std::atomic<double> clockPosition{0};
    std::atomic<int64_t> clockTime{0};
    std::atomic<double> clockSpeed{0};
    std::atomic<int64_t> clockSpan{0};
    std::atomic<uint32_t> clockGeneration{0};
    mutable std::atomic<double> lastReturned{0};
    mutable std::atomic<uint32_t> lastReturnedGeneration{~0u};

    friend class Engine;
};

class Engine {
public:
    Engine();
    ~Engine();

    // The output's rate and latency, which the output sets once it knows them, and again when its
    // device changes.
    void setOutputRate(int rate) { outputRate.store(rate, std::memory_order_relaxed); }
    int getOutputRate() const { return outputRate.load(std::memory_order_relaxed); }
    void setOutputLatency(double seconds) { outputLatency.store(seconds, std::memory_order_relaxed); }
    double getOutputLatency() const { return outputLatency.load(std::memory_order_relaxed); }

    // Mixes `count` frames of every playing playback into `output` (interleaved stereo), replacing
    // what it holds. Only the output's callback calls it.
    void render(float *output, int count);

    // Decodes a whole file, null if it can't be decoded.
    static std::shared_ptr<const SampleData> decodeSample(const FileData &data);

    // The playbacks are owned by the engine: they're released, never deleted. Null if the data can't
    // be decoded.
    SamplePlayback *createSamplePlayback(std::shared_ptr<const SampleData> data);
    StreamPlayback *createStream(const FileData &data);
    void release(Playback *playback);

    // The monotonic time in nanoseconds positions are extrapolated with.
    static int64_t now();

private:
    void enter(Playback *playback);
    void flushPending();
    void serviceLoop();

    std::atomic<int> outputRate{48000};
    std::atomic<double> outputLatency{0};

    // The playbacks the callback renders, only the callback's, and those entering it.
    std::vector<Playback *> mixing;
    SpscQueue<Playback *> entering{1024};
    SpscQueue<Playback *> garbage{1024};
    std::mutex enteringLock;
    std::vector<Playback *> pending;

    std::mutex streamsLock;
    std::vector<StreamPlayback *> streams;

    std::mutex serviceLock;
    std::condition_variable serviceWake;
    bool stopping = false;
    std::thread service;
};

}
