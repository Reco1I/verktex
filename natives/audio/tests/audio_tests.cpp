// The tests of the native audio core: the decoders, and the engine rendered by the test itself as if it
// were the output's callback. Run with ctest, see natives/audio/CMakeLists.txt.

#include "audio_engine.h"

#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <functional>
#include <iterator>
#include <string>
#include <thread>
#include <vector>

using namespace verktex::audio;

namespace {

int failures = 0;
std::string currentTest;

#define CHECK(condition)                                                                        \
    do {                                                                                        \
        if (!(condition)) {                                                                     \
            std::printf("  FAILED %s:%d: %s\n", __FILE__, __LINE__, #condition);                \
            ++failures;                                                                         \
        }                                                                                       \
    } while (0)

#define CHECK_NEAR(actual, expected, tolerance)                                                  \
    do {                                                                                        \
        double a_ = (actual), e_ = (expected);                                                  \
        if (std::fabs(a_ - e_) > (tolerance)) {                                                 \
            std::printf("  FAILED %s:%d: %s = %f, expected %f (+-%f)\n", __FILE__, __LINE__,     \
                        #actual, a_, e_, double(tolerance));                                    \
            ++failures;                                                                         \
        }                                                                                       \
    } while (0)

constexpr int kRate = 44100;
constexpr int kBlock = 256;

FileData readFixture(const std::string &name) {
    std::ifstream file(std::string(VERKTEX_AUDIO_FIXTURES) + "/" + name, std::ios::binary);
    auto bytes = std::make_shared<std::vector<uint8_t>>(std::istreambuf_iterator<char>(file), std::istreambuf_iterator<char>());
    return bytes;
}

// A WAV of 32 bit float stereo frames.
FileData makeWav(const std::vector<float> &stereo, int rate = kRate) {
    auto bytes = std::make_shared<std::vector<uint8_t>>();
    auto put = [&](const void *data, size_t size) {
        auto *b = static_cast<const uint8_t *>(data);
        bytes->insert(bytes->end(), b, b + size);
    };
    auto u32 = [&](uint32_t v) { put(&v, 4); };
    auto u16 = [&](uint16_t v) { put(&v, 2); };

    uint32_t dataSize = uint32_t(stereo.size() * sizeof(float));
    put("RIFF", 4); u32(36 + dataSize); put("WAVE", 4);
    put("fmt ", 4); u32(16); u16(3); u16(2); u32(uint32_t(rate)); u32(uint32_t(rate) * 8); u16(8); u16(32);
    put("data", 4); u32(dataSize); put(stereo.data(), dataSize);
    return bytes;
}

// Frame i of the ramp is i / 100000 on both channels, so a frame tells where it is.
std::vector<float> ramp(int frames) {
    std::vector<float> stereo(size_t(frames) * 2);
    for (int i = 0; i < frames; ++i) stereo[size_t(i) * 2] = stereo[size_t(i) * 2 + 1] = float(i) / 100000.f;
    return stereo;
}

std::vector<float> sine(int frames, float hz, float amplitude) {
    std::vector<float> stereo(size_t(frames) * 2);
    for (int i = 0; i < frames; ++i) {
        stereo[size_t(i) * 2] = stereo[size_t(i) * 2 + 1] = amplitude * std::sin(2.f * float(M_PI) * hz * float(i) / kRate);
    }
    return stereo;
}

std::vector<float> render(Engine &engine, int frames) {
    std::vector<float> output(size_t(frames) * 2);
    for (int done = 0; done < frames; done += kBlock) {
        engine.render(output.data() + size_t(done) * 2, std::min(kBlock, frames - done));
    }
    return output;
}

// Renders with the stream's producer on time, as the service thread is while the output plays in real
// time, whatever the speed of the machine running the tests.
std::vector<float> renderPaced(Engine &engine, int frames, StreamPlayback *stream = nullptr) {
    std::vector<float> output(size_t(frames) * 2);
    for (int done = 0; done < frames; done += kBlock) {
        if (stream) stream->produce();
        engine.render(output.data() + size_t(done) * 2, std::min(kBlock, frames - done));
    }
    return output;
}

bool waitFor(const std::function<bool()> &condition) {
    for (int i = 0; i < 200; ++i) {
        if (condition()) return true;
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
    return condition();
}

//region Decoders

void decodersGiveExactLengths() {
    for (const char *name : {"sine-440-22050-frames.mp3", "sine-440-22050-frames-stereo.mp3",
                             "sine-440-22050-frames.ogg", "sine-440-22050-frames.flac"}) {
        auto decoder = openDecoder(readFixture(name));
        CHECK(decoder != nullptr);
        if (!decoder) continue;

        CHECK(decoder->sampleRate() == kRate);
        // The MP3s' encoder delay and padding are removed, from their LAME tag.
        if (decoder->length() != 22050) std::printf("  %s: length %lld\n", name, (long long) decoder->length());
        CHECK(decoder->length() == 22050);

        std::vector<float> frames(30000 * 2);
        int64_t read = decoder->read(frames.data(), 30000);
        CHECK(read == 22050);

        // The sine is there, on both channels of the mono files too.
        float peak = 0;
        for (int64_t i = 0; i < read; ++i) peak = std::max(peak, std::fabs(frames[size_t(i) * 2 + 1]));
        if (peak <= 0.05f) std::printf("  %s: peak %f\n", name, peak);
        CHECK(peak > 0.05f);
    }
}

void decodersSeekExactly() {
    for (const char *name : {"sine-440-22050-frames.mp3", "sine-440-22050-frames.ogg", "sine-440-22050-frames.flac"}) {
        auto data = readFixture(name);
        auto whole = openDecoder(data);
        std::vector<float> all(22050 * 2);
        whole->read(all.data(), 22050);

        auto seeking = openDecoder(data);
        for (int64_t target : {12345, 3, 20000, 0}) {
            CHECK(seeking->seek(target));
            std::vector<float> part(100 * 2);
            seeking->read(part.data(), 100);

            float difference = 0;
            for (size_t i = 0; i < part.size(); ++i) difference = std::max(difference, std::fabs(part[i] - all[size_t(target) * 2 + i]));
            if (difference > 1e-4f) std::printf("  %s: seeking to %lld differs by %f\n", name, (long long) target, difference);
            CHECK(difference <= 1e-4f);
        }
    }
}

void wavDecodesTheFramesGiven() {
    auto decoder = openDecoder(makeWav(ramp(5000)));
    CHECK(decoder && decoder->length() == 5000);
    CHECK(decoder->seek(4321));
    float frame[2];
    CHECK(decoder->read(frame, 1) == 1);
    CHECK_NEAR(frame[0], 0.04321, 1e-6);
}

void invalidDataIsRejected() {
    auto garbage = std::make_shared<std::vector<uint8_t>>(1000, uint8_t(7));
    CHECK(openDecoder(garbage) == nullptr);
    CHECK(Engine::decodeSample(garbage) == nullptr);
}

//endregion

//region Samples

void samplesPlayAtTheirRate() {
    Engine engine;
    engine.setOutputRate(kRate);

    auto sample = Engine::decodeSample(makeWav(ramp(1000)));
    auto *playback = engine.createSamplePlayback(sample);
    playback->setVolume(0.5f);
    playback->play(false);
    CHECK(playback->status() == Status::Playing);

    auto output = render(engine, 1024);
    // At the same rate the resampler returns the frames as they are.
    CHECK_NEAR(output[0], 0.0, 1e-6);
    CHECK_NEAR(output[500 * 2], 0.5 * 0.005, 1e-6);
    CHECK_NEAR(output[999 * 2 + 1], 0.5 * 0.00999, 1e-6);
    CHECK_NEAR(output[1010 * 2], 0.0, 1e-6);

    CHECK(playback->status() == Status::Stopped);
    CHECK_NEAR(playback->position(), 1000.0 / kRate, 1e-9);

    // Playing again after the end starts over.
    playback->play(false);
    output = render(engine, 256);
    CHECK_NEAR(output[100 * 2], 0.5 * 0.001, 1e-6);
    engine.release(playback);
}

void samplesChangePitchAndLoop() {
    Engine engine;
    engine.setOutputRate(kRate);

    auto sample = Engine::decodeSample(makeWav(ramp(1000)));
    auto *playback = engine.createSamplePlayback(sample);
    playback->setRate(2.f);
    playback->play(false);

    auto output = render(engine, 1024);
    CHECK_NEAR(output[100 * 2], 0.002, 1e-6);
    CHECK(playback->status() == Status::Stopped);

    playback->setRate(1.f);
    playback->setLooping(true);
    playback->play(true);
    output = render(engine, 2560);
    CHECK(playback->status() == Status::Playing);
    CHECK_NEAR(output[(1000 + 10) * 2], 0.0001, 1e-6);

    playback->pause();
    double paused = playback->position();
    render(engine, 512);
    CHECK(playback->status() == Status::Paused);
    CHECK_NEAR(playback->position(), paused, 1e-9);

    playback->stop();
    CHECK(playback->status() == Status::Stopped);
    CHECK_NEAR(playback->position(), 0.0, 1e-9);
    engine.release(playback);
}

void samplesRampTheirVolume() {
    Engine engine;
    engine.setOutputRate(kRate);

    auto sample = Engine::decodeSample(makeWav(std::vector<float>(size_t(kRate) * 2, 1.f)));
    auto *playback = engine.createSamplePlayback(sample);
    playback->play(false);
    render(engine, 256);

    playback->setVolume(0.f);
    auto output = render(engine, 512);
    // Half way through the 5 ms ramp, then silent.
    CHECK(output[0] > 0.9f);
    CHECK_NEAR(output[110 * 2], 0.5, 0.02);
    CHECK_NEAR(output[300 * 2], 0.0, 1e-6);
    engine.release(playback);
}

//endregion

//region Streams

void streamsPlayTheirAudio() {
    Engine engine;
    engine.setOutputRate(kRate);

    auto *stream = engine.createStream(makeWav(ramp(kRate)));
    CHECK(stream != nullptr);
    CHECK_NEAR(stream->length(), 1.0, 1e-9);
    stream->play(false);

    auto output = renderPaced(engine, 4096, stream);
    CHECK_NEAR(output[0], 0.0, 1e-6);
    CHECK_NEAR(output[3000 * 2], 0.03, 1e-6);
    CHECK_NEAR(output[4095 * 2 + 1], 0.04095, 1e-6);

    // Without latency, the frames of the latest callback are being heard.
    CHECK_NEAR(stream->position(), (4096.0 - 256) / kRate, 0.006);
    engine.release(stream);
}

void streamsSeek() {
    Engine engine;
    engine.setOutputRate(kRate);

    auto *stream = engine.createStream(makeWav(ramp(kRate)));
    stream->play(false);
    renderPaced(engine, 2048, stream);

    stream->setPosition(0.5);
    CHECK_NEAR(stream->position(), 0.5, 1e-9);

    auto output = renderPaced(engine, 512, stream);
    CHECK_NEAR(output[0], 22050 / 100000.0, 1e-6);
    CHECK_NEAR(output[300 * 2], 22350 / 100000.0, 1e-6);
    CHECK(stream->position() >= 0.5);

    // While paused, a seek is kept and played from.
    stream->pause();
    stream->setPosition(0.25);
    renderPaced(engine, 512, stream);
    CHECK_NEAR(stream->position(), 0.25, 1e-9);
    stream->play(false);
    output = renderPaced(engine, 256, stream);
    CHECK_NEAR(output[0], 11025 / 100000.0, 1e-6);
    engine.release(stream);
}

void streamsIgnoreTheOutputLatency() {
    Engine engine;
    engine.setOutputRate(kRate);
    engine.setOutputLatency(0.05);

    auto *stream = engine.createStream(makeWav(ramp(kRate)));
    stream->setPosition(0.2);
    stream->play(false);

    // As BASS's, the position is the one rendered, whatever the latency, and moves forward only.
    renderPaced(engine, 1024, stream);
    CHECK_NEAR(stream->position(), 0.2 + (1024.0 - 256) / kRate, 0.006);

    double previous = 0;
    bool monotonic = true;
    for (int i = 0; i < 40; ++i) {
        renderPaced(engine, kBlock, stream);
        double position = stream->position();
        if (position < previous) monotonic = false;
        previous = position;
    }
    CHECK(monotonic);
    // 1024 + 40 * 256 frames rendered, the latest callback's being the position.
    CHECK_NEAR(previous, 0.2 + (1024.0 + 39 * 256) / kRate, 0.01);
    engine.release(stream);
}

void streamsChangeTempoKeepingPitch() {
    Engine engine;
    engine.setOutputRate(kRate);

    auto *stream = engine.createStream(makeWav(sine(kRate * 4, 1000.f, 0.5f)));
    stream->setTempo(1.5f);
    stream->setPosition(0);
    stream->play(false);

    double previous = 0;
    bool monotonic = true;
    std::vector<float> latest;
    for (int i = 0; i < kRate / kBlock; ++i) {
        latest = renderPaced(engine, kBlock, stream);
        double position = stream->position();
        if (position < previous) monotonic = false;
        previous = position;
    }
    CHECK(monotonic);
    // One second of output played 1.5 seconds of the source.
    CHECK_NEAR(previous, 1.5 * (kRate / kBlock - 1) * kBlock / double(kRate), 0.03);

    // At the same pitch: the strongest band is still 1 kHz.
    float spectrum[512];
    CHECK(stream->readFFT(spectrum));
    int peak = 0;
    for (int i = 1; i < 512; ++i) if (spectrum[i] > spectrum[peak]) peak = i;
    CHECK_NEAR(peak, 1000.0 * StreamPlayback::kFftSize / kRate, 1.0);
    engine.release(stream);
}

void streamsChangePitchWithFrequency() {
    Engine engine;
    engine.setOutputRate(kRate);

    auto *stream = engine.createStream(makeWav(sine(kRate * 2, 1000.f, 0.5f)));
    stream->setFrequency(kRate * 2.f);
    stream->play(false);
    renderPaced(engine, 8192, stream);

    float spectrum[512];
    CHECK(stream->readFFT(spectrum));
    int peak = 0;
    for (int i = 1; i < 512; ++i) if (spectrum[i] > spectrum[peak]) peak = i;
    CHECK_NEAR(peak, 2000.0 * StreamPlayback::kFftSize / kRate, 1.0);
    // A half scale sine reads about half.
    CHECK_NEAR(spectrum[peak], 0.5, 0.15);

    CHECK_NEAR(stream->position(), 2.0 * (8192 - 256) / kRate, 0.01);
    engine.release(stream);
}

void streamsEndOnceAndLoop() {
    Engine engine;
    engine.setOutputRate(kRate);

    auto *stream = engine.createStream(makeWav(ramp(4000)));
    std::atomic<int> ends{0};
    stream->setEndListener([&] { ++ends; });
    stream->play(false);

    renderPaced(engine, 8192, stream);
    CHECK(stream->status() == Status::Stopped);
    CHECK_NEAR(stream->position(), 4000.0 / kRate, 1e-9);
    CHECK(waitFor([&] { return ends.load() == 1; }));
    renderPaced(engine, 1024, stream);
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    CHECK(ends.load() == 1);

    stream->setLooping(true);
    stream->play(false);
    auto output = renderPaced(engine, 8192, stream);
    CHECK(stream->status() == Status::Playing);
    CHECK(ends.load() == 1);
    // Past the end it's back at the start.
    CHECK_NEAR(output[4100 * 2], 100 / 100000.0, 1e-6);
    engine.release(stream);
}

// The root mean square of the left channel of `stereo`, from frame `from`.
double rms(const std::vector<float> &stereo, size_t from) {
    double sum = 0;
    size_t count = 0;
    for (size_t i = from * 2; i < stereo.size(); i += 2, ++count) sum += double(stereo[i]) * stereo[i];
    return count > 0 ? std::sqrt(sum / count) : 0;
}

void filtersAttenuateAcrossTheirCutoff() {
    auto signal = sine(kRate, 2000, 0.5f);
    auto filter = [&](BiquadFilter::Type type, float cutoff) {
        BiquadFilter biquad(type);
        biquad.configure(cutoff, kRate, true);
        std::vector<float> output(signal.size());
        for (size_t i = 0; i < signal.size(); i += 2) {
            output[i] = biquad.process(0, signal[i]);
            output[i + 1] = biquad.process(1, signal[i + 1]);
        }
        return rms(output, 4096);
    };
    double input = rms(signal, 4096);

    // A 2 kHz sine two octaves above a low-pass is about 24 dB down, two below a high-pass too.
    CHECK(filter(BiquadFilter::Type::LowPass, 500) < input * 0.1);
    CHECK(filter(BiquadFilter::Type::HighPass, 8000) < input * 0.1);
    // Far from the cutoff it passes.
    CHECK_NEAR(filter(BiquadFilter::Type::LowPass, 15000), input, input * 0.02);
    CHECK_NEAR(filter(BiquadFilter::Type::HighPass, 50), input, input * 0.02);

    BiquadFilter bypassed(BiquadFilter::Type::LowPass);
    bypassed.configure(100, kRate, false);
    CHECK(!bypassed.isActive());
    CHECK(bypassed.process(0, 0.25f) == 0.25f);
}

void streamsFilterTheirAudio() {
    Engine engine;
    engine.setOutputRate(kRate);

    auto *stream = engine.createStream(makeWav(sine(kRate, 2000, 0.5f)));
    stream->play(false);
    double unfiltered = rms(renderPaced(engine, 8192, stream), 0);

    stream->setLowPassCutoff(500);
    renderPaced(engine, 2048, stream);
    double lowPassed = rms(renderPaced(engine, 8192, stream), 0);
    CHECK(lowPassed < unfiltered * 0.15);

    // Off again at the maximum cutoff.
    stream->setLowPassCutoff(StreamPlayback::kMaxLowPassCutoff);
    renderPaced(engine, 2048, stream);
    CHECK_NEAR(rms(renderPaced(engine, 8192, stream), 0), unfiltered, unfiltered * 0.05);
    engine.release(stream);
}

void streamsStallWithoutAudio() {
    Engine engine;
    engine.setOutputRate(kRate);

    auto *stream = engine.createStream(makeWav(ramp(kRate * 2)));
    stream->play(false);
    // Rendering faster than the producer runs out of the buffer at some point.
    bool stalled = false;
    for (int i = 0; i < 400 && !stalled; ++i) {
        render(engine, kBlock);
        stalled = stream->status() == Status::Stalled;
    }
    CHECK(stalled);
    CHECK(waitFor([&] {
        render(engine, 64);
        return stream->status() == Status::Playing;
    }));
    engine.release(stream);
}

void releasedPlaybacksAreDeleted() {
    Engine engine;
    engine.setOutputRate(kRate);

    auto sample = Engine::decodeSample(makeWav(ramp(1000)));
    for (int i = 0; i < 2000; ++i) {
        auto *playback = engine.createSamplePlayback(sample);
        playback->play(false);
        if (i % 50 == 0) render(engine, 64);
        engine.release(playback);
    }
    for (int i = 0; i < 50; ++i) render(engine, 64);

    // Only the test holds the sample once every playback was deleted.
    CHECK(waitFor([&] {
        render(engine, 64);
        return sample.use_count() == 1;
    }));
}

//endregion

void run(const char *name, void (*test)()) {
    int before = failures;
    currentTest = name;
    test();
    std::printf("%s %s\n", failures == before ? "PASS" : "FAIL", name);
}

}

int main() {
    run("decodersGiveExactLengths", decodersGiveExactLengths);
    run("decodersSeekExactly", decodersSeekExactly);
    run("wavDecodesTheFramesGiven", wavDecodesTheFramesGiven);
    run("invalidDataIsRejected", invalidDataIsRejected);
    run("samplesPlayAtTheirRate", samplesPlayAtTheirRate);
    run("samplesChangePitchAndLoop", samplesChangePitchAndLoop);
    run("samplesRampTheirVolume", samplesRampTheirVolume);
    run("streamsPlayTheirAudio", streamsPlayTheirAudio);
    run("streamsSeek", streamsSeek);
    run("streamsIgnoreTheOutputLatency", streamsIgnoreTheOutputLatency);
    run("streamsChangeTempoKeepingPitch", streamsChangeTempoKeepingPitch);
    run("streamsChangePitchWithFrequency", streamsChangePitchWithFrequency);
    run("streamsEndOnceAndLoop", streamsEndOnceAndLoop);
    run("filtersAttenuateAcrossTheirCutoff", filtersAttenuateAcrossTheirCutoff);
    run("streamsFilterTheirAudio", streamsFilterTheirAudio);
    run("streamsStallWithoutAudio", streamsStallWithoutAudio);
    run("releasedPlaybacksAreDeleted", releasedPlaybacksAreDeleted);

    std::printf(failures == 0 ? "All tests passed\n" : "%d checks failed\n", failures);
    return failures == 0 ? 0 : 1;
}
