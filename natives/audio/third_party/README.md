# Third party code of the audio core

Copied as they are from their repositories, with their licenses, at these commits. They're compiled
in `src/audio_decoder.cpp` (the decoders) and `src/audio_engine.cpp` (the stretcher).

| Folder | What | License | Source |
|---|---|---|---|
| `minimp3/` | `minimp3.h`, `minimp3_ex.h`: MP3, with the LAME/Xing tag's delay and padding | CC0 | https://github.com/lieff/minimp3 at `9f1f7c67d596e94ca1b8be70b58fddc94820bf15` |
| `dr_libs/` | `dr_wav.h`, `dr_flac.h` | Public domain or MIT-0 | https://github.com/mackron/dr_libs at `c2d31fed078120f67263fd36237c0dcaae6bc07b` |
| `stb_vorbis/` | `stb_vorbis.c`: Ogg Vorbis | Public domain or MIT | https://github.com/nothings/stb at `9600522de53973336fa033a289858a4354cbb04c` |
| `signalsmith-stretch/` | Time stretching, 1.4.0 | MIT | https://github.com/Signalsmith-Audio/signalsmith-stretch at `a670068d9aeb64913331d5cc29337b19a457a7df` |
| `signalsmith-linear/` | Its FFT and STFT, 0.6.4 (without its tests) | MIT | https://github.com/Signalsmith-Audio/linear at `de55e6a50ffcf6f8f43f649692d94691c7025151` |

To update one, replace its files with those of a newer commit, update the table, and run the tests
(`natives/audio/CMakeLists.txt`).
