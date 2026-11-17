package com.reco1l.verktex.testing

import com.reco1l.verktex.Platform
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.audio.AudioPlayback
import com.reco1l.verktex.audio.Sounds
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.math.toFixed
import com.reco1l.verktex.ui.container.UILinearContainer.Orientation
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.ui.control.UICheckbox
import com.reco1l.verktex.ui.control.slider
import com.reco1l.verktex.ui.shape.UIBox
import com.reco1l.verktex.ui.shape.box
import com.reco1l.verktex.ui.text.text
import com.reco1l.verktex.ui.textButton
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import okio.Buffer

private const val RATE = 44100

/**
 * Builds the "Audio" test: a sample and a stream of the audio backend in use, generated as WAV files
 * in the cache folder, with their speed, pitch and position, the spectrum and the output's latency.
 * Compare backends with the Audio backend setting (or a host's own way to pick one).
 */
internal fun UITestStage.audioTest() {
    val directory = Platform.storage.cacheDirectory / "audio-test"
    Platform.fileSystem.createDirectories(directory)

    // A short blip, and a metronome: a click every half second, the first of every four higher, over a
    // quiet tone, to hear the timing and the pitch.
    val samplePath = directory / "blip.wav"
    val streamPath = directory / "metronome.wav"
    Platform.fileSystem.write(samplePath) { write(wav(RATE / 10) { i -> envelope(i, RATE / 10) * sine(i, 880.0) * 0.5f }) }
    Platform.fileSystem.write(streamPath) {
        write(wav(RATE * 16) { i ->
            val beat = i / (RATE / 2)
            val inBeat = i % (RATE / 2)
            val click = if (inBeat < RATE / 20) envelope(inBeat, RATE / 20) * sine(i, if (beat % 4 == 0) 1760.0 else 880.0) * 0.6f else 0f
            click + sine(i, 220.0) * 0.1f
        })
    }

    val sample = Sounds.loadSample("audio-test-blip", samplePath.toString())
    val stream = Sounds.loadStream("audio-test-metronome", streamPath.toString())
    onDispose {
        Sounds.unload("audio-test-blip")
        Sounds.unload("audio-test-metronome")
    }

    var rapidHitsLeft = 0
    var rapidHitTimer = 0f

    column {
        section("Backend") {
            orientation = Orientation.Vertical
            val info = text { }
            onEachFrame {
                val driver = Verktex.audio
                info.text = "${driver.backend.displayName}, output latency ${(driver.outputLatency * 1000).toFixed(1)} ms"
            }
        }

        section("Sample") {
            textButton { text = "Play"; onActionUp = { sample.play(restart = true) } }
            textButton {
                text = "10 rapid hits"
                onActionUp = { rapidHitsLeft = 10; rapidHitTimer = 0f }
            }
            text { text = "Pitch" }
            slider {
                width = 200f.dip
                min = 0.5f
                max = 2f
                value = 1f
                onValueChange = { sample.pitch = it }
            }
        }

        section("Stream") {
            textButton { text = "Play"; onActionUp = { stream.play() } }
            textButton { text = "Pause"; onActionUp = { stream.pause() } }
            textButton { text = "Stop"; onActionUp = { stream.stop() } }
            textButton { text = "Seek to 4 s"; onActionUp = { stream.position = 4.0 } }
            +UICheckbox().apply { onValueChange = { stream.looping = it; log("Looping: $it") } }
            text { text = "Loop" }
        }

        section("Speed and pitch") {
            orientation = Orientation.Vertical
            val speedText = text { text = "Speed 1.00x" }
            slider {
                width = 300f.dip
                min = 0.5f
                max = 2f
                value = 1f
                onValueChange = {
                    stream.speed = it
                    speedText.text = "Speed ${it.toFixed(2)}x"
                }
            }
            linearContainer {
                spacing = 8f.dip
                +UICheckbox().apply { onValueChange = { stream.adjustPitch = it } }
                text { text = "Speed changes the pitch" }
            }
            val pitchText = text { text = "Pitch 1.00x" }
            slider {
                width = 300f.dip
                min = 0.5f
                max = 2f
                value = 1f
                onValueChange = {
                    stream.pitch = it
                    pitchText.text = "Pitch ${it.toFixed(2)}x"
                }
            }
        }

        section("Position and spectrum") {
            orientation = Orientation.Vertical
            val positionText = text { }
            val bars = mutableListOf<UIBox>()
            linearContainer {
                spacing = 2f.dip
                height = 100f.dip
                repeat(48) {
                    bars += box {
                        width = 6f.dip
                        height = 1f.dip
                        color = Color4.Blue400
                    }
                }
            }

            onEachFrame { clock ->
                positionText.text = "${stream.position.toFixed(3)} / ${stream.length.toFixed(3)} s, " +
                    "${statusOf(stream)}, ${stream.frequency.toFixed(0)} Hz"

                val spectrum = stream.readFFT()
                for ((index, bar) in bars.withIndex()) {
                    // The lower bands, where the clicks and the tone are.
                    val magnitude = spectrum?.let { it[min(it.size - 1, index * 2 + 1)] } ?: 0f
                    bar.height = (1f + min(1f, magnitude * 4f) * 99f).dip
                }

                if (rapidHitsLeft > 0) {
                    rapidHitTimer -= clock.elapsedFrameTime
                    if (rapidHitTimer <= 0f) {
                        sample.play(restart = true)
                        rapidHitsLeft--
                        rapidHitTimer = 0.06f
                    }
                }
            }
        }
    }
}

private fun statusOf(playback: AudioPlayback) = when {
    playback.isPlaying -> "playing"
    playback.isStalled -> "stalled"
    playback.isPaused -> "paused"
    else -> "stopped"
}

private fun sine(frame: Int, hz: Double) = sin(2.0 * PI * hz * frame / RATE).toFloat()

// Fades in and out over a few milliseconds, so the blips don't click.
private fun envelope(frame: Int, length: Int): Float {
    val fade = RATE / 200
    return min(1f, min(frame, length - frame).toFloat() / fade)
}

/**
 * A WAV of 32 bit float mono frames.
 */
private fun wav(frames: Int, frame: (Int) -> Float): ByteArray {
    val buffer = Buffer()
    buffer.writeUtf8("RIFF").writeIntLe(36 + frames * 4).writeUtf8("WAVE")
    buffer.writeUtf8("fmt ").writeIntLe(16).writeShortLe(3).writeShortLe(1).writeIntLe(RATE).writeIntLe(RATE * 4)
        .writeShortLe(4).writeShortLe(32)
    buffer.writeUtf8("data").writeIntLe(frames * 4)
    for (i in 0 until frames) buffer.writeIntLe(frame(i).toBits())
    return buffer.readByteArray()
}
