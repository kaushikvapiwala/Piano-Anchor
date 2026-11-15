package pianoanchor.feature.monitor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.monitor.resources.Res
import pianoanchor.feature.monitor.resources.monitor_title

/** The screen's title; the settings gear lives on Today. */
@Composable
internal fun Header() {
    Text(stringResource(Res.string.monitor_title), style = MaterialTheme.typography.headlineMedium)
}
