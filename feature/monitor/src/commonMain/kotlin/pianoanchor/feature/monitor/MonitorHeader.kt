package pianoanchor.feature.monitor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.monitor.resources.Res
import pianoanchor.feature.monitor.resources.monitor_settings
import pianoanchor.feature.monitor.resources.monitor_settings_glyph
import pianoanchor.feature.monitor.resources.monitor_title

/** The title and the 44 dp gear that opens the settings (read as "Settings"). */
@Composable
internal fun Header(onSettings: () -> Unit) {
    val settingsLabel = stringResource(Res.string.monitor_settings)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.monitor_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onSettings,
            modifier = Modifier.size(44.dp).semantics { contentDescription = settingsLabel },
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        ) {
            Text(
                stringResource(Res.string.monitor_settings_glyph),
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
