package pianoanchor.feature.lesson

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.lesson.rhythmCount
import pianoanchor.feature.lesson.resources.Res
import pianoanchor.feature.lesson.resources.lesson_try_any_key
import pianoanchor.feature.lesson.resources.lesson_try_chord
import pianoanchor.feature.lesson.resources.lesson_try_even
import pianoanchor.feature.lesson.resources.lesson_try_find_all
import pianoanchor.feature.lesson.resources.lesson_try_hold
import pianoanchor.feature.lesson.resources.lesson_try_legato
import pianoanchor.feature.lesson.resources.lesson_try_pedal_change
import pianoanchor.feature.lesson.resources.lesson_try_pedal_hold
import pianoanchor.feature.lesson.resources.lesson_try_play
import pianoanchor.feature.lesson.resources.lesson_try_play_lit
import pianoanchor.feature.lesson.resources.lesson_try_repeat
import pianoanchor.feature.lesson.resources.lesson_try_rhythm
import pianoanchor.feature.lesson.resources.lesson_try_sequence
import pianoanchor.feature.lesson.resources.lesson_try_soft_loud
import pianoanchor.feature.lesson.resources.lesson_try_stress

private const val MIDDLE_C = 60

/** The per-check instruction of the Try. */
@Composable
internal fun instruction(check: TryCheck?): String =
    when (check) {
        null, is TryCheck.Passage -> {
            ""
        }

        TryCheck.AnyKey -> {
            stringResource(Res.string.lesson_try_any_key)
        }

        is TryCheck.PlayKey -> {
            stringResource(
                if (check.lit) Res.string.lesson_try_play_lit else Res.string.lesson_try_play,
                MidiNote(check.note).name,
            )
        }

        is TryCheck.FindAll -> {
            stringResource(Res.string.lesson_try_find_all, pitchClassName(check.pitchClass))
        }

        is TryCheck.Sequence, is TryCheck.EvenSequence -> {
            sequenceInstruction(check)
        }

        is TryCheck.Repeat -> {
            stringResource(Res.string.lesson_try_repeat, MidiNote(check.note).name, check.times.toString())
        }

        is TryCheck.Hold -> {
            stringResource(Res.string.lesson_try_hold, MidiNote(check.note).name, check.clicks.toString())
        }

        is TryCheck.SoftThenLoud -> {
            stringResource(Res.string.lesson_try_soft_loud, MidiNote(check.note).name)
        }

        is TryCheck.StressBeatOne -> {
            stringResource(Res.string.lesson_try_stress, MidiNote(check.note).name)
        }

        is TryCheck.Rhythm -> {
            stringResource(Res.string.lesson_try_rhythm, MidiNote(check.note).name, rhythmCount(check.lengths))
        }

        is TryCheck.Chord -> {
            stringResource(Res.string.lesson_try_chord, check.notes.sorted().joinToString(" ") { MidiNote(it).name })
        }

        is TryCheck.Legato, is TryCheck.PedalHold, is TryCheck.PedalChange -> {
            pedalInstruction(check)
        }
    }

/** "C" for pitch class 0 ... "B" for 11: a note name without its octave. */
private fun pitchClassName(pitchClass: Int): String = MidiNote(MIDDLE_C + pitchClass).name.trimEnd { it.isDigit() }

/** The instruction of the checks that play notes in order: any time, or evenly on the click. */
@Composable
private fun sequenceInstruction(check: TryCheck): String =
    when (check) {
        is TryCheck.EvenSequence -> {
            stringResource(Res.string.lesson_try_even, check.notes.joinToString(" ") { MidiNote(it).name })
        }

        else -> {
            val sequence = check as TryCheck.Sequence
            stringResource(Res.string.lesson_try_sequence, sequence.notes.joinToString(" ") { MidiNote(it).name })
        }
    }

/** The instruction of the legato and pedal checks. */
@Composable
private fun pedalInstruction(check: TryCheck): String =
    when (check) {
        is TryCheck.Legato -> {
            stringResource(Res.string.lesson_try_legato, check.notes.joinToString(" ") { MidiNote(it).name })
        }

        is TryCheck.PedalChange -> {
            stringResource(
                Res.string.lesson_try_pedal_change,
                check.chords.joinToString(", ") { chord -> chord.sorted().joinToString(" ") { MidiNote(it).name } },
            )
        }

        else -> {
            val hold = check as TryCheck.PedalHold
            stringResource(
                Res.string.lesson_try_pedal_hold,
                hold.chord.sorted().joinToString(" ") { MidiNote(it).name },
            )
        }
    }
