#pragma once

#include <cmath>
#include <complex>
#include <vector>

namespace verktex::audio {

// An in-place radix-2 FFT of `values`, whose size is a power of 2.
inline void fft(std::vector<std::complex<float>> &values) {
    const size_t n = values.size();

    for (size_t i = 1, j = 0; i < n; ++i) {
        size_t bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) std::swap(values[i], values[j]);
    }

    for (size_t length = 2; length <= n; length <<= 1) {
        const float angle = -2.f * float(M_PI) / float(length);
        const std::complex<float> unit(std::cos(angle), std::sin(angle));

        for (size_t start = 0; start < n; start += length) {
            std::complex<float> w(1.f, 0.f);
            for (size_t k = 0; k < length / 2; ++k) {
                std::complex<float> even = values[start + k];
                std::complex<float> odd = values[start + k + length / 2] * w;
                values[start + k] = even + odd;
                values[start + k + length / 2] = even - odd;
                w *= unit;
            }
        }
    }
}

}
