package pianoanchor.feature.practice

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_backing_chip

/** The "Chords + click" chip: selected while the chords play with the click; tapping turns them off or on. */
@Composable
internal fun BackingChip(
    on: Boolean,
    onToggle: () -> Unit,
) {
    FilterChip(
        selected = on,
        onClick = onToggle,
        label = { Text(stringResource(Res.string.practice_backing_chip), fontSize = 12.sp) },
    )
}
