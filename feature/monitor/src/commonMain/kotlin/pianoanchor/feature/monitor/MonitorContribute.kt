package pianoanchor.feature.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.monitor.resources.Res
import pianoanchor.feature.monitor.resources.monitor_contribute_help
import pianoanchor.feature.monitor.resources.monitor_contribute_title
import pianoanchor.feature.monitor.resources.monitor_save_kit

/** The "Contribute this piano" block under the recording controls: help text and the save-kit button. */
@Composable
internal fun ContributeBlock(
    state: MonitorState,
    actions: MonitorActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.monitor_contribute_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.monitor_contribute_help), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = actions.onSaveKit, enabled = !state.recording && state.recordedCount > 0) {
            Text(stringResource(Res.string.monitor_save_kit))
        }
    }
}
