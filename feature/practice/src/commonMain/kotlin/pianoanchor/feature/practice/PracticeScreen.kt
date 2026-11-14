package pianoanchor.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_back_to_pianos
import pianoanchor.feature.practice.resources.practice_beat
import pianoanchor.feature.practice.resources.practice_click_glyph
import pianoanchor.feature.practice.resources.practice_click_off
import pianoanchor.feature.practice.resources.practice_click_on
import pianoanchor.feature.practice.resources.practice_count_in
import pianoanchor.feature.practice.resources.practice_discard
import pianoanchor.feature.practice.resources.practice_disconnected
import pianoanchor.feature.practice.resources.practice_end_run
import pianoanchor.feature.practice.resources.practice_mode_tempo
import pianoanchor.feature.practice.resources.practice_mode_wait
import pianoanchor.feature.practice.resources.practice_ready_timed
import pianoanchor.feature.practice.resources.practice_retry
import pianoanchor.feature.practice.resources.practice_save_failed
import pianoanchor.feature.practice.resources.practice_tempo_done
import pianoanchor.feature.practice.resources.practice_tempo_percent
import pianoanchor.feature.practice.resources.practice_tempo_wait
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors

/**
 * The practice screen (Living Studio mock, `docs/ui/living-studio.md`): top bar with the tempo ladder and the click
 * switch, the staff with the passage and a cursor, in a timed run the count-in and pulse line, and the live key strip.
 * Stateless: see [PracticeViewModel]. [onBack] leaves the run (the back button, "End run" and the disconnected card's
 * "Back to pianos"); [onSelectRung] picks a rung of the tempo ladder (discarding the run); [onToggleClick] mutes or
 * unmutes the metronome; [onRetrySave] and [onDiscard] are the save-failed card's Retry and Discard.
 */
@Composable
public fun PracticeScreen(
    state: PracticeState,
    onBack: () -> Unit,
    onSelectRung: (TempoRung) -> Unit,
    onToggleClick: () -> Unit,
    onRetrySave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.safeDrawingPadding()) {
            TopBar(state, onBack, onSelectRung, onToggleClick)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (state.connection == PracticeConnection.DISCONNECTED) {
                ErrorCard(
                    stringResource(Res.string.practice_disconnected),
                    CardAction(stringResource(Res.string.practice_back_to_pianos), onBack),
                    modifier = CARD_MODIFIER,
                )
            }
            if (state.saveFailed) {
                ErrorCard(
                    stringResource(Res.string.practice_save_failed),
                    CardAction(stringResource(Res.string.practice_retry), onRetrySave),
                    CardAction(stringResource(Res.string.practice_discard), onDiscard),
                    modifier = CARD_MODIFIER,
                )
            }
            StaffView(
                passage = state.passage,
                steps = state.steps,
                liveMarks = state.liveMarks,
                timed = state.rung != TempoRung.WAIT,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp).fillMaxWidth().height(196.dp),
            )
            if (state.rung != TempoRung.WAIT) RunStatus(state, Modifier.padding(horizontal = 20.dp).fillMaxWidth())
            KeyStrip(state, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth())
        }
    }
}

@Composable
private fun TopBar(
    state: PracticeState,
    onBack: () -> Unit,
    onSelectRung: (TempoRung) -> Unit,
    onToggleClick: () -> Unit,
) {
    val bpm = state.rung.bpm(state.passage)
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackButton(onBack)
        Column {
            Text(state.passage.title, fontFamily = displayFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                if (bpm == null) {
                    stringResource(Res.string.practice_mode_wait)
                } else {
                    stringResource(Res.string.practice_mode_tempo, bpm.toString())
                },
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        TempoRung.entries.forEach { rung ->
            val label =
                rung.percent?.let { stringResource(Res.string.practice_tempo_percent, it.toString()) }
                    ?: stringResource(Res.string.practice_tempo_wait)
            TempoChip(label, selected = rung == state.rung, done = rung in state.doneRungs) { onSelectRung(rung) }
        }
        ClickButton(state.clickOn, onToggleClick)
        Button(
            onClick = onBack,
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(18.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Text(stringResource(Res.string.practice_end_run), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The 44 dp metronome switch: a note glyph, struck through while off; read as "Click on" or "Click off". */
@Composable
private fun ClickButton(
    clickOn: Boolean,
    onToggle: () -> Unit,
) {
    val label = stringResource(if (clickOn) Res.string.practice_click_on else Res.string.practice_click_off)
    TextButton(
        onClick = onToggle,
        modifier = Modifier.size(44.dp).semantics { contentDescription = label },
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
    ) {
        Text(
            stringResource(Res.string.practice_click_glyph),
            fontSize = 20.sp,
            textDecoration = if (clickOn) TextDecoration.None else TextDecoration.LineThrough,
            color = if (clickOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One rung of the tempo ladder: plum when [selected], blue ink when [done] (also said in its description). */
@Composable
private fun TempoChip(
    label: String,
    selected: Boolean,
    done: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val doneColor = MaterialTheme.pianoAnchorColors.correct
    val description = if (done) stringResource(Res.string.practice_tempo_done, label) else label
    val shape = RoundedCornerShape(15.dp)
    val borderColor =
        when {
            selected -> colors.primary
            done -> doneColor
            else -> colors.outline
        }
    val textColor =
        when {
            selected -> colors.onPrimary
            done -> doneColor
            else -> colors.onSurfaceVariant
        }
    Box(
        modifier =
            Modifier
                .minimumInteractiveComponentSize()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = description }
                .height(30.dp)
                .widthIn(min = 44.dp)
                .border(1.5.dp, borderColor, shape)
                .background(if (selected) colors.primary else colors.background, shape)
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

/**
 * The timed run's status line: "Play any key to count in" before the run, then the beat pulse (one dot per beat of the
 * bar, the current one filled) with the count while counting in.
 */
@Composable
private fun RunStatus(
    state: PracticeState,
    modifier: Modifier = Modifier,
) {
    val beats = state.passage.timeSignature.beats
    val colors = MaterialTheme.colorScheme
    val beatText = stringResource(Res.string.practice_beat, state.beat.toString(), beats.toString())
    Row(
        modifier.height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (state.phase) {
            RunPhase.READY -> {
                Text(
                    stringResource(Res.string.practice_ready_timed),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                )
            }

            RunPhase.COUNT_IN, RunPhase.PLAYING -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { contentDescription = beatText },
                ) {
                    repeat(beats) { i ->
                        val on = i + 1 == state.beat
                        Box(
                            Modifier
                                .size(if (i == 0) 16.dp else 12.dp)
                                .border(1.5.dp, colors.primary, CircleShape)
                                .background(if (on) colors.primary else colors.background, CircleShape),
                        )
                    }
                }
                if (state.phase == RunPhase.COUNT_IN) {
                    Text(
                        stringResource(Res.string.practice_count_in),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                    if (state.beat > 0) {
                        Text(
                            state.beat.toString(),
                            fontFamily = displayFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = colors.primary,
                        )
                    }
                }
            }

            RunPhase.FINISHED -> {
                // Nothing to show: the screen is about to leave for the results.
            }
        }
    }
}

private val CARD_MODIFIER = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()
