package pianoanchor.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.SessionStep
import pianoanchor.feature.today.resources.Res
import pianoanchor.feature.today.resources.today_action_choose_steps
import pianoanchor.feature.today.resources.today_action_practise_more
import pianoanchor.feature.today.resources.today_action_start_session
import pianoanchor.feature.today.resources.today_done_detail
import pianoanchor.feature.today.resources.today_done_title
import pianoanchor.feature.today.resources.today_hero_bar_description
import pianoanchor.feature.today.resources.today_hero_title
import pianoanchor.feature.today.resources.today_trim_back
import pianoanchor.feature.today.resources.today_trim_minutes
import pianoanchor.feature.today.resources.today_trim_start
import pianoanchor.feature.today.resources.today_trim_switch_description
import pianoanchor.feature.today.resources.today_trim_title
import pianoanchor.ui.shell.PedalHintLine
import pianoanchor.ui.shell.pianoAnchorColors
import pianoanchor.ui.shell.withPedalHint

private val BarHeight = 10.dp
private val SwitchTarget = 48.dp
private val ColdOpenBackground = Color(0xFF2A2730)

/**
 * The Today hero: the day's steps as one card (a segmented bar and **Start session**), or the trim view that switches
 * steps off. After the recap it says the day is done. Stateless but for the trim view's switches.
 */
@Composable
internal fun SessionHero(
    state: TodayState,
    onStartSession: (Set<Int>) -> Unit,
) {
    var trimming by remember { mutableStateOf(false) }
    var off by remember { mutableStateOf<Set<SessionStep>>(emptySet()) }
    val steps = state.steps
    if (trimming && steps.isNotEmpty()) {
        TrimView(
            steps,
            off,
            // A song's Practise and Play along steps share one card, which the session skips: switch them together.
            onToggle = { step, on ->
                val together = steps.filter { it.card == step.card }.toSet()
                off = if (on) off - together else off + together
            },
            onStart = { onStartSession(off.mapTo(HashSet()) { state.cardIndex(it) }) },
            onBack = { trimming = false },
        )
    } else {
        val title =
            if (state.doneToday) {
                stringResource(Res.string.today_done_title)
            } else {
                stringResource(Res.string.today_hero_title, steps.sumOf { it.minutes })
            }
        OutlinedCard(
            shape = CardShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (state.doneToday) {
                    Text(stringResource(Res.string.today_done_detail), style = MaterialTheme.typography.bodyMedium)
                } else {
                    StepsBar(steps)
                }
                val action =
                    stringResource(
                        if (state.doneToday) {
                            Res.string.today_action_practise_more
                        } else {
                            Res.string.today_action_start_session
                        },
                    )
                Button(
                    onClick = { onStartSession(emptySet()) },
                    modifier = Modifier.heightIn(min = TouchTarget).withPedalHint(action, state.pedalHint),
                ) {
                    Text(action)
                }
                PedalHintLine(state.pedalHint)
                if (!state.doneToday) {
                    TextButton(onClick = { trimming = true }, modifier = Modifier.heightIn(min = TouchTarget)) {
                        Text(stringResource(Res.string.today_action_choose_steps))
                    }
                }
            }
        }
    }
}

/** One segment per step, as wide as its minutes; the caption is read aloud and the segments are silent. */
@Composable
private fun StepsBar(steps: List<SessionStep>) {
    val description = stringResource(Res.string.today_hero_bar_description, steps.size, steps.sumOf { it.minutes })
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .clearAndSetSemantics { contentDescription = description },
    ) {
        for (step in steps) {
            Box(
                Modifier
                    .weight(step.minutes.coerceAtLeast(1).toFloat())
                    .height(BarHeight)
                    .background(stepColor(step), CircleShape),
            )
        }
    }
}

@Composable
private fun stepColor(step: SessionStep): Color =
    when (step.card) {
        is Card.ColdOpen, is Card.Quizzes -> ColdOpenBackground
        is Card.Lesson -> MaterialTheme.colorScheme.primary
        is Card.Song -> MaterialTheme.colorScheme.outline
        is Card.SightReading -> MaterialTheme.colorScheme.tertiary
        is Card.Ear -> MaterialTheme.pianoAnchorColors.earAccent
    }

/** The steps with a switch each (on unless in [off]) and a button that starts the ones left on. */
@Composable
private fun TrimView(
    steps: List<SessionStep>,
    off: Set<SessionStep>,
    onToggle: (SessionStep, Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val chosen = steps.filter { it !in off }
    OutlinedCard(
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.today_trim_title), style = MaterialTheme.typography.titleLarge)
            for (step in steps) {
                val title = stepTitle(step)
                val minutes = stringResource(Res.string.today_trim_minutes, step.minutes)
                val description = stringResource(Res.string.today_trim_switch_description, title, step.minutes)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = SwitchTarget),
                ) {
                    Switch(
                        checked = step !in off,
                        onCheckedChange = { onToggle(step, it) },
                        modifier =
                            Modifier.sizeIn(minWidth = SwitchTarget, minHeight = SwitchTarget).semantics {
                                contentDescription = description
                            },
                    )
                    Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(minutes, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = onStart,
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.heightIn(min = TouchTarget),
            ) {
                Text(stringResource(Res.string.today_trim_start, chosen.size, chosen.sumOf { it.minutes }))
            }
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = TouchTarget)) {
                Text(stringResource(Res.string.today_trim_back))
            }
        }
    }
}
