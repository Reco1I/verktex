# Audio backends

Verktex plays audio through an `AudioDriver`. Two exist: **BASS**, on every platform, and **Oboe**,
on Android only, experimental. The backend is the engine setting `VerktexSettings.AudioBackend`
(the name of an `AudioBackend`), read once when the engine starts, so a change applies on the next
start. Where Oboe fails to start, the engine falls back to BASS.

## Why Oboe

BASS is closed source, has its own buffering on top of Android's, and its positions don't account
for the latency of the output. Oboe (https://github.com/google/oboe) is Google's C++ library over
AAudio (Android 8.1 and up) and OpenSL ES (before), made for low latency: exclusive low latency
streams, a callback on a real time thread, and timestamps of the frames actually presented, which
let the position of a stream be the one that is being heard.

Oboe only outputs audio: decoding, mixing, resampling and changing the speed are Verktex's, in a
native audio core of its own, `natives/audio/`.

## Architecture

```
commonMain     AudioDriver ─ AudioDevice, AudioSample(.Source) → AudioPlayback, AudioStream(.Source)
                  │ the driver opens the files: createSampleSource, createStreamSource, createUrlStreamSource
androidMain    BASSAudioDriver (android/bass/)        OboeAudioDriver (android/oboe/) ─ OboeNative (JNI)
desktopMain    BASSAudioDriver (desktop/bass/)                         │
natives/audio  ───────────────────────────────────────  Engine ─ Mixer ─ Voices (samples, streams)
                                                          │        Decoders, Stretch, Resampler, FFT
android-natives                                        OboeOutput (AudioOutput over oboe::AudioStream)
```

- `AudioDriver` creates the sources of samples and streams, `Provider` doesn't: switching drivers
  switches every resource the engine and the application load (`Sounds`, and sources created with
  `Verktex.audio`).
- `AudioBackend` lists the backends, `Platform.availableAudioBackends` those the platform has
  (`[BASS, Oboe]` on Android, `[BASS]` on desktop, so applications don't offer a choice there).
- `createAudioDriver()` (in each platform's source set) reads the setting and falls back to BASS.

### The native core (`natives/audio/`)

Plain C++17 independent of Oboe and of Android: the output is an `AudioOutput` interface, so the core
builds and is tested on Linux with a null output (`natives/audio/tests/`).

- **Decoders**, single-header libraries chosen for exact timing: `minimp3` (with `minimp3_ex`, which
  reads the LAME/Xing tag and removes the encoder delay and padding, as BASS does), `stb_vorbis`,
  `dr_wav` and `dr_flac`. They seek to the exact frame. The NDK's `AMediaExtractor` isn't used: its
  MP3 seeking is imprecise and its encoder delay differs between devices, an offset error in a
  rhythm game. Assets are read through the `AAssetManager` into memory.
- **Mixer**: stereo float at the device's native rate. The application's threads never touch the
  voices: they post commands (play, pause, stop, seek, volume, rate, loop) to a lock-free queue the
  output's callback drains at the start of each callback, and read back the state (playing, paused,
  stalled, position) that the callback publishes in atomics. The callback doesn't lock, allocate or
  call into the JVM. Volume and rate changes are ramped to avoid clicks. Voices come from a fixed
  pool; when it's exhausted the oldest voice of the same sample is reused.
- **Samples** are decoded whole at load, at their own rate. A playback is a voice with a cubic
  resampler, whose step is `sampleRate / outputRate * pitch`. As with BASS, samples ignore `speed`
  and `adjustPitch`.
- **Streams** are decoded by a worker thread: decoder → time stretch (when `speed != 1` and the pitch
  is kept) → into a ring buffer of 100 ms, which the callback resamples to `frequency / outputRate`.
  A seek flushes the buffers and seeks the decoder to the exact frame. `onEnd` is called by a
  notifier thread, never the callback.
- **Position of a stream**: the source frame of the audio being heard, i.e. the frames decoded
  minus those still buffered (in the stretcher, scaled by the speed, and in the ring buffer, scaled
  by the rate) minus the latency of the output (from Oboe's timestamps). Unlike BASS's, it includes
  the output latency, so the offset calibrated with BASS is off by about that latency.
- **Speed without changing the pitch**: Signalsmith Stretch (MIT, header-only,
  https://github.com/Signalsmith-Audio/signalsmith-stretch).
- **Spectrum**: the callback keeps the last 1024 output frames; `readFFT()` applies a Hann window
  and a 1024 point FFT and returns 512 magnitudes, scaled as `BASS_DATA_FFT1024`.
- **URL streams** aren't supported by Oboe yet (`createUrlStreamSource` returns null).

### The Oboe output (`android-natives`)

`OboeOutput` opens an `oboe::AudioStream`: low latency performance mode, exclusive sharing (Oboe
falls back to shared), `Usage::Game`, float, stereo, the device's native rate, a buffer of two
bursts. Its timestamps give the latency. On `ErrorDisconnected` (headphones unplugged, Bluetooth) it
reopens the stream from another thread, the voices keep their positions. The host pauses the output
while the activity is stopped.

Oboe is built from source (pinned, see `gradle/libs.versions.toml`) with `add_subdirectory`, so it
links with the static STL the Vulkan library already uses: its Prefab AAR is built against
`c++_shared`.

## The setting in applications

osu!droid offers the choice in its audio settings (an "Output" category) when
`Platform.availableAudioBackends` has more than one entry, applied on the next start, with a toast
that recommends calibrating the offset again for Oboe.

## Plan and status

1. Drivers open their files, `AudioBackend`, `VerktexSettings.AudioBackend`,
   `Platform.availableAudioBackends`, `createAudioDriver()`. BASS stays the default.
2. The native core with its tests on Linux: decoders, mixer, samples, streams, positions.
3. Time stretch and spectrum.
4. The Oboe output and `android-natives`' build.
5. The Kotlin driver (`android/oboe/`), the fallback, the lifecycle.
6. An audio UI test (`Verktex/Audio`), the backend and latency in the performance overlay.
7. The game's setting.

Left for later: choosing the output device (`AudioManager.getDevices`, Oboe's `setDeviceId`), URL
streams, switching the backend without restarting, an `Auto` backend once Oboe is proven, the
native core on desktop.

## Testing

- `natives/audio/tests/`: decoding lengths (MP3 with and without gapless tags), exact seeks,
  monotonic positions with `speed != 1` and across seeks, `onEnd` once, looping, the spectrum.
- `Verktex/Audio` UI test: samples and streams, speed, pitch and frequency, rapid hits, spectrum,
  and the backend and latency.
- By hand: the same beatmap with each backend against the calibration scene; low end devices
  (OpenSL ES, Android 7 to 8), Bluetooth, plugging and unplugging headphones.
