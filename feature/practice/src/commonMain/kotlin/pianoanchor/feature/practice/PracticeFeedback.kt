package pianoanchor.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.notation.Engraving
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_listen
import pianoanchor.feature.practice.resources.practice_next_key
import pianoanchor.feature.practice.resources.practice_next_key_done
import pianoanchor.feature.practice.resources.practice_play_now
import pianoanchor.feature.practice.resources.practice_restart_missed
import pianoanchor.feature.practice.resources.practice_restart_wrong
import pianoanchor.feature.practice.resources.practice_skip
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.wrongKeyHintText

private val HIDDEN_STAFF_HEIGHT = 72.dp
private val COUNT_IN_SIZE = 96.sp
private const val NUMERAL_BACKDROP_ALPHA = 0.9f

/**
 * The staff or, in Unit 0, a short gap that only speaks: a polite live region reads which key comes next, since there
 * is no staff to describe it.
 */
@Composable
internal fun StaffArea(state: PracticeState) {
    val next = state.nextKeyDescription()
    val staffHeight = remember(state.passage) { staffHeightDp(Engraving.engrave(state.passage)).dp }
    Box(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 6.dp)
            .fillMaxWidth()
            .height(if (state.staffHidden) HIDDEN_STAFF_HEIGHT else staffHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (state.staffHidden) {
            Spacer(
                Modifier.fillMaxSize().semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = next
                },
            )
        } else {
            StaffView(
                passage = state.passage,
                steps = state.shownSteps,
                liveMarks = state.liveMarks,
                timed = state.rung != TempoRung.WAIT,
                legend = state.showsVerdicts,
                modifier = Modifier.fillMaxWidth().height(staffHeight),
            )
        }
    }
}

/** "Waiting for D4." or "Done."; empty while no step waits. */
@Composable
private fun PracticeState.nextKeyDescription(): String =
    when {
        waitingNotes.isNotEmpty() -> stringResource(Res.string.practice_next_key, waitingNotes.chordName())
        steps.all { it.status == StepStatus.DONE } -> stringResource(Res.string.practice_next_key_done)
        else -> ""
    }

/** The landmark line under the staff (a wrong key in wait mode) and the Listen or Skip button. */
@Composable
internal fun HintRow(
    state: PracticeState,
    onListen: () -> Unit,
    onSkipListening: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hint = state.hint
    Row(modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hint != null) {
            Text(
                wrongKeyHintText(hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (state.listening) {
            TextButton(onClick = onSkipListening) { Text(stringResource(Res.string.practice_skip)) }
        } else if (state.showsListen) {
            TextButton(onClick = onListen) { Text(stringResource(Res.string.practice_listen)) }
        }
    }
}

/**
 * The big centred count-in numeral ("3", "2", "1") and "Play!" on the run's first beat; TalkBack announces each. It
 * floats over the screen, so no layout area clips it, whatever the font scale.
 */
@Composable
internal fun CountInNumeral(state: PracticeState) {
    val text =
        state.countInNumeral?.toString()
            ?: stringResource(Res.string.practice_play_now).takeIf { state.phase == RunPhase.PLAYING && state.playNow }
            ?: return
    Text(
        text,
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = COUNT_IN_SIZE,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .background(
                    MaterialTheme.colorScheme.background.copy(alpha = NUMERAL_BACKDROP_ALPHA),
                    RoundedCornerShape(24.dp),
                ).padding(horizontal = 32.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

/** Says why a timed run stopped and starts again: "Wrong note: F4. Again from the top." (a polite live region). */
@Composable
internal fun RestartReasonLine(
    reason: RestartReason,
    modifier: Modifier = Modifier,
) {
    val text =
        when (reason.kind) {
            RestartKind.WRONG -> Res.string.practice_restart_wrong
            RestartKind.MISSED -> Res.string.practice_restart_missed
        }
    Text(
        stringResource(text, reason.note.name),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
