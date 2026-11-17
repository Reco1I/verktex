#include "audio_decoder.h"

#include <algorithm>
#include <cstring>

// The decoders are single-header libraries, implemented in this unit alone. See third_party/README.md.
#define MINIMP3_IMPLEMENTATION
#define MINIMP3_FLOAT_OUTPUT
#include "minimp3_ex.h"

#define DR_WAV_IMPLEMENTATION
#include "dr_wav.h"

#define DR_FLAC_IMPLEMENTATION
#include "dr_flac.h"

#define STB_VORBIS_NO_STDIO
#define STB_VORBIS_NO_PUSHDATA_API
#include "stb_vorbis.c"

namespace verktex::audio {

void Decoder::toStereo(const float *input, float *output, int64_t count) const {
    if (channels == 2) {
        std::memcpy(output, input, size_t(count) * 2 * sizeof(float));
        return;
    }
    for (int64_t i = 0; i < count; ++i) {
        const float *frame = input + i * channels;
        output[i * 2] = frame[0];
        output[i * 2 + 1] = channels == 1 ? frame[0] : frame[1];
    }
}

namespace {

// Reads through a buffer of the file's own channels, which is converted to stereo.
class ConvertingDecoder : public Decoder {
public:
    int64_t read(float *output, int64_t count) override {
        if (channels == 2) return readNative(output, count);

        int64_t total = 0;
        while (total < count) {
            int64_t chunk = std::min<int64_t>(count - total, kChunk);
            buffer.resize(size_t(chunk) * channels);
            int64_t read = readNative(buffer.data(), chunk);
            if (read <= 0) break;
            toStereo(buffer.data(), output + total * 2, read);
            total += read;
        }
        return total;
    }

protected:
    // Reads `count` frames of the file's channels.
    virtual int64_t readNative(float *output, int64_t count) = 0;

private:
    static constexpr int64_t kChunk = 1024;
    std::vector<float> buffer;
};

class Mp3Decoder : public ConvertingDecoder {
public:
    explicit Mp3Decoder(FileData data) : data(std::move(data)) {}

    ~Mp3Decoder() override {
        if (opened) mp3dec_ex_close(&decoder);
    }

    bool open() {
        // Scans the whole file, as BASS_STREAM_PRESCAN does: exact lengths and seeks. The LAME/Xing tag's
        // encoder delay and padding are removed.
        if (mp3dec_ex_open_buf(&decoder, data->data(), data->size(), MP3D_SEEK_TO_SAMPLE) != 0) return false;
        opened = true;
        if (decoder.info.channels <= 0 || decoder.info.hz <= 0 || decoder.samples == 0) return false;

        channels = decoder.info.channels;
        rate = decoder.info.hz;
        frames = int64_t(decoder.samples / uint64_t(channels));
        return true;
    }

    bool seek(int64_t frame) override {
        return mp3dec_ex_seek(&decoder, uint64_t(frame) * uint64_t(channels)) == 0;
    }

protected:
    int64_t readNative(float *output, int64_t count) override {
        size_t samples = mp3dec_ex_read(&decoder, output, size_t(count) * size_t(channels));
        return int64_t(samples / size_t(channels));
    }

private:
    FileData data;
    mp3dec_ex_t decoder{};
    bool opened = false;
};

class VorbisDecoder : public ConvertingDecoder {
public:
    explicit VorbisDecoder(FileData data) : data(std::move(data)) {}

    ~VorbisDecoder() override {
        if (vorbis) stb_vorbis_close(vorbis);
    }

    bool open() {
        int error = 0;
        vorbis = stb_vorbis_open_memory(data->data(), int(data->size()), &error, nullptr);
        if (!vorbis) return false;

        stb_vorbis_info info = stb_vorbis_get_info(vorbis);
        channels = info.channels;
        rate = int(info.sample_rate);
        frames = stb_vorbis_stream_length_in_samples(vorbis);
        return channels > 0 && rate > 0;
    }

    bool seek(int64_t frame) override {
        return stb_vorbis_seek(vorbis, unsigned(frame)) != 0;
    }

protected:
    int64_t readNative(float *output, int64_t count) override {
        int64_t total = 0;
        while (total < count) {
            int64_t want = std::min<int64_t>(count - total, 4096);
            int read = stb_vorbis_get_samples_float_interleaved(vorbis, channels, output + total * channels,
                                                                int(want * channels));
            if (read <= 0) break;
            total += read;
        }
        return total;
    }

private:
    FileData data;
    stb_vorbis *vorbis = nullptr;
};

class WavDecoder : public ConvertingDecoder {
public:
    explicit WavDecoder(FileData data) : data(std::move(data)) {}

    ~WavDecoder() override {
        if (opened) drwav_uninit(&wav);
    }

    bool open() {
        if (!drwav_init_memory(&wav, data->data(), data->size(), nullptr)) return false;
        opened = true;
        channels = wav.channels;
        rate = int(wav.sampleRate);
        frames = int64_t(wav.totalPCMFrameCount);
        return channels > 0 && rate > 0;
    }

    bool seek(int64_t frame) override {
        return drwav_seek_to_pcm_frame(&wav, drwav_uint64(frame)) != 0;
    }

protected:
    int64_t readNative(float *output, int64_t count) override {
        return int64_t(drwav_read_pcm_frames_f32(&wav, drwav_uint64(count), output));
    }

private:
    FileData data;
    drwav wav{};
    bool opened = false;
};

class FlacDecoder : public ConvertingDecoder {
public:
    explicit FlacDecoder(FileData data) : data(std::move(data)) {}

    ~FlacDecoder() override {
        if (flac) drflac_close(flac);
    }

    bool open() {
        flac = drflac_open_memory(data->data(), data->size(), nullptr);
        if (!flac) return false;
        channels = flac->channels;
        rate = int(flac->sampleRate);
        frames = int64_t(flac->totalPCMFrameCount);
        return channels > 0 && rate > 0;
    }

    bool seek(int64_t frame) override {
        return drflac_seek_to_pcm_frame(flac, drflac_uint64(frame)) != 0;
    }

protected:
    int64_t readNative(float *output, int64_t count) override {
        return int64_t(drflac_read_pcm_frames_f32(flac, drflac_uint64(count), output));
    }

private:
    FileData data;
    drflac *flac = nullptr;
};

bool startsWith(const FileData &data, const char *magic) {
    size_t length = std::strlen(magic);
    return data->size() >= length && std::memcmp(data->data(), magic, length) == 0;
}

template <typename T>
std::unique_ptr<Decoder> tryOpen(const FileData &data) {
    auto decoder = std::make_unique<T>(data);
    if (!decoder->open()) return nullptr;
    return decoder;
}

}

std::unique_ptr<Decoder> openDecoder(const FileData &data) {
    if (!data || data->empty()) return nullptr;

    if (startsWith(data, "RIFF") || startsWith(data, "RIFX") || startsWith(data, "RF64")) return tryOpen<WavDecoder>(data);
    if (startsWith(data, "fLaC")) return tryOpen<FlacDecoder>(data);
    if (startsWith(data, "OggS")) return tryOpen<VorbisDecoder>(data);

    // MP3s start with an ID3 tag or a frame, and some skins name WAVs .mp3 and the reverse: anything
    // else is tried as MP3.
    return tryOpen<Mp3Decoder>(data);
}

}
