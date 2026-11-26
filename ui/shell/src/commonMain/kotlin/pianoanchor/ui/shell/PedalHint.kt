package pianoanchor.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import pianoanchor.ui.shell.resources.Res
import pianoanchor.ui.shell.resources.pedal_hint
import pianoanchor.ui.shell.resources.pedal_hint_description

/**
 * The small line "Tap the pedal to continue" by a screen's primary button; shown only when [visible]. TalkBack reads it
 * as part of the button (see [withPedalHint]), so the line itself has no semantics.
 */
@Composable
public fun PedalHintLine(visible: Boolean) {
    if (visible) {
        Text(
            stringResource(Res.string.pedal_hint),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** A button's description, [label] plus the pedal hint, when [hint] is true, so TalkBack reads both. */
@Composable
public fun Modifier.withPedalHint(
    label: String,
    hint: Boolean,
): Modifier {
    val description = stringResource(Res.string.pedal_hint_description, label, stringResource(Res.string.pedal_hint))
    return if (hint) semantics { contentDescription = description } else this
}
