package pianoanchor.feature.lesson

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.MidiNote
import pianoanchor.engine.guidance.Direction
import pianoanchor.engine.guidance.Landmark
import pianoanchor.engine.guidance.WrongKeyHint
import pianoanchor.feature.lesson.resources.Res
import pianoanchor.feature.lesson.resources.quiz_hint_between_two
import pianoanchor.feature.lesson.resources.quiz_hint_black_above
import pianoanchor.feature.lesson.resources.quiz_hint_in_three_high
import pianoanchor.feature.lesson.resources.quiz_hint_in_three_low
import pianoanchor.feature.lesson.resources.quiz_hint_left_of_three
import pianoanchor.feature.lesson.resources.quiz_hint_left_of_two
import pianoanchor.feature.lesson.resources.quiz_hint_right_of_three
import pianoanchor.feature.lesson.resources.quiz_hint_right_of_two
import pianoanchor.feature.lesson.resources.quiz_hint_same_key
import pianoanchor.feature.lesson.resources.quiz_hint_that_was

/** A key's name for reading, with the sharp sign: "C♯4". */
private fun MidiNote.spoken(): String = name.replace("#", "♯")

/** A key's letter without its octave: "C♯". */
private fun letter(note: Int): String = MidiNote(note).spoken().trimEnd { it.isDigit() || it == '-' }

/**
 * "That was F4. C is just left of the two black keys.": the played key, then how to find the expected one. The same
 * words as the practice screen's hint (`docs/algorithms/wrong-key-hint.md`), in this module's strings.
 */
@Composable
internal fun quizHintText(hint: WrongKeyHint): String {
    val expected = letter(hint.expected)
    val how =
        if (hint.direction == Direction.SAME_KEY_OTHER_OCTAVE) {
            stringResource(Res.string.quiz_hint_same_key, MidiNote(hint.expected).spoken())
        } else {
            when (hint.landmark) {
                Landmark.LEFT_OF_TWO -> {
                    stringResource(Res.string.quiz_hint_left_of_two, expected)
                }

                Landmark.BETWEEN_TWO -> {
                    stringResource(Res.string.quiz_hint_between_two, expected)
                }

                Landmark.RIGHT_OF_TWO -> {
                    stringResource(Res.string.quiz_hint_right_of_two, expected)
                }

                Landmark.LEFT_OF_THREE -> {
                    stringResource(Res.string.quiz_hint_left_of_three, expected)
                }

                Landmark.IN_THREE_LOW -> {
                    stringResource(Res.string.quiz_hint_in_three_low, expected)
                }

                Landmark.IN_THREE_HIGH -> {
                    stringResource(Res.string.quiz_hint_in_three_high, expected)
                }

                Landmark.RIGHT_OF_THREE -> {
                    stringResource(Res.string.quiz_hint_right_of_three, expected)
                }

                Landmark.BLACK_ABOVE -> {
                    stringResource(Res.string.quiz_hint_black_above, expected, letter(hint.expected - 1))
                }
            }
        }
    return stringResource(Res.string.quiz_hint_that_was, MidiNote(hint.played).spoken(), how)
}
