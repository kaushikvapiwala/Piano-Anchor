package pianoanchor.feature.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.BarRange
import pianoanchor.engine.Tunables
import pianoanchor.feature.today.resources.Res
import pianoanchor.feature.today.resources.leave_keep
import pianoanchor.feature.today.resources.leave_leave
import pianoanchor.feature.today.resources.leave_text
import pianoanchor.feature.today.resources.leave_title
import pianoanchor.feature.today.resources.recap_done
import pianoanchor.feature.today.resources.recap_learned_none
import pianoanchor.feature.today.resources.recap_learned_title
import pianoanchor.feature.today.resources.recap_minutes
import pianoanchor.feature.today.resources.recap_steps_title
import pianoanchor.feature.today.resources.recap_time_title
import pianoanchor.feature.today.resources.recap_tip_bar
import pianoanchor.feature.today.resources.recap_tip_bars
import pianoanchor.feature.today.resources.recap_tip_none
import pianoanchor.feature.today.resources.recap_tip_title
import pianoanchor.feature.today.resources.recap_title
import pianoanchor.feature.today.resources.session_go_now_label
import pianoanchor.feature.today.resources.session_next_label
import pianoanchor.feature.today.resources.session_pause
import pianoanchor.feature.today.resources.session_resume
import pianoanchor.feature.today.resources.session_seconds_description
import pianoanchor.feature.today.resources.session_skip_step
import pianoanchor.ui.shell.PedalHintLine
import pianoanchor.ui.shell.rememberReduceMotion

private val RingSize = 120.dp
private val RingStroke = 8.dp
private const val PLUM_TINT = 0.14f

/** What the user can do on the [SessionFlowScreen]. */
public data class SessionFlowActions(
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onGoNow: () -> Unit = {},
    val onDone: () -> Unit = {},
)

/**
 * The session flow between its steps, landscape: the card that counts down to the next step, then the recap. Nothing
 * else shows while a step runs (its own screen is on top). Stateless. [reduceMotion] stops the ring from animating;
 * the number still counts.
 */
@Composable
public fun SessionFlowScreen(
    state: SessionFlowState,
    actions: SessionFlowActions,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            val between = state.between
            val recap = state.recap
            if (recap != null) {
                RecapView(recap, actions.onDone)
            } else if (between != null) {
                BetweenCard(state, between, actions, reduceMotion)
            }
        }
    }
}

/** The full plum-tint card between two steps: ring, "NEXT · STEP k OF n", the title, Pause and Skip step. */
@Composable
private fun BetweenCard(
    state: SessionFlowState,
    between: Between,
    actions: SessionFlowActions,
    reduceMotion: Boolean,
) {
    val tint = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, PLUM_TINT)
    val goNow = stringResource(Res.string.session_go_now_label)
    val label = stringResource(Res.string.session_next_label, state.current + 2, state.steps.size)
    Surface(
        color = tint,
        shape = CardShape,
        modifier = Modifier.fillMaxSize().clickable(onClickLabel = goNow, onClick = actions.onGoNow),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxSize().padding(24.dp),
        ) {
            CountdownRing(between.secondsLeft, reduceMotion)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                // A polite live region: TalkBack announces the next step once, when it appears, not every second.
                Text(
                    stepTitle(between.next),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val pauseLabel =
                        stringResource(if (between.paused) Res.string.session_resume else Res.string.session_pause)
                    Button(
                        onClick = if (between.paused) actions.onResume else actions.onPause,
                        modifier = Modifier.heightIn(min = TouchTarget),
                    ) { Text(pauseLabel) }
                    OutlinedButton(onClick = actions.onSkip, modifier = Modifier.heightIn(min = TouchTarget)) {
                        Text(stringResource(Res.string.session_skip_step))
                    }
                }
                PedalHintLine(state.pedalHint)
            }
        }
    }
}

/** A ring that empties over the countdown, with the seconds left in its middle. The seconds are not announced. */
@Composable
private fun CountdownRing(
    secondsLeft: Int,
    reduceMotion: Boolean,
) {
    val target = secondsLeft.toFloat() / Tunables.SESSION_NEXT_COUNTDOWN_S
    // Reduced motion: the ring jumps a second at a time (no animation); the number counts either way.
    val animated by animateFloatAsState(target)
    val fraction = if (reduceMotion) target else animated
    val track = MaterialTheme.colorScheme.outlineVariant
    val arc = MaterialTheme.colorScheme.primary
    val description = stringResource(Res.string.session_seconds_description, secondsLeft)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(RingSize).clearAndSetSemantics { contentDescription = description },
    ) {
        Canvas(Modifier.size(RingSize)) {
            val stroke = Stroke(RingStroke.toPx(), cap = StrokeCap.Round)
            val inset = RingStroke.toPx() / 2
            val box = Size(size.width - 2 * inset, size.height - 2 * inset)
            drawArc(track, 0f, FULL_TURN, false, Offset(inset, inset), box, style = stroke)
            drawArc(arc, -QUARTER_TURN, FULL_TURN * fraction, false, Offset(inset, inset), box, style = stroke)
        }
        Text(secondsLeft.toString(), style = MaterialTheme.typography.headlineLarge)
    }
}

private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f

/** The recap in three columns: time played, steps done and what was learned; the tip and Done below. */
@Composable
private fun RecapView(
    recap: SessionRecap,
    onDone: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Text(stringResource(Res.string.recap_title), style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            RecapColumn(stringResource(Res.string.recap_time_title), Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.recap_minutes, recap.minutes),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            RecapColumn(stringResource(Res.string.recap_steps_title), Modifier.weight(1f)) {
                Text(recap.stepsDone.toString(), style = MaterialTheme.typography.headlineMedium)
            }
            RecapColumn(stringResource(Res.string.recap_learned_title), Modifier.weight(1f)) {
                if (recap.learned.isEmpty()) {
                    Text(stringResource(Res.string.recap_learned_none), style = MaterialTheme.typography.bodyMedium)
                }
                for (skill in recap.learned) Text(skill.title, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(stringResource(Res.string.recap_tip_title), style = MaterialTheme.typography.labelLarge)
        Text(tipText(recap.tip), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Start)
        Button(onClick = onDone, modifier = Modifier.heightIn(min = TouchTarget)) {
            Text(stringResource(Res.string.recap_done))
        }
    }
}

@Composable
private fun RecapColumn(
    title: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    OutlinedCard(
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}

/** The tip's sentence, built from the typed [tip]; a line saying nothing stood out when there is none. */
@Composable
private fun tipText(tip: RecapTip?): String =
    when {
        tip == null -> stringResource(Res.string.recap_tip_none)
        tip.bars.isSingleBar() -> stringResource(Res.string.recap_tip_bar, tip.passageTitle, tip.bars.first)
        else -> stringResource(Res.string.recap_tip_bars, tip.passageTitle, tip.bars.first, tip.bars.last)
    }

private fun BarRange.isSingleBar(): Boolean = first == last

/** "Leave today's session?": [onKeepGoing] dismisses it, [onLeave] leaves. Finished steps stay done. */
@Composable
public fun LeaveSessionDialog(
    onKeepGoing: () -> Unit,
    onLeave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepGoing,
        title = { Text(stringResource(Res.string.leave_title)) },
        text = { Text(stringResource(Res.string.leave_text)) },
        confirmButton = { TextButton(onClick = onLeave) { Text(stringResource(Res.string.leave_leave)) } },
        dismissButton = { TextButton(onClick = onKeepGoing) { Text(stringResource(Res.string.leave_keep)) } },
    )
}
