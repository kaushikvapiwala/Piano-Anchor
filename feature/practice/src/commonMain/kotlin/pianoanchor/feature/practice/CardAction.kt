package pianoanchor.feature.practice

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.practice.resources.Res

/** A button on an [MessageCard]: its [label] and what it does. */
internal class CardAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * The living-studio card: [message] and a text button per action, in order. Red ([containerColor] and [contentColor])
 * for an error, another colour pair for a notice.
 */
@Composable
internal fun MessageCard(
    message: String,
    vararg actions: CardAction,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.errorContainer,
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
            Text(message, modifier = Modifier.weight(1f))
            actions.forEach { action ->
                TextButton(
                    onClick = action.onClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                ) {
                    Text(action.label)
                }
            }
        }
    }
}
