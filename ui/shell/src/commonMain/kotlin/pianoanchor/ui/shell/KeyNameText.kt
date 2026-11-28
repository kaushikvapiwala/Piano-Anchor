package pianoanchor.ui.shell

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.lesson.Accidental
import pianoanchor.engine.lesson.KeyName
import pianoanchor.engine.lesson.Letter
import pianoanchor.ui.shell.resources.Res
import pianoanchor.ui.shell.resources.note_flat
import pianoanchor.ui.shell.resources.note_letter_a
import pianoanchor.ui.shell.resources.note_letter_b
import pianoanchor.ui.shell.resources.note_letter_c
import pianoanchor.ui.shell.resources.note_letter_d
import pianoanchor.ui.shell.resources.note_letter_e
import pianoanchor.ui.shell.resources.note_letter_f
import pianoanchor.ui.shell.resources.note_letter_g
import pianoanchor.ui.shell.resources.note_sharp

private fun Letter.resource(): StringResource =
    when (this) {
        Letter.C -> Res.string.note_letter_c
        Letter.D -> Res.string.note_letter_d
        Letter.E -> Res.string.note_letter_e
        Letter.F -> Res.string.note_letter_f
        Letter.G -> Res.string.note_letter_g
        Letter.A -> Res.string.note_letter_a
        Letter.B -> Res.string.note_letter_b
    }

/** The text of a key's [name] from string resources, so a translation can respell letters and accidentals. */
@Composable
fun keyNameText(name: KeyName): String {
    val letter = stringResource(name.letter.resource())
    return when (name.accidental) {
        Accidental.NATURAL -> letter
        Accidental.SHARP -> stringResource(Res.string.note_sharp, letter)
        Accidental.FLAT -> stringResource(Res.string.note_flat, letter)
    }
}
