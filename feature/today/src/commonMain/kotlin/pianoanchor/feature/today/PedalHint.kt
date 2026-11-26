package pianoanchor.feature.today

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.today.resources.Res
import pianoanchor.feature.today.resources.today_pedal_hint

/** The small line by a card's button saying that a pedal tap starts it; shown only when [visible]. */
@Composable
internal fun PedalHint(visible: Boolean) {
    if (visible) {
        Text(
            stringResource(Res.string.today_pedal_hint),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** A button's description with the pedal hint appended when [hint] is true, so TalkBack reads both. */
@Composable
internal fun Modifier.withPedalHint(
    label: String,
    hint: Boolean,
): Modifier {
    val line = stringResource(Res.string.today_pedal_hint)
    return if (hint) semantics { contentDescription = "$label. $line" } else this
}
