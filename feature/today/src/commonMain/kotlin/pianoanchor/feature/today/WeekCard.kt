package pianoanchor.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.Tunables
import pianoanchor.engine.session.WeekStatus
import pianoanchor.feature.today.resources.Res
import pianoanchor.feature.today.resources.today_week_busy
import pianoanchor.feature.today.resources.today_week_goal
import pianoanchor.feature.today.resources.today_week_pause
import pianoanchor.feature.today.resources.today_week_pause_cancel
import pianoanchor.feature.today.resources.today_week_pause_confirm
import pianoanchor.feature.today.resources.today_week_pause_confirm_text
import pianoanchor.feature.today.resources.today_week_pause_confirm_title
import pianoanchor.feature.today.resources.today_week_pause_note
import pianoanchor.feature.today.resources.today_week_paused
import pianoanchor.feature.today.resources.today_week_pauses_left
import pianoanchor.feature.today.resources.today_week_streak
import pianoanchor.feature.today.resources.today_week_title

private val DotSize = 12.dp

/**
 * The week card: dots for the days played against the goal, the streak in weeks, and while [WeekStatus.canPause] the
 * "Pause this week" offer, which asks to confirm. A paused week says so instead. The dots of the pauses left close it.
 */
@Composable
internal fun WeekCard(
    week: WeekStatus,
    onPause: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    OutlinedCard(
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.today_week_title, week.daysPlayed, week.goalDays),
                style = MaterialTheme.typography.titleMedium,
            )
            Dots(
                filled = week.daysPlayed,
                total = maxOf(week.goalDays, week.daysPlayed),
                description = stringResource(Res.string.today_week_goal, week.daysPlayed, week.goalDays),
            )
            Text(
                stringResource(Res.string.today_week_streak, week.streakWeeks),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (week.paused) {
                Text(stringResource(Res.string.today_week_paused), style = MaterialTheme.typography.bodyMedium)
            } else if (week.canPause) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.today_week_busy), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { confirming = true }, modifier = Modifier.heightIn(min = TouchTarget)) {
                        Text(stringResource(Res.string.today_week_pause))
                    }
                }
                Text(
                    stringResource(Res.string.today_week_pause_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Dots(
                filled = week.pausesLeft,
                total = Tunables.WEEK_PAUSES_MAX,
                description =
                    stringResource(
                        Res.string.today_week_pauses_left,
                        week.pausesLeft,
                        Tunables.WEEK_PAUSES_MAX,
                    ),
            )
        }
    }
    if (confirming) {
        ConfirmPauseDialog(
            pausesLeftAfter = week.pausesLeft - 1,
            onConfirm = {
                confirming = false
                onPause()
            },
            onCancel = { confirming = false },
        )
    }
}

@Composable
private fun ConfirmPauseDialog(
    pausesLeftAfter: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.today_week_pause_confirm_title)) },
        text = { Text(stringResource(Res.string.today_week_pause_confirm_text, pausesLeftAfter)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) { Text(stringResource(Res.string.today_week_pause_confirm)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(Res.string.today_week_pause_cancel)) } },
    )
}

/** [total] dots, the first [filled] solid; read aloud as [description]. */
@Composable
private fun Dots(
    filled: Int,
    total: Int,
    description: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        repeat(total) { index ->
            val solid = index < filled
            val color = if (solid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(modifier = Modifier.size(DotSize).background(color, CircleShape))
        }
    }
}
