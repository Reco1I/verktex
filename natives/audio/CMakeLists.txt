# The native audio core of Verktex, see docs/audio-backends.md. Platform independent: Android links it
# into the library of the Oboe backend (android-natives), and on its own it builds with its tests:
#
#   cmake -S natives/audio -B build/audio -DVERKTEX_AUDIO_TESTS=ON && cmake --build build/audio && ctest --test-dir build/audio
cmake_minimum_required(VERSION 3.18.1)

project(verktex_audio CXX)

set(THIRD_PARTY ${CMAKE_CURRENT_SOURCE_DIR}/third_party)

add_library(verktex_audio_core STATIC
        src/audio_decoder.cpp
        src/audio_engine.cpp)

target_include_directories(verktex_audio_core
        PUBLIC src
        PRIVATE
        ${THIRD_PARTY}/minimp3
        ${THIRD_PARTY}/dr_libs
        ${THIRD_PARTY}/stb_vorbis
        ${THIRD_PARTY}/signalsmith-stretch
        ${THIRD_PARTY}/signalsmith-linear/include)

target_compile_features(verktex_audio_core PUBLIC cxx_std_17)
set_target_properties(verktex_audio_core PROPERTIES POSITION_INDEPENDENT_CODE ON)

find_package(Threads REQUIRED)
target_link_libraries(verktex_audio_core PUBLIC Threads::Threads)

# The decoders are third party code, compiled in audio_decoder.cpp: only the engine gets the warnings.
set_source_files_properties(src/audio_engine.cpp PROPERTIES
        COMPILE_OPTIONS "-Wall;-Wextra;-Wno-unused-parameter")

option(VERKTEX_AUDIO_TESTS "Build the tests of the audio core" OFF)

if (VERKTEX_AUDIO_TESTS)
    enable_testing()
    add_executable(verktex_audio_tests tests/audio_tests.cpp)
    target_link_libraries(verktex_audio_tests PRIVATE verktex_audio_core)
    target_compile_definitions(verktex_audio_tests PRIVATE
            VERKTEX_AUDIO_FIXTURES="${CMAKE_CURRENT_SOURCE_DIR}/tests/fixtures")
    add_test(NAME verktex_audio_tests COMMAND verktex_audio_tests)
endif ()
