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
  `dr_wav` and `dr_flac` (`third_party/README.md`). They seek to the exact frame. The NDK's
  `AMediaExtractor` isn't used: its MP3 seeking is imprecise and its encoder delay differs between
  devices, an offset error in a rhythm game. The driver reads files (and assets) whole in Kotlin and
  gives their bytes to the core, which decodes them from memory.
- **Mixer**: stereo float at the device's native rate. The application's threads never touch the
  voices: they post commands (play, pause, stop, seek, volume, rate, loop) to a lock-free queue the
  output's callback drains at the start of each callback, and read back the state (playing, paused,
  stalled, position) that the callback publishes in atomics. The callback doesn't lock, allocate or
  call into the JVM. Volume and rate changes are ramped to avoid clicks. Voices come from a fixed
  pool; when it's exhausted the oldest voice of the same sample is reused.
- **Samples** are decoded whole at load, at their own rate. A playback is a voice with a cubic
  resampler, whose step is `sampleRate / outputRate * pitch`. As with BASS, samples ignore `speed`
  and `adjustPitch`.
- **Streams** are decoded ahead by the engine's service thread: decoder → time stretch (when the
  speed isn't 1 and the pitch is kept) → chunks of about 100 ms in all, at the source's rate, each
  frame labeled with its source frame. The callback resamples them to `frequency / outputRate`, so a
  change of frequency is heard right away. A seek decodes from the exact frame synchronously and
  starts a new generation of chunks, the callback drops the older ones. A change of tempo restarts
  from the position heard, with a short glitch. `onEnd` is called by the service thread.
- **Position of a stream**: the source frame of the audio being heard: the one the callback renders
  minus the latency of the output (from Oboe's timestamps) at the current rate, extrapolated with
  the time between callbacks, and never going back but for a loop. After a seek it stays at the
  target until the audio after it is heard. Unlike BASS's, it includes the output latency, so the
  offset calibrated with BASS is off by about that latency.
- **Speed without changing the pitch**: Signalsmith Stretch (MIT, header-only,
  https://github.com/Signalsmith-Audio/signalsmith-stretch), with its cheaper preset. It's meant for
  0.75x to 1.5x, the speeds of HT and DT, and works beyond. BASS_FX uses a SoundTouch-like WSOLA:
  how they compare on the same song is to be checked by ear.
- **Spectrum**: the callback keeps the last 1024 frames of the stream; `readFFT()` applies a Hann
  window and a 1024 point FFT and returns 512 magnitudes, a full scale sine reading about 1. That
  BASS's `BASS_DATA_FFT1024` reads the same is to be checked against it.
- **URL streams** aren't supported by Oboe yet (`createUrlStreamSource` returns null).

### The Oboe output (`android-natives`)

`OboeOutput` opens an `oboe::AudioStream`: low latency performance mode, exclusive sharing (Oboe
falls back to shared), `Usage::Game`, float, stereo, the device's native rate, a buffer of two
bursts. Its timestamps give the latency. When the stream is disconnected (headphones unplugged,
Bluetooth) it's reopened on the default device, the playbacks keep their positions. The host closes
the output while its activity is stopped, unless a stream plays (BASS keeps playing in the
background, so music may too).

Oboe is built from source (pinned, see `gradle/libs.versions.toml`) with `add_subdirectory`, so it
links with the static STL the Vulkan library already uses: its Prefab AAR is built against
`c++_shared`.

## The setting in applications

osu!droid offers the choice in its audio settings (an "Output" category) when
`Platform.availableAudioBackends` has more than one entry, applied on the next start, with a toast
that recommends calibrating the offset again for Oboe.

## Status

Done: drivers open their files, the setting and the fallback; the native core with its tests; the
Oboe output and `android-natives`' build; the Kotlin driver (`android/oboe/`); the `Verktex/Audio`
UI test and the backend in the performance overlay; the game's setting (osu!droid).

The native core is built and tested on Linux. The Android build (Oboe, the JNI functions, the Kotlin
driver) was written without the Android SDK at hand and is yet to be built and tried on devices.

Left for later: choosing the output device (`AudioManager.getDevices`, Oboe's `setDeviceId`), URL
streams, switching the backend without restarting, an `Auto` backend once Oboe is proven, the
native core on desktop.

## Testing

- `natives/audio/tests/` (`cmake -S natives/audio -B build/audio -DVERKTEX_AUDIO_TESTS=ON`, then
  `cmake --build build/audio && ctest --test-dir build/audio`): decoded lengths (MP3s with their
  LAME tag), exact seeks, samples' rates, loops and volume ramps, streams' positions with the
  output's latency, tempo and frequency, ends, loops, stalls, and released playbacks. They pass with
  the address, undefined behavior and thread sanitizers.
- `Verktex/Audio` UI test: samples and streams, speed, pitch and frequency, rapid hits, spectrum,
  and the backend and latency.
- By hand: the same beatmap with each backend against the calibration scene; low end devices
  (OpenSL ES, Android 7 to 8), Bluetooth, plugging and unplugging headphones.
