package pianoanchor.ui.shell

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.guidance.Direction
import pianoanchor.engine.guidance.Landmark
import pianoanchor.engine.guidance.WrongKeyHint
import pianoanchor.engine.lesson.NoteNameRule
import pianoanchor.ui.shell.resources.Res
import pianoanchor.ui.shell.resources.hint_between_two
import pianoanchor.ui.shell.resources.hint_black_above
import pianoanchor.ui.shell.resources.hint_in_three_high
import pianoanchor.ui.shell.resources.hint_in_three_low
import pianoanchor.ui.shell.resources.hint_left_of_three
import pianoanchor.ui.shell.resources.hint_left_of_two
import pianoanchor.ui.shell.resources.hint_right_of_three
import pianoanchor.ui.shell.resources.hint_right_of_two
import pianoanchor.ui.shell.resources.hint_same_key
import pianoanchor.ui.shell.resources.hint_that_was
import pianoanchor.ui.shell.resources.note_with_octave

/** A key's name with its octave, from the shared key-name resources: "C♯4" (middle C, 60, is C4). */
@Composable
internal fun keyNameWithOctaveText(note: Int): String =
    stringResource(Res.string.note_with_octave, keyNameText(NoteNameRule.keyNameOf(note)), note / OCTAVE - 1)

@Composable
private fun letterText(note: Int): String = keyNameText(NoteNameRule.keyNameOf(note))

private const val OCTAVE = 12

/** "That was F4. C is just left of the two black keys.": the played key, then how to find the expected one. */
@Composable
fun wrongKeyHintText(hint: WrongKeyHint): String {
    val expected = letterText(hint.expected)
    val how =
        if (hint.direction == Direction.SAME_KEY_OTHER_OCTAVE) {
            stringResource(Res.string.hint_same_key, keyNameWithOctaveText(hint.expected))
        } else {
            when (hint.landmark) {
                Landmark.LEFT_OF_TWO -> {
                    stringResource(Res.string.hint_left_of_two, expected)
                }

                Landmark.BETWEEN_TWO -> {
                    stringResource(Res.string.hint_between_two, expected)
                }

                Landmark.RIGHT_OF_TWO -> {
                    stringResource(Res.string.hint_right_of_two, expected)
                }

                Landmark.LEFT_OF_THREE -> {
                    stringResource(Res.string.hint_left_of_three, expected)
                }

                Landmark.IN_THREE_LOW -> {
                    stringResource(Res.string.hint_in_three_low, expected)
                }

                Landmark.IN_THREE_HIGH -> {
                    stringResource(Res.string.hint_in_three_high, expected)
                }

                Landmark.RIGHT_OF_THREE -> {
                    stringResource(Res.string.hint_right_of_three, expected)
                }

                Landmark.BLACK_ABOVE -> {
                    stringResource(
                        Res.string.hint_black_above,
                        expected,
                        letterText(hint.expected - 1),
                    )
                }
            }
        }
    return stringResource(Res.string.hint_that_was, keyNameWithOctaveText(hint.played), how)
}
