package pianoanchor.feature.practice

import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.Passage
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.Tone
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.core.ports.TonePort
import pianoanchor.engine.midi.canPlayOn
import pianoanchor.engine.midi.playTones

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Plays a passage for the learner to hear: on [device] when [output] lists it, else on the speaker through [tone]. A
 * MIDI send that fails moves the rest of this player's life to the speaker, as the ear trainer does.
 */
public class PassagePlayer(
    private val device: MidiDevice,
    private val output: MidiOutputPort,
    private val tone: TonePort,
) {
    private var speakerOnly = false

    /** Plays [tones] and returns when they have ended; cancelling stops the sound. */
    internal suspend fun play(tones: List<Tone>) {
        if (!speakerOnly && output.canPlayOn(device)) {
            try {
                output.playTones(device, tones)
                return
            } catch (_: MidiDeviceUnavailableException) {
                speakerOnly = true
            }
        }
        tone.play(tones)
    }
}

/** The passage's notes as tones at its written tempo: each starts at its step's onset and lasts its written length. */
internal fun Passage.tones(): List<Tone> =
    steps.flatMap { step ->
        step.notes.map {
            Tone(
                it.pitch,
                step.onsetTicks.toMillis(tempoBpm),
                maxOf(1L, it.lengthTicks.toMillis(tempoBpm)),
            )
        }
    }

/** Ticks as milliseconds at [bpm] quarter notes a minute. */
internal fun Int.toMillis(bpm: Int): Long = this * MILLIS_PER_MINUTE / (TICKS_PER_QUARTER.toLong() * bpm)
