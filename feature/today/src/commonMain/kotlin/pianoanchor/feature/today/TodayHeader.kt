package pianoanchor.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.today.resources.Res
import pianoanchor.feature.today.resources.today_calling
import pianoanchor.feature.today.resources.today_chip_separator
import pianoanchor.feature.today.resources.today_greeting_afternoon
import pianoanchor.feature.today.resources.today_greeting_evening
import pianoanchor.feature.today.resources.today_greeting_morning
import pianoanchor.feature.today.resources.today_latency_ms
import pianoanchor.feature.today.resources.today_piano_none
import pianoanchor.feature.today.resources.today_piano_none_description
import pianoanchor.feature.today.resources.today_ready
import pianoanchor.feature.today.resources.today_transport_ble
import pianoanchor.feature.today.resources.today_transport_usb
import pianoanchor.feature.today.resources.today_week_goal

private val TouchTarget = 44.dp
private val DotSize = 12.dp

/** The greeting, the line naming the cold-open skill, the piano chip and the weekly goal dots. */
@Composable
internal fun Header(
    state: TodayState,
    onPianos: () -> Unit,
) {
    val greeting =
        when (state.greeting) {
            Greeting.MORNING -> Res.string.today_greeting_morning
            Greeting.AFTERNOON -> Res.string.today_greeting_afternoon
            Greeting.EVENING -> Res.string.today_greeting_evening
        }
    Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(greeting), style = MaterialTheme.typography.headlineMedium)
        if (state.session != null) {
            val calling =
                state.coldOpen?.let { stringResource(Res.string.today_calling, it.skill.title) }
                    ?: stringResource(Res.string.today_ready)
            Text(
                calling,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PianoChipView(state.piano, onPianos)
            WeekDots(state.daysThisWeek, state.weeklyGoalDays)
        }
    }
}

@Composable
private fun PianoChipView(
    piano: PianoChip?,
    onPianos: () -> Unit,
) {
    val text =
        if (piano == null) {
            stringResource(Res.string.today_piano_none)
        } else {
            listOfNotNull(
                piano.device.name,
                piano.transport?.let {
                    stringResource(
                        if (it ==
                            Transport.USB
                        ) {
                            Res.string.today_transport_usb
                        } else {
                            Res.string.today_transport_ble
                        },
                    )
                },
                piano.latencyMs?.let { stringResource(Res.string.today_latency_ms, it) },
            ).joinToString(stringResource(Res.string.today_chip_separator))
        }
    val description = if (piano == null) stringResource(Res.string.today_piano_none_description) else text
    OutlinedCard(
        onClick = onPianos,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.heightIn(min = TouchTarget).semantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier.heightIn(min = TouchTarget).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun WeekDots(
    done: Int,
    goal: Int,
) {
    val description = stringResource(Res.string.today_week_goal, done, goal)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        repeat(goal) { index ->
            val color =
                if (index <
                    done
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            Box(modifier = Modifier.size(DotSize).background(color, CircleShape))
        }
    }
}
