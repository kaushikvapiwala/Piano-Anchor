package pianoanchor.feature.practice

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_back
import pianoanchor.feature.practice.resources.practice_back_glyph

/** The 44 dp back arrow of the practice and results screens, read as "Back". */
@Composable
internal fun BackButton(onBack: () -> Unit) {
    val backLabel = stringResource(Res.string.practice_back)
    TextButton(
        onClick = onBack,
        modifier = Modifier.size(44.dp).semantics { contentDescription = backLabel },
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
    ) {
        Text(
            stringResource(Res.string.practice_back_glyph),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The living-studio error card: [message] and one [action] button that calls [onAction]. */
@Composable
internal fun ErrorCard(
    message: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) {
                Text(action)
            }
        }
    }
}
