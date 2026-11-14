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
import androidx.compose.ui.graphics.Color
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
