package pianoanchor.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.session.Card
import pianoanchor.feature.today.resources.Res
import pianoanchor.feature.today.resources.today_action_quizzes
import pianoanchor.feature.today.resources.today_card_quizzes
import pianoanchor.feature.today.resources.today_card_quizzes_detail

/** The Quizzes card: "Quick quizzes · 3", the minutes and a button that opens the quiz screen. */
@Composable
internal fun QuizzesCardView(
    card: Card.Quizzes,
    onStart: (Card.Quizzes) -> Unit,
    pedalHint: Boolean = false,
) {
    OutlinedCard(
        shape = CardShape,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(Res.string.today_card_quizzes, card.quizzes.size.toString()),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(Res.string.today_card_quizzes_detail, card.minutes.toString()),
                style = MaterialTheme.typography.bodyMedium,
            )
            val action = stringResource(Res.string.today_action_quizzes)
            Button(
                onClick = { onStart(card) },
                modifier = Modifier.heightIn(min = TouchTarget).withPedalHint(action, pedalHint),
            ) {
                Text(action)
            }
            PedalHint(pedalHint)
        }
    }
}
