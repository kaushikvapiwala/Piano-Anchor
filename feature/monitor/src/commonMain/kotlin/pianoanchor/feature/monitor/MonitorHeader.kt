package pianoanchor.feature.monitor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import pianoanchor.feature.monitor.resources.monitor_back
import pianoanchor.feature.monitor.resources.monitor_back_glyph
import pianoanchor.feature.monitor.resources.monitor_title

/** The smallest touch target (44 dp, as on the other screens). */
private val TouchTarget = 44.dp

/** The screen's title, after a back button when [onBack] is set; the settings gear lives on Today. */
@Composable
internal fun Header(onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            val backLabel = stringResource(Res.string.monitor_back)
            TextButton(
                onClick = onBack,
                modifier = Modifier.size(TouchTarget).semantics { contentDescription = backLabel },
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
            ) {
                Text(
                    stringResource(Res.string.monitor_back_glyph),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            stringResource(Res.string.monitor_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = if (onBack != null) 8.dp else 0.dp),
        )
    }
}
