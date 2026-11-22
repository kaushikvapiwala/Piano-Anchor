package pianoanchor.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import pianoanchor.feature.today.resources.today_action_ear
import pianoanchor.feature.today.resources.today_card_ear
import pianoanchor.feature.today.resources.today_card_ear_skill_one
import pianoanchor.feature.today.resources.today_card_ear_skills
import pianoanchor.ui.shell.pianoAnchorColors

/** The Ear card: the ear accent, the first skill's title and how many skills the screen runs. */
@Composable
internal fun EarCardView(
    card: Card.Ear,
    onStart: (Card.Ear) -> Unit,
) {
    val colors = MaterialTheme.pianoAnchorColors
    OutlinedCard(
        shape = CardShape,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        border = BorderStroke(2.dp, colors.earAccent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(Res.string.today_card_ear, card.minutes.toString()),
                style = MaterialTheme.typography.labelMedium,
                color = colors.earAccent,
            )
            Text(card.skills.first().title, style = MaterialTheme.typography.titleLarge)
            Text(
                if (card.skills.size == 1) {
                    stringResource(Res.string.today_card_ear_skill_one)
                } else {
                    stringResource(Res.string.today_card_ear_skills, card.skills.size.toString())
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { onStart(card) },
                modifier = Modifier.heightIn(min = TouchTarget),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = colors.earAccent,
                        contentColor = colors.onEarAccent,
                    ),
            ) {
                Text(stringResource(Res.string.today_action_ear))
            }
        }
    }
}
