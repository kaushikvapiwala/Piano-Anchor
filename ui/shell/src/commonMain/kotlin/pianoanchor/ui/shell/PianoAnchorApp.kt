package pianoanchor.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.ui.shell.resources.Res
import pianoanchor.ui.shell.resources.app_name
import pianoanchor.ui.shell.resources.welcome

/** Root composable shared by the Android and desktop apps. */
@Composable
fun PianoAnchorApp(modifier: Modifier = Modifier) {
    MaterialTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(Res.string.welcome), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
