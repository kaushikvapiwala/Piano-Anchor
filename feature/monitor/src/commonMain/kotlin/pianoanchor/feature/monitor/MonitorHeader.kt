package pianoanchor.feature.monitor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.monitor.resources.Res
import pianoanchor.feature.monitor.resources.monitor_title
import pianoanchor.ui.shell.BackButton

/** The screen's title, after a back button when [onBack] is set; the settings gear lives on Today. */
@Composable
internal fun Header(onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            BackButton(onClick = onBack)
        }
        Text(
            stringResource(Res.string.monitor_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = if (onBack != null) 8.dp else 0.dp),
        )
    }
}
