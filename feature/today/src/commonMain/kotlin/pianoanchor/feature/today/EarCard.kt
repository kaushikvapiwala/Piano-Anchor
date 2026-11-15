package pianoanchor.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.session.Card
import pianoanchor.feature.today.resources.Res
import pianoanchor.feature.today.resources.today_action_ear
import pianoanchor.feature.today.resources.today_card_ear
import pianoanchor.feature.today.resources.today_card_ear_skill_one
import pianoanchor.feature.today.resources.today_card_ear_skills

private val TouchTarget = 44.dp
private val CardShape = RoundedCornerShape(20.dp)
private val AmberLight = Color(0xFF8A5300)
private val AmberDark = Color(0xFFFFC857)
private val OnAmberDark = Color(0xFF1B1B1F)

/** Below this luminance the surface is dark. */
private const val DARK_SURFACE_LUMINANCE = 0.5f

/** The Ear card: the amber accent, the first skill's title and how many skills the screen runs. */
@Composable
internal fun EarCardView(
    card: Card.Ear,
    onStart: (Card.Ear) -> Unit,
) {
    val amber = amberAccent()
    OutlinedCard(
        shape = CardShape,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        border = BorderStroke(2.dp, amber),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(Res.string.today_card_ear, card.minutes.toString()),
                style = MaterialTheme.typography.labelMedium,
                color = amber,
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
                        containerColor = amber,
                        contentColor =
                            if (amber ==
                                AmberDark
                            ) {
                                OnAmberDark
                            } else {
                                Color.White
                            },
                    ),
            ) {
                Text(stringResource(Res.string.today_action_ear))
            }
        }
    }
}

/** The ear accent: dark enough for text on the light surface, light on the dark one. */
@Composable
internal fun amberAccent(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() <
        DARK_SURFACE_LUMINANCE
    ) {
        AmberDark
    } else {
        AmberLight
    }
