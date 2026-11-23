package pianoanchor.feature.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.monitor.resources.Res
import pianoanchor.feature.monitor.resources.monitor_calibrate
import pianoanchor.feature.monitor.resources.monitor_practice

/** Wait-mode practice and latency calibration of the selected piano, while it is connected. */
@Composable
internal fun PracticeButtons(
    state: MonitorState,
    actions: MonitorActions,
) {
    val device = state.selected
    val enabled = device != null && state.connection == Connection.CONNECTED
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.onPractice?.let { onPractice ->
            Button(onClick = { device?.let(onPractice) }, enabled = enabled) {
                Text(stringResource(Res.string.monitor_practice))
            }
        }
        actions.onCalibrate?.let { onCalibrate ->
            Button(onClick = { device?.let(onCalibrate) }, enabled = enabled) {
                Text(stringResource(Res.string.monitor_calibrate))
            }
        }
    }
}
