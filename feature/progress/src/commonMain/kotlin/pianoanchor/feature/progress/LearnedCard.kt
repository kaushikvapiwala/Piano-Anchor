package pianoanchor.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.Skill
import pianoanchor.feature.progress.resources.Res
import pianoanchor.feature.progress.resources.progress_learned_more
import pianoanchor.feature.progress.resources.progress_learned_none
import pianoanchor.feature.progress.resources.progress_learned_title

/** The most skills the card lists; the rest are counted as "+N more". */
internal const val LEARNED_SHOWN_MAX = 5

/** What the learner learned this week: up to [LEARNED_SHOWN_MAX] skill titles, then "+N more". */
@Composable
internal fun LearnedCard(skills: List<Skill>) {
    OutlinedCard(shape = CardShape, border = cardBorder(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.progress_learned_title), style = MaterialTheme.typography.titleMedium)
            if (skills.isEmpty()) {
                Text(
                    stringResource(Res.string.progress_learned_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            skills.take(LEARNED_SHOWN_MAX).forEach { Text(it.title, style = MaterialTheme.typography.bodyLarge) }
            if (skills.size > LEARNED_SHOWN_MAX) {
                Text(
                    stringResource(Res.string.progress_learned_more, skills.size - LEARNED_SHOWN_MAX),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
