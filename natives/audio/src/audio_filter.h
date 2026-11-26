#pragma once

#include <cmath>

namespace verktex::audio {

// A stereo biquad low-pass or high-pass filter (the RBJ cookbook's), processed a frame at a time. Its
// coefficients are computed again only when the cutoff or the rate changes, so the cutoff can move
// every callback.
class BiquadFilter {
public:
    enum class Type { LowPass, HighPass };

    explicit BiquadFilter(Type type) : type(type) {}

    // Sets the cutoff in Hz at the output rate `rate`, `enabled` false bypasses the filter. Its state
    // is cleared when it's enabled again, so a stale one doesn't click.
    void configure(float cutoff, int rate, bool enabled, float q = 0.70710677f) {
        if (!enabled) {
            active = false;
            return;
        }
        if (!active) {
            x1 = {}; x2 = {}; y1 = {}; y2 = {};
            active = true;
        }
        if (cutoff == appliedCutoff && rate == appliedRate) return;
        appliedCutoff = cutoff;
        appliedRate = rate;

        // Below the Nyquist frequency.
        double frequency = std::fmin(std::fmax(double(cutoff), 1.0), rate * 0.49);
        double w = 2.0 * M_PI * frequency / rate;
        double cosW = std::cos(w);
        double alpha = std::sin(w) / (2.0 * q);
        double a0 = 1.0 + alpha;

        if (type == Type::LowPass) {
            b0 = (1.0 - cosW) / 2.0 / a0;
            b1 = (1.0 - cosW) / a0;
        } else {
            b0 = (1.0 + cosW) / 2.0 / a0;
            b1 = -(1.0 + cosW) / a0;
        }
        b2 = b0;
        a1 = -2.0 * cosW / a0;
        a2 = (1.0 - alpha) / a0;
    }

    bool isActive() const { return active; }

    // Filters `channel` (0 or 1) of the next frame.
    float process(int channel, float input) {
        if (!active) return input;

        double output = b0 * input + b1 * x1[channel] + b2 * x2[channel] - a1 * y1[channel] - a2 * y2[channel];
        x2[channel] = x1[channel];
        x1[channel] = input;
        y2[channel] = y1[channel];
        y1[channel] = output;
        return float(output);
    }

private:
    struct Pair {
        double values[2]{};
        double &operator[](int index) { return values[index]; }
    };

    Type type;
    bool active = false;
    float appliedCutoff = -1.f;
    int appliedRate = 0;
    double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    Pair x1, x2, y1, y2;
};

} // namespace verktex::audio
