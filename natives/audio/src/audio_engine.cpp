#include "audio_engine.h"
#include "audio_fft.h"

#include "signalsmith-stretch.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>

namespace verktex::audio {

namespace {

// Volume changes take this long, to avoid clicks.
constexpr float kGainRampSeconds = 0.005f;

// How far position() extrapolates past the latest callback, a few callbacks.
constexpr int64_t kMaxExtrapolationNanos = 50'000'000;

inline float hermite(float x0, float x1, float x2, float x3, float t) {
    float c1 = 0.5f * (x2 - x0);
    float c2 = x0 - 2.5f * x1 + 2.f * x2 - 0.5f * x3;
    float c3 = 0.5f * (x3 - x0) + 1.5f * (x1 - x2);
    return ((c3 * t + c2) * t + c1) * t + x1;
}

}

//region Playback

Playback::Playback(Engine &engine, int sampleRate, int64_t lengthFrames)
    : engine(engine), sourceRate(sampleRate), sourceLength(lengthFrames) {}

double Playback::position() const {
    return publishedPosition.load(std::memory_order_relaxed);
}

void Playback::setPosition(double seconds) {
    seekTo(int64_t(std::llround(std::max(0.0, seconds) * sourceRate)));
}

void Playback::seekTo(int64_t frame) {
    frame = std::clamp<int64_t>(frame, 0, sourceLength);
    seekFrame.store(frame, std::memory_order_relaxed);
    publishedPosition.store(double(frame) / sourceRate, std::memory_order_relaxed);
    isAtEnd.store(false, std::memory_order_relaxed);
    seekSerial.fetch_add(1, std::memory_order_release);
}

void Playback::play(bool restart) {
    if (restart || isAtEnd.load(std::memory_order_relaxed)) seekTo(0);
    statusValue.store(int(Status::Playing), std::memory_order_release);
}

void Playback::pause() {
    int expected = int(Status::Playing);
    if (!statusValue.compare_exchange_strong(expected, int(Status::Paused))) {
        expected = int(Status::Stalled);
        statusValue.compare_exchange_strong(expected, int(Status::Paused));
    }
}

void Playback::stop() {
    statusValue.store(int(Status::Stopped), std::memory_order_release);
    seekTo(0);
}

void Playback::markEnded(uint32_t serial) {
    // A seek or a restart since then wins over the end.
    if (seekSerial.load(std::memory_order_acquire) != serial) return;

    int expected = int(Status::Playing);
    bool ended = statusValue.compare_exchange_strong(expected, int(Status::Stopped));
    if (!ended && expected == int(Status::Stalled)) {
        ended = statusValue.compare_exchange_strong(expected, int(Status::Stopped));
    }
    if (!ended) return;

    publishedPosition.store(length(), std::memory_order_relaxed);
    isAtEnd.store(true, std::memory_order_relaxed);
    endedPending.store(true, std::memory_order_release);
}

float Playback::nextGain(float step) {
    float target = targetVolume.load(std::memory_order_relaxed);
    if (gain < 0) {
        gain = target;
    } else if (gain < target) {
        gain = std::min(gain + step, target);
    } else if (gain > target) {
        gain = std::max(gain - step, target);
    }
    return gain;
}

//endregion

//region Samples

SamplePlayback::SamplePlayback(Engine &engine, std::shared_ptr<const SampleData> data)
    : Playback(engine, data->sampleRate, data->length), data(std::move(data)) {}

void SamplePlayback::render(float *output, int count, int outputRate) {
    uint32_t serial = seekSerial.load(std::memory_order_acquire);
    if (serial != appliedSeek) {
        appliedSeek = serial;
        cursor = double(seekFrame.load(std::memory_order_relaxed));
    }

    if (status() != Status::Playing) return;

    const int64_t length = data->length;
    if (length == 0) {
        markEnded(serial);
        return;
    }

    const float *frames = data->frames.data();
    const bool loop = looping();
    const double step = double(sourceRate) / outputRate * rate();
    const float gainStep = 1.f / (kGainRampSeconds * float(outputRate));

    auto at = [&](int64_t index, int channel) -> float {
        if (index < 0 || index >= length) {
            if (!loop) return 0.f;
            index = ((index % length) + length) % length;
        }
        return frames[index * 2 + channel];
    };

    for (int i = 0; i < count; ++i) {
        if (cursor >= double(length)) {
            if (!loop) {
                markEnded(serial);
                return;
            }
            cursor = std::fmod(cursor, double(length));
        }

        int64_t index = int64_t(cursor);
        float t = float(cursor - double(index));
        float g = nextGain(gainStep);

        for (int channel = 0; channel < 2; ++channel) {
            output[i * 2 + channel] += g * hermite(at(index - 1, channel), at(index, channel),
                                                   at(index + 1, channel), at(index + 2, channel), t);
        }
        cursor += step;
    }

    if (seekSerial.load(std::memory_order_relaxed) == serial) {
        publishedPosition.store(std::min(cursor, double(length)) / sourceRate, std::memory_order_relaxed);
    }
}

//endregion

//region Streams

struct StreamPlayback::Stretcher {
    signalsmith::stretch::SignalsmithStretch<float> stretch;
    std::vector<float> input[2];
    std::vector<float> output[2];
};

StreamPlayback::StreamPlayback(Engine &engine, std::unique_ptr<Decoder> decoder)
    : Playback(engine, decoder->sampleRate(), decoder->length()),
      decoder(std::move(decoder)),
      frequency(float(sourceRate)),
      chunks(kChunkCount) {
    std::lock_guard<std::mutex> lock(producerLock);
    restart(0);
}

StreamPlayback::~StreamPlayback() = default;

void StreamPlayback::seekTo(int64_t frame) {
    frame = std::clamp<int64_t>(frame, 0, sourceLength);

    std::lock_guard<std::mutex> lock(producerLock);
    seekSeconds.store(double(frame) / sourceRate, std::memory_order_relaxed);
    Playback::seekTo(frame);
    restart(frame);

    // Audio to play right away.
    produceLocked();
}

void StreamPlayback::setEndListener(std::function<void()> listener) {
    std::lock_guard<std::mutex> lock(listenerLock);
    endListener = std::move(listener);
}

void StreamPlayback::restart(int64_t frame) {
    decoder->seek(frame);
    decodePosition = frame;
    decoderEnded = false;
    sourceDone = false;
    finished = false;
    stretched.clear();
    stretchedSource.clear();
    stretchedRead = 0;
    stretchAccumulator = 0;
    stretchSourceNext = double(frame);
    stretchTempo = tempo.load(std::memory_order_relaxed);

    if (stretchTempo != 1.f) {
        if (!stretcher) {
            stretcher = std::make_unique<Stretcher>();
            stretcher->stretch.presetCheaper(2, float(sourceRate));
        }

        // The output of the next blocks starts at `frame`: the stretcher is given what's before its
        // processing point, see outputSeekLength.
        auto &stretch = stretcher->stretch;
        int length = stretch.outputSeekLength(stretchTempo);
        decodeBuffer.assign(size_t(length) * 2, 0.f);
        decode(decodeBuffer.data(), length);

        for (int channel = 0; channel < 2; ++channel) {
            auto &input = stretcher->input[channel];
            input.resize(size_t(length));
            for (int i = 0; i < length; ++i) input[size_t(i)] = decodeBuffer[size_t(i) * 2 + channel];
        }
        float *inputs[2] = {stretcher->input[0].data(), stretcher->input[1].data()};
        stretch.outputSeek(inputs, length);
    }

    // The callback drops the chunks of the previous generation, the new ones start here.
    generationStartWrite = chunkWrite.load(std::memory_order_relaxed);
    generation.fetch_add(1, std::memory_order_release);
}

int64_t StreamPlayback::decode(float *output, int64_t count) {
    int64_t total = 0;
    bool wrapped = false;

    while (total < count) {
        int64_t read = decoder->read(output + total * 2, count - total);
        if (read <= 0) {
            if (looping() && sourceLength > 0 && !wrapped) {
                decoder->seek(0);
                decodePosition = 0;
                wrapped = true;
                continue;
            }
            decoderEnded = true;
            break;
        }
        wrapped = false;
        total += read;
        decodePosition += read;
    }

    // What's past the end is silence, the stretcher is given it to finish.
    std::fill(output + total * 2, output + count * 2, 0.f);
    return total;
}

void StreamPlayback::appendDecoded(int64_t count) {
    decodeBuffer.resize(size_t(count) * 2);
    int64_t total = 0;
    bool wrapped = false;

    while (total < count) {
        int64_t read = decoder->read(decodeBuffer.data() + total * 2, count - total);
        if (read <= 0) {
            if (looping() && sourceLength > 0 && !wrapped) {
                decoder->seek(0);
                decodePosition = 0;
                wrapped = true;
                continue;
            }
            decoderEnded = true;
            sourceDone = true;
            break;
        }
        wrapped = false;
        for (int64_t i = 0; i < read; ++i) stretchedSource.push_back(double(decodePosition + i));
        decodePosition += read;
        total += read;
    }

    stretched.insert(stretched.end(), decodeBuffer.begin(), decodeBuffer.begin() + total * 2);
}

void StreamPlayback::stretchBlock() {
    constexpr int kOutput = 512;
    auto &stretch = stretcher->stretch;

    stretchAccumulator += kOutput * double(stretchTempo);
    int inputCount = int(stretchAccumulator);
    stretchAccumulator -= inputCount;

    decodeBuffer.resize(size_t(inputCount) * 2);
    decode(decodeBuffer.data(), inputCount);

    for (int channel = 0; channel < 2; ++channel) {
        auto &input = stretcher->input[channel];
        input.resize(size_t(inputCount));
        for (int i = 0; i < inputCount; ++i) input[size_t(i)] = decodeBuffer[size_t(i) * 2 + channel];
        stretcher->output[channel].resize(kOutput);
    }

    float *inputs[2] = {stretcher->input[0].data(), stretcher->input[1].data()};
    float *outputs[2] = {stretcher->output[0].data(), stretcher->output[1].data()};
    stretch.process(inputs, inputCount, outputs, kOutput);

    // The output is aligned to the source since restart: its frame k is at stretchSourceNext + k * tempo.
    const bool loop = looping();
    for (int i = 0; i < kOutput; ++i) {
        double source = stretchSourceNext + i * double(stretchTempo);
        if (loop && sourceLength > 0) {
            source = std::fmod(source, double(sourceLength));
        } else if (source >= double(sourceLength)) {
            // Past the end of the source: the stretcher's tail is done.
            sourceDone = true;
            stretchSourceNext = double(sourceLength);
            return;
        }
        stretched.push_back(stretcher->output[0][size_t(i)]);
        stretched.push_back(stretcher->output[1][size_t(i)]);
        stretchedSource.push_back(source);
    }
    stretchSourceNext += kOutput * double(stretchTempo);
}

bool StreamPlayback::fillStretched(size_t frames) {
    while (stretchedSource.size() - stretchedRead < frames && !sourceDone) {
        if (stretchTempo == 1.f) {
            appendDecoded(1024);
        } else {
            stretchBlock();
        }
    }
    return stretchedSource.size() - stretchedRead >= frames;
}

void StreamPlayback::dropWritten() {
    if (stretchedRead < 8192) return;

    stretched.erase(stretched.begin(), stretched.begin() + ptrdiff_t(stretchedRead) * 2);
    stretchedSource.erase(stretchedSource.begin(), stretchedSource.begin() + ptrdiff_t(stretchedRead));
    stretchedRead = 0;
}

void StreamPlayback::produce() {
    std::lock_guard<std::mutex> lock(producerLock);
    produceLocked();
}

void StreamPlayback::produceLocked() {
    // A new tempo restarts from where it's heard.
    if (tempo.load(std::memory_order_relaxed) != stretchTempo) {
        int64_t frame = int64_t(position() * sourceRate);
        seekSeconds.store(double(frame) / sourceRate, std::memory_order_relaxed);
        restart(frame);
    }

    // Enough for the buffer's time at the frequency it plays at.
    const double framesPerSecond = std::max(1.f, frequency.load(std::memory_order_relaxed));
    const uint64_t wanted = std::max<uint64_t>(4, uint64_t(std::ceil(kBufferSeconds * framesPerSecond / kChunkFrames)) + 1);

    while (!finished) {
        uint64_t write = chunkWrite.load(std::memory_order_relaxed);
        uint64_t read = chunkRead.load(std::memory_order_acquire);
        if (write - std::max(read, generationStartWrite) >= wanted || write - read >= kChunkCount) break;

        fillStretched(kChunkFrames);

        Chunk &chunk = chunks[write % kChunkCount];
        int count = 0;
        const size_t available = stretchedSource.size() - stretchedRead;

        while (count < kChunkFrames && size_t(count) < available) {
            size_t index = stretchedRead + size_t(count);
            // A loop starts a new chunk, the positions in a chunk are linear.
            if (count > 0 && stretchedSource[index] < stretchedSource[index - 1]) break;

            chunk.frames[size_t(count) * 2] = stretched[index * 2];
            chunk.frames[size_t(count) * 2 + 1] = stretched[index * 2 + 1];
            ++count;
        }

        if (count == 0 && !sourceDone) break;

        chunk.count = count;
        chunk.sourceStart = count > 0 ? stretchedSource[stretchedRead] : double(sourceLength);
        chunk.sourceStep = count > 1 ? (stretchedSource[stretchedRead + size_t(count) - 1] - chunk.sourceStart) / (count - 1)
                                     : double(stretchTempo);
        chunk.generation = generation.load(std::memory_order_relaxed);
        chunk.last = count == 0;
        finished = chunk.last;
        stretchedRead += size_t(count);
        chunkWrite.store(write + 1, std::memory_order_release);

        dropWritten();
    }
}

bool StreamPlayback::pullFrame(WindowFrame &frame, uint32_t currentGeneration, bool &ended) {
    uint64_t read = chunkRead.load(std::memory_order_relaxed);

    for (;;) {
        if (read == chunkWrite.load(std::memory_order_acquire)) {
            chunkRead.store(read, std::memory_order_release);
            return false;
        }

        const Chunk &chunk = chunks[read % kChunkCount];
        if (chunk.generation != currentGeneration || (chunkOffset >= chunk.count && !chunk.last)) {
            ++read;
            chunkOffset = 0;
            continue;
        }

        if (chunk.last) {
            ended = true;
            chunkRead.store(read, std::memory_order_release);
            return false;
        }

        frame.left = chunk.frames[size_t(chunkOffset) * 2];
        frame.right = chunk.frames[size_t(chunkOffset) * 2 + 1];
        frame.source = chunk.sourceStart + chunkOffset * chunk.sourceStep;
        frame.step = chunk.sourceStep;
        frame.valid = true;

        if (++chunkOffset >= chunk.count) {
            ++read;
            chunkOffset = 0;
        }
        chunkRead.store(read, std::memory_order_release);
        return true;
    }
}

void StreamPlayback::render(float *output, int count, int outputRate) {
    // The chunks are published after the generation they belong to: reading them first, the
    // generation read is at least theirs.
    const uint64_t write = chunkWrite.load(std::memory_order_acquire);
    const uint32_t currentGeneration = generation.load(std::memory_order_acquire);

    if (currentGeneration != consumedGeneration) {
        consumedGeneration = currentGeneration;
        chunkOffset = 0;
        primed = false;
        windowFilled = false;
        lastHeard = seekSeconds.load(std::memory_order_relaxed) * sourceRate;
    }

    // Drops what a seek replaced, even while idle so the producer has room.
    uint64_t read = chunkRead.load(std::memory_order_relaxed);
    while (read < write && chunks[read % kChunkCount].generation != currentGeneration) {
        ++read;
        chunkOffset = 0;
    }
    chunkRead.store(read, std::memory_order_release);

    const Status current = status();
    if (current != Status::Playing && current != Status::Stalled) return;

    const uint32_t serial = seekSerial.load(std::memory_order_acquire);
    const float gainStep = 1.f / (kGainRampSeconds * float(outputRate));
    const double step = double(frequency.load(std::memory_order_relaxed)) / outputRate;

    // No more frames will come from the chunks, and the playback is past its last frame.
    bool drained = false;
    bool ended = false;

    // Fills the window's frames after window[1], in order, false when one isn't decoded yet.
    auto fill = [&]() {
        for (size_t slot = 2; slot < 4; ++slot) {
            if (window[slot].valid || drained) continue;
            if (!pullFrame(window[slot], currentGeneration, drained) && !drained) return false;
        }
        return true;
    };

    // The window holds the frames around the resampler's position, which is between window[1] and
    // window[2]. The first frame after a seek is its own history.
    if (!windowFilled) {
        WindowFrame first;
        if (!pullFrame(first, currentGeneration, drained)) {
            if (drained) markEnded(serial);
            return;
        }
        window = {first, first, WindowFrame{}, WindowFrame{}};
        fraction = 0;
        windowFilled = true;
    }

    uint32_t fftIndex = fftWrite.load(std::memory_order_relaxed);
    int written = 0;
    double renderStart = -1, sourcePerOutputFrame = 0;

    for (; written < count; ++written) {
        if (!fill()) break;

        bool starved = false;
        while (fraction >= 1.0) {
            window[0] = window[1];
            window[1] = window[2];
            window[2] = window[3];
            window[3] = WindowFrame{};
            fraction -= 1.0;
            if (!fill()) {
                starved = true;
                break;
            }
        }
        if (starved) break;

        if (!window[1].valid) {
            ended = true;
            break;
        }

        auto t = float(fraction);
        auto sample = [&](int channel) {
            auto value = [&](const WindowFrame &frame) { return frame.valid ? (channel == 0 ? frame.left : frame.right) : 0.f; };
            return hermite(value(window[0]), value(window[1]), value(window[2]), value(window[3]), t);
        };
        float left = sample(0), right = sample(1);

        if (renderStart < 0) {
            renderStart = window[1].source + fraction * window[1].step;
            sourcePerOutputFrame = window[1].step * step;
        }

        float g = nextGain(gainStep);
        output[written * 2] += left * g;
        output[written * 2 + 1] += right * g;
        fftHistory[fftIndex++ % kFftSize] = 0.5f * (left + right);

        fraction += step;
    }

    fftWrite.store(fftIndex, std::memory_order_release);

    if (ended) {
        markEnded(serial);
        return;
    }

    if (written > 0) {
        primed = true;
        int expected = int(Status::Stalled);
        statusValue.compare_exchange_strong(expected, int(Status::Playing));
    }
    if (written < count && primed) {
        // Ran out of audio, the producer is late.
        int expected = int(Status::Playing);
        statusValue.compare_exchange_strong(expected, int(Status::Stalled));
    }

    // The position is the one being rendered, as BASS's: the output's latency isn't subtracted. Sounds
    // played on a hit take that latency to be heard too, so a clock behind by it (about 300 ms over
    // Bluetooth) put them late against the music; the offset setting accounts for the latency instead.
    // Positions only move forward, but for a loop.
    double speed = 0;
    if (renderStart >= 0) {
        double heard = renderStart;
        if (heard < lastHeard && heard > lastHeard - sourceRate) {
            heard = lastHeard;
        } else if (written == count) {
            speed = sourcePerOutputFrame * outputRate / sourceRate;
        }
        lastHeard = heard;
    }

    uint32_t version = clockVersion.load(std::memory_order_relaxed);
    clockVersion.store(version + 1, std::memory_order_relaxed);
    std::atomic_thread_fence(std::memory_order_release);
    clockPosition.store(lastHeard, std::memory_order_relaxed);
    clockTime.store(Engine::now(), std::memory_order_relaxed);
    clockSpeed.store(speed, std::memory_order_relaxed);
    clockSpan.store(int64_t(count) * 1'000'000'000 / outputRate, std::memory_order_relaxed);
    clockGeneration.store(currentGeneration, std::memory_order_relaxed);
    clockVersion.store(version + 2, std::memory_order_release);
}

double StreamPlayback::position() const {
    if (isAtEnd.load(std::memory_order_relaxed)) return length();

    double frames, speed;
    int64_t time, span;
    uint32_t clockOf;
    for (;;) {
        uint32_t before = clockVersion.load(std::memory_order_acquire);
        if (before & 1u) continue;
        frames = clockPosition.load(std::memory_order_relaxed);
        time = clockTime.load(std::memory_order_relaxed);
        speed = clockSpeed.load(std::memory_order_relaxed);
        span = clockSpan.load(std::memory_order_relaxed);
        clockOf = clockGeneration.load(std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_acquire);
        if (clockVersion.load(std::memory_order_relaxed) == before) break;
    }

    // Not heard since the latest seek.
    const uint32_t current = generation.load(std::memory_order_acquire);
    if (clockOf != current) return seekSeconds.load(std::memory_order_relaxed);

    // Between callbacks the position moves on with the time, up to where the next callback puts it.
    double seconds = frames / sourceRate;
    if (status() == Status::Playing && speed > 0) {
        int64_t elapsed = std::clamp<int64_t>(Engine::now() - time, 0, std::min(span, kMaxExtrapolationNanos));
        seconds += double(elapsed) * 1e-9 * speed;
    }
    seconds = std::clamp(seconds, 0.0, length());

    // The extrapolation may overtake a late callback's position: what was returned isn't taken back,
    // but for a loop.
    if (lastReturnedGeneration.load(std::memory_order_relaxed) == current) {
        double returned = lastReturned.load(std::memory_order_relaxed);
        if (seconds < returned && seconds > returned - 1.0) seconds = returned;
    }
    lastReturned.store(seconds, std::memory_order_relaxed);
    lastReturnedGeneration.store(current, std::memory_order_relaxed);
    return seconds;
}

bool StreamPlayback::readFFT(float *magnitudes) {
    if (status() != Status::Playing) return false;

    uint32_t end = fftWrite.load(std::memory_order_acquire);
    std::vector<std::complex<float>> values(kFftSize);
    for (int i = 0; i < kFftSize; ++i) {
        float window = 0.5f - 0.5f * std::cos(2.f * float(M_PI) * float(i) / float(kFftSize - 1));
        values[size_t(i)] = fftHistory[(end + uint32_t(i)) % kFftSize] * window;
    }

    fft(values);

    // The Hann window halves a sine's amplitude, and the energy of a real signal is split between
    // the positive and the negative frequencies.
    for (int i = 0; i < kFftSize / 2; ++i) magnitudes[i] = std::abs(values[size_t(i)]) * 4.f / kFftSize;
    return true;
}

//endregion

//region Engine

Engine::Engine() {
    mixing.reserve(4096);
    service = std::thread(&Engine::serviceLoop, this);
}

Engine::~Engine() {
    {
        std::lock_guard<std::mutex> lock(serviceLock);
        stopping = true;
    }
    serviceWake.notify_all();
    service.join();

    // The output is stopped: whatever is left is deleted here.
    for (Playback *playback : mixing) delete playback;
    Playback *playback;
    while (entering.pop(playback)) delete playback;
    while (garbage.pop(playback)) delete playback;
    for (Playback *left : pending) delete left;
}

int64_t Engine::now() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

void Engine::render(float *output, int count) {
    std::memset(output, 0, size_t(count) * 2 * sizeof(float));

    Playback *entered;
    while (mixing.size() < mixing.capacity() && entering.pop(entered)) mixing.push_back(entered);

    const int rate = outputRate.load(std::memory_order_relaxed);
    size_t kept = 0;

    for (Playback *playback : mixing) {
        if (playback->released.load(std::memory_order_acquire)) {
            // Handed to the service thread, or kept until there's room for it.
            if (garbage.push(playback)) continue;
            mixing[kept++] = playback;
            continue;
        }
        playback->render(output, count, rate);
        mixing[kept++] = playback;
    }
    mixing.resize(kept);

    for (int i = 0; i < count * 2; ++i) output[i] = std::clamp(output[i], -1.f, 1.f);
}

std::shared_ptr<const SampleData> Engine::decodeSample(const FileData &data) {
    auto decoder = openDecoder(data);
    if (!decoder) return nullptr;

    auto sample = std::make_shared<SampleData>();
    sample->sampleRate = decoder->sampleRate();
    if (decoder->length() > 0) sample->frames.reserve(size_t(decoder->length()) * 2);

    constexpr int64_t kBlock = 4096;
    for (;;) {
        size_t start = sample->frames.size();
        sample->frames.resize(start + kBlock * 2);
        int64_t read = decoder->read(sample->frames.data() + start, kBlock);
        sample->frames.resize(start + size_t(std::max<int64_t>(read, 0)) * 2);
        if (read <= 0) break;
    }

    sample->length = int64_t(sample->frames.size() / 2);
    return sample;
}

SamplePlayback *Engine::createSamplePlayback(std::shared_ptr<const SampleData> data) {
    if (!data || data->sampleRate <= 0) return nullptr;
    auto *playback = new SamplePlayback(*this, std::move(data));
    enter(playback);
    return playback;
}

StreamPlayback *Engine::createStream(const FileData &data) {
    auto decoder = openDecoder(data);
    if (!decoder || decoder->sampleRate() <= 0) return nullptr;

    auto *stream = new StreamPlayback(*this, std::move(decoder));
    {
        std::lock_guard<std::mutex> lock(streamsLock);
        streams.push_back(stream);
    }
    stream->produce();
    enter(stream);
    return stream;
}

void Engine::release(Playback *playback) {
    if (!playback) return;

    bool isStream = false;
    {
        std::lock_guard<std::mutex> lock(streamsLock);
        auto found = std::find(streams.begin(), streams.end(), playback);
        if (found != streams.end()) {
            streams.erase(found);
            isStream = true;
        }
    }
    if (isStream) {
        // Waits for a producer that may still be decoding it.
        std::lock_guard<std::mutex> producing(static_cast<StreamPlayback *>(playback)->producerLock);
    }

    playback->statusValue.store(int(Status::Stopped), std::memory_order_relaxed);
    playback->released.store(true, std::memory_order_release);
}

void Engine::enter(Playback *playback) {
    std::lock_guard<std::mutex> lock(enteringLock);
    while (!pending.empty() && entering.push(pending.front())) pending.erase(pending.begin());
    if (!pending.empty() || !entering.push(playback)) pending.push_back(playback);
}

void Engine::flushPending() {
    std::lock_guard<std::mutex> lock(enteringLock);
    while (!pending.empty() && entering.push(pending.front())) pending.erase(pending.begin());
}

void Engine::serviceLoop() {
    std::unique_lock<std::mutex> lock(serviceLock);

    while (!stopping) {
        lock.unlock();

        flushPending();

        std::vector<std::function<void()>> ended;
        {
            std::lock_guard<std::mutex> streamsGuard(streamsLock);
            for (StreamPlayback *stream : streams) {
                stream->produce();

                if (stream->endedPending.exchange(false, std::memory_order_acq_rel)) {
                    std::lock_guard<std::mutex> listenerGuard(stream->listenerLock);
                    if (stream->endListener) ended.push_back(stream->endListener);
                }
            }
        }
        // Outside of the lock: listeners may create or release streams.
        for (auto &listener : ended) listener();

        Playback *released;
        while (garbage.pop(released)) delete released;

        lock.lock();
        serviceWake.wait_for(lock, std::chrono::milliseconds(5), [this] { return stopping; });
    }
}

//endregion

}
