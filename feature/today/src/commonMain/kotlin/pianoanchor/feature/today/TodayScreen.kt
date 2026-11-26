package pianoanchor.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.Release
import pianoanchor.core.model.RunMode
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.Session
import pianoanchor.feature.today.resources.Res
import pianoanchor.feature.today.resources.today_action_cold_open
import pianoanchor.feature.today.resources.today_action_cold_open_perform
import pianoanchor.feature.today.resources.today_action_lesson
import pianoanchor.feature.today.resources.today_action_retry
import pianoanchor.feature.today.resources.today_action_sight
import pianoanchor.feature.today.resources.today_action_song
import pianoanchor.feature.today.resources.today_card_cold_open
import pianoanchor.feature.today.resources.today_card_detail
import pianoanchor.feature.today.resources.today_card_lesson
import pianoanchor.feature.today.resources.today_card_more_due
import pianoanchor.feature.today.resources.today_card_sight
import pianoanchor.feature.today.resources.today_card_sight_detail
import pianoanchor.feature.today.resources.today_card_song
import pianoanchor.feature.today.resources.today_card_tempo
import pianoanchor.feature.today.resources.today_card_wait
import pianoanchor.feature.today.resources.today_card_why_now
import pianoanchor.feature.today.resources.today_card_why_start
import pianoanchor.feature.today.resources.today_caught_up
import pianoanchor.feature.today.resources.today_error_storage
import pianoanchor.feature.today.resources.today_practice_days
import pianoanchor.feature.today.resources.today_session_bar
import pianoanchor.feature.today.resources.today_update_available
import pianoanchor.feature.today.resources.today_update_dismiss
import pianoanchor.feature.today.resources.today_update_see
import pianoanchor.ui.shell.AppNavRail
import pianoanchor.ui.shell.PedalHintLine
import pianoanchor.ui.shell.RailDestination
import pianoanchor.ui.shell.pianoAnchorColors
import pianoanchor.ui.shell.withPedalHint
import androidx.compose.material3.Card as MaterialCard

internal val TouchTarget = 44.dp
private val ColdOpenBackground = Color(0xFF2A2730)
private val OnColdOpen = Color(0xFFF7F5F2)
internal val CardShape = RoundedCornerShape(20.dp)
private val BarHeight = 10.dp

/** The right-hand pane (piano chip, practice days, update banner) in landscape. */
private val SidePaneWidth = 280.dp

/**
 * Today, landscape: a rail (Today, Songs, Pianos, Settings), the plan (greeting, the day's cards, the session bar) in
 * a scrolling column, and a side pane with the piano chip, weekly goal dots, practice-day line and update banner. An
 * error card tops the plan. Both panes scroll. Stateless; nothing animates, so screenshots stay still.
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
                val session = state.session
                if (session != null) {
                    if (state.caughtUp) {
                        Text(stringResource(Res.string.today_caught_up), style = MaterialTheme.typography.bodyLarge)
                    }
                    for (card in session.cards) {
                        val hint = state.pedalHint && card === session.cards.first()
                        when (card) {
                            is Card.PassageCard -> CardView(card, session, actions.onStart, hint)
                            is Card.Ear -> EarCardView(card, actions.onStart, hint)
                            is Card.Quizzes -> QuizzesCardView(card, actions.onStart, hint)
                        }
                    }
                    if (session.cards.isNotEmpty()) SessionBar(session)
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

@Composable
private fun CardView(
    card: Card.PassageCard,
    session: Session,
    onStart: (Card.PassageCard) -> Unit,
    pedalHint: Boolean = false,
) {
    val dark = card is Card.ColdOpen
    val content = if (dark) OnColdOpen else MaterialTheme.colorScheme.onSurface
    val detail =
        when (card) {
            is Card.Song -> tempoLine(card)
            is Card.ColdOpen -> stringResource(Res.string.today_card_detail, card.skill.title, card.minutes)
            is Card.Lesson -> stringResource(Res.string.today_card_detail, card.passage.title, card.minutes)
            is Card.SightReading -> stringResource(Res.string.today_card_sight_detail, card.minutes)
        }
    OutlinedCard(
        shape = CardShape,
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = if (dark) ColdOpenBackground else MaterialTheme.colorScheme.surface,
                contentColor = content,
            ),
        border = BorderStroke(1.dp, if (dark) ColdOpenBackground else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(cardLabel(card)), style = MaterialTheme.typography.labelMedium)
            Text(
                if (card is Card.Lesson) card.skill.title else card.passage.title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            if (card is Card.Lesson) Text(whyNow(card), style = MaterialTheme.typography.bodyMedium)
            if (card is Card.ColdOpen && session.moreDue.isNotEmpty()) {
                Text(
                    stringResource(Res.string.today_card_more_due, session.moreDue.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val action = stringResource(cardAction(card))
            Button(
                onClick = { onStart(card) },
                modifier = Modifier.heightIn(min = TouchTarget).withPedalHint(action, pedalHint),
                colors =
                    if (dark) {
                        ButtonDefaults.buttonColors(containerColor = OnColdOpen, contentColor = ColdOpenBackground)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
            ) {
                Text(action)
            }
            PedalHintLine(pedalHint)
        }
    }
}

private fun cardLabel(card: Card.PassageCard): StringResource =
    when (card) {
        is Card.ColdOpen -> Res.string.today_card_cold_open
        is Card.Lesson -> Res.string.today_card_lesson
        is Card.Song -> Res.string.today_card_song
        is Card.SightReading -> Res.string.today_card_sight
    }

private fun cardAction(card: Card.PassageCard): StringResource =
    when {
        card.mode == RunMode.PERFORM -> Res.string.today_action_cold_open_perform
        card is Card.ColdOpen -> Res.string.today_action_cold_open
        card is Card.Lesson -> Res.string.today_action_lesson
        card is Card.SightReading -> Res.string.today_action_sight
        else -> Res.string.today_action_song
    }

@Composable
private fun whyNow(lesson: Card.Lesson): String =
    if (lesson.unlockedBy.isEmpty()) {
        stringResource(Res.string.today_card_why_start)
    } else {
        stringResource(Res.string.today_card_why_now, lesson.unlockedBy.joinToString { it.title })
    }

@Composable
private fun tempoLine(card: Card.Song): String {
    val rungs = TempoRung.entries.size
    val position = card.rung.ordinal + 1
    val bpm = card.rung.bpm(card.passage)
    return if (bpm == null) {
        stringResource(Res.string.today_card_wait, position, rungs)
    } else {
        stringResource(Res.string.today_card_tempo, bpm, position, rungs)
    }
}

/** The Review, Lesson, Song, Sight-reading and Ear minutes as proportional segments; the caption is read aloud. */
@Composable
private fun SessionBar(session: Session) {
    val total =
        session.reviewMinutes + session.lessonMinutes + session.songMinutes + session.sightMinutes + session.earMinutes
    val description =
        stringResource(
            Res.string.today_session_bar,
            total,
            session.reviewMinutes,
            session.lessonMinutes,
            session.songMinutes,
            session.sightMinutes,
            session.earMinutes,
        )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().height(BarHeight).clearAndSetSemantics {},
        ) {
            val segments =
                listOf(
                    session.reviewMinutes to ColdOpenBackground,
                    session.lessonMinutes to MaterialTheme.colorScheme.primary,
                    session.songMinutes to MaterialTheme.colorScheme.outline,
                    session.sightMinutes to MaterialTheme.colorScheme.tertiary,
                    session.earMinutes to MaterialTheme.pianoAnchorColors.earAccent,
                )
            for ((minutes, color) in segments) {
                if (minutes > 0) {
                    Box(Modifier.weight(minutes.toFloat()).height(BarHeight).background(color, CircleShape))
                }
            }
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
