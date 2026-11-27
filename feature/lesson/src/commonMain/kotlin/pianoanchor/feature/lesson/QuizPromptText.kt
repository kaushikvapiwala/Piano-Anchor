package pianoanchor.feature.lesson

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.MidiNote
import pianoanchor.engine.lesson.ChordName
import pianoanchor.engine.lesson.QuizPrompt
import pianoanchor.engine.lesson.rhythmCount
import pianoanchor.feature.lesson.resources.Res
import pianoanchor.feature.lesson.resources.quiz_accent
import pianoanchor.feature.lesson.resources.quiz_chord
import pianoanchor.feature.lesson.resources.quiz_click_times
import pianoanchor.feature.lesson.resources.quiz_hold_for
import pianoanchor.feature.lesson.resources.quiz_play_every
import pianoanchor.feature.lesson.resources.quiz_play_key
import pianoanchor.feature.lesson.resources.quiz_play_key_staff
import pianoanchor.feature.lesson.resources.quiz_skip_up_from
import pianoanchor.feature.lesson.resources.quiz_soft_loud
import pianoanchor.feature.lesson.resources.quiz_step_up_from
import pianoanchor.feature.lesson.resources.quiz_tap_rhythm

private val FLAT_LETTERS = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
private val SHARP_LETTERS = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
private const val NOTES_PER_OCTAVE = 12

/** A key's name with its octave, a black key spelled with a flat when [flat]: "D♭4". */
internal fun spelledName(
    note: Int,
    flat: Boolean = false,
): String {
    val letters = if (flat) FLAT_LETTERS else SHARP_LETTERS
    return letters[note % NOTES_PER_OCTAVE] + (note / NOTES_PER_OCTAVE - 1)
}

/** "C" for pitch class 0 ... "B" for 11: a note name without its octave. */
internal fun letterName(pitchClass: Int): String = SHARP_LETTERS[pitchClass]

/** The instruction line of a quiz. */
@Composable
internal fun quizPromptText(prompt: QuizPrompt): String =
    when (prompt) {
        is QuizPrompt.PlayEvery -> {
            stringResource(Res.string.quiz_play_every, letterName(prompt.pitchClass))
        }

        is QuizPrompt.PlayKey -> {
            if (prompt.onStaff) {
                stringResource(Res.string.quiz_play_key_staff)
            } else {
                stringResource(Res.string.quiz_play_key, spelledName(prompt.note, prompt.flat))
            }
        }

        is QuizPrompt.SkipUpFrom -> {
            stringResource(Res.string.quiz_skip_up_from, MidiNote(prompt.note).name)
        }

        is QuizPrompt.StepUpFrom -> {
            stringResource(Res.string.quiz_step_up_from, MidiNote(prompt.note).name)
        }

        is QuizPrompt.HoldFor -> {
            stringResource(Res.string.quiz_hold_for, MidiNote(prompt.note).name, prompt.clicks.toString())
        }

        is QuizPrompt.ClickTimes -> {
            stringResource(Res.string.quiz_click_times, MidiNote(prompt.note).name, prompt.times.toString())
        }

        is QuizPrompt.SoftThenLoud -> {
            stringResource(Res.string.quiz_soft_loud, MidiNote(prompt.note).name)
        }

        is QuizPrompt.AccentBeatOne -> {
            stringResource(Res.string.quiz_accent, MidiNote(prompt.note).name)
        }

        is QuizPrompt.TapRhythm -> {
            stringResource(Res.string.quiz_tap_rhythm, MidiNote(prompt.note).name, rhythmCount(prompt.lengths))
        }

        is QuizPrompt.ChordOf -> {
            stringResource(Res.string.quiz_chord, chordLetter(prompt.name))
        }
    }

private fun chordLetter(name: ChordName): String = name.name
