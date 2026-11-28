package pianoanchor.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.Release
import pianoanchor.feature.today.resources.Res
import pianoanchor.feature.today.resources.today_action_retry
import pianoanchor.feature.today.resources.today_caught_up
import pianoanchor.feature.today.resources.today_error_storage
import pianoanchor.feature.today.resources.today_practice_days
import pianoanchor.feature.today.resources.today_update_available
import pianoanchor.feature.today.resources.today_update_dismiss
import pianoanchor.feature.today.resources.today_update_see
import pianoanchor.ui.shell.AppNavRail
import pianoanchor.ui.shell.RailDestination
import androidx.compose.material3.Card as MaterialCard

internal val TouchTarget = 44.dp
internal val CardShape = RoundedCornerShape(20.dp)

/** The right-hand pane (piano chip, practice days, update banner) in landscape. */
private val SidePaneWidth = 280.dp

/**
 * Today, landscape: a rail (Today, Songs, Pianos, Settings), the plan (greeting and the session hero) in a scrolling
 * column, and a side pane with the piano chip, the week card, the practice-day line and the update banner. An error
 * card tops the plan. Both panes scroll. Stateless but for the trim view's switches; nothing animates, so screenshots
 * stay still.
 */
@Composable
public fun TodayScreen(
    state: TodayState,
    actions: TodayActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.safeDrawingPadding()) {
            AppNavRail(
                RailDestination.TODAY,
                onToday = {},
                onSongs = actions.onSongs,
                onPianos = actions.onPianos,
                onSettings = actions.onSettings,
            )
            // Visual order, so TalkBack reads the plan first and the piano and practice days second.
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.storageFailed) ErrorCard(actions.onRetry)
                Header(state)
                if (state.session != null) {
                    if (state.caughtUp) {
                        Text(stringResource(Res.string.today_caught_up), style = MaterialTheme.typography.bodyLarge)
                    }
                    if (state.steps.isNotEmpty() || state.doneToday) SessionHero(state, actions.onStartSession)
                }
                Spacer(Modifier.height(16.dp))
            }
            Column(
                modifier =
                    Modifier
                        .width(SidePaneWidth)
                        .verticalScroll(rememberScrollState())
                        .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PianoStatus(state, actions.onPianos)
                state.week?.let { WeekCard(it, actions.onPauseWeek) }
                if (state.session != null) {
                    Text(
                        stringResource(Res.string.today_practice_days, state.practicedDays, PRACTICE_WINDOW_DAYS),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.update?.let { UpdateBanner(it, actions) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ErrorCard(onRetry: () -> Unit) {
    MaterialCard(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.today_error_storage))
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = TouchTarget)) {
                Text(stringResource(Res.string.today_action_retry), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

/** A newer version is out: its number, a link to the release page and "Not now". */
@Composable
private fun UpdateBanner(
    release: Release,
    actions: TodayActions,
) {
    MaterialCard(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.today_update_available, release.version))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { actions.onSeeRelease(release) },
                    modifier = Modifier.heightIn(min = TouchTarget),
                ) {
                    Text(
                        stringResource(Res.string.today_update_see),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                TextButton(onClick = actions.onDismissUpdate, modifier = Modifier.heightIn(min = TouchTarget)) {
                    Text(
                        stringResource(Res.string.today_update_dismiss),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
