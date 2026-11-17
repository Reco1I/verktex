#pragma once

#include <cstdint>
#include <memory>
#include <vector>

namespace verktex::audio {

// The bytes of a whole audio file, shared by the decoders reading it.
using FileData = std::shared_ptr<const std::vector<uint8_t>>;

// Decodes a file to interleaved stereo float frames, at the file's own sample rate. Mono files are
// duplicated to both channels, files with more channels keep their first two (front left and right).
class Decoder {
public:
    virtual ~Decoder() = default;

    int sampleRate() const { return rate; }

    // The length in frames, the encoder delay and padding of MP3s removed.
    int64_t length() const { return frames; }

    // Reads up to `count` frames into `output` (2 floats per frame), returns how many were read, 0 at
    // the end of the file.
    virtual int64_t read(float *output, int64_t count) = 0;

    // Moves to the exact frame `frame`. False if the decoder couldn't.
    virtual bool seek(int64_t frame) = 0;

protected:
    int rate = 0;
    int channels = 0;
    int64_t frames = 0;

    // Converts `count` frames of `channels` interleaved channels in `input` to stereo in `output`.
    void toStereo(const float *input, float *output, int64_t count) const;
};

// Opens a decoder for MP3, Ogg Vorbis, WAV or FLAC data, detected from its first bytes. Null if the
// data isn't one of them or is invalid.
std::unique_ptr<Decoder> openDecoder(const FileData &data);

}
