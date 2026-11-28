package pianoanchor.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.progress.resources.Res
import pianoanchor.feature.progress.resources.progress_action_retry
import pianoanchor.feature.progress.resources.progress_error_storage
import pianoanchor.feature.progress.resources.progress_title
import pianoanchor.ui.shell.AppNavRail
import pianoanchor.ui.shell.RailDestination

internal val TouchTarget: Dp = 48.dp

/**
 * Progress, landscape: the rail with Progress selected, then two scrolling columns. The first has the week card (days
 * played against the goal, the streak and the pause status, read-only) and the minutes by day as seven bars; the second
 * has what the learner learned this week and, when two takes exist, "Day 1 vs today" with a play button and a
 * note-timing lane per take. A storage error banner tops the first column. Stateless; nothing animates, so screenshots
 * stay still.
 */
@Composable
public fun ProgressScreen(
    state: ProgressState,
    actions: ProgressActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.safeDrawingPadding()) {
            AppNavRail(
                RailDestination.PROGRESS,
                onToday = actions.onToday,
                onSongs = actions.onSongs,
                onProgress = {},
                onPianos = actions.onPianos,
                onSettings = actions.onSettings,
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(Res.string.progress_title), style = MaterialTheme.typography.headlineMedium)
                if (state.storageFailed) ErrorCard(actions.onRetry)
                state.week?.let { WeekCard(it) }
                if (state.minutesByDay.isNotEmpty()) MinutesBars(state.minutesByDay, state.todayIndex)
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.week != null) LearnedCard(state.learnedThisWeek)
                state.compare?.let { CompareCard(it, state.playing, actions) }
            }
        }
    }
}

@Composable
private fun ErrorCard(onRetry: () -> Unit) {
    OutlinedCard(
        shape = CardShape,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.progress_error_storage))
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = TouchTarget)) {
                Text(
                    stringResource(Res.string.progress_action_retry),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
