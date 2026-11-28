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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.RunMode
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
import pianoanchor.feature.practice.resources.practice_hint
import pianoanchor.feature.practice.resources.practice_hint_glyph
import pianoanchor.feature.practice.resources.practice_link_resumed
import pianoanchor.feature.practice.resources.practice_mode_perform
import pianoanchor.feature.practice.resources.practice_mode_switch_perform
import pianoanchor.feature.practice.resources.practice_mode_switch_perform_disabled
import pianoanchor.feature.practice.resources.practice_mode_switch_practice
import pianoanchor.feature.practice.resources.practice_mode_tempo
import pianoanchor.feature.practice.resources.practice_mode_wait
import pianoanchor.feature.practice.resources.practice_ready_timed
import pianoanchor.feature.practice.resources.practice_retry
import pianoanchor.feature.practice.resources.practice_save_failed
import pianoanchor.feature.practice.resources.practice_tempo_done
import pianoanchor.feature.practice.resources.practice_tempo_percent
import pianoanchor.feature.practice.resources.practice_tempo_wait
import pianoanchor.feature.practice.resources.practice_too_hard
import pianoanchor.feature.practice.resources.practice_too_hard_keep_trying
import pianoanchor.feature.practice.resources.practice_too_hard_slower
import pianoanchor.ui.shell.BackButton
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors

/**
 * The practice screen (Living Studio mock, `docs/ui/living-studio.md`): top bar with the tempo ladder, the Hint
 * button and the click switch, the staff with the passage and a cursor, in a timed run the count-in and pulse line,
 * and the live key strip.
 * Stateless: see [PracticeViewModel]. [onBack] leaves the run (the back button, "End run" and the disconnected card's
 * "Back to pianos"); [onSelectRung] picks a rung of the tempo ladder (discarding the run); [onToggleClick] mutes or
 * unmutes the metronome; [onToggleBacking] turns the "Chords + click" chip (shown when the passage has a chord line) on
 * or off; [onHint] shows the key band on the waiting step (the
 * lightbulb button); [onRetrySave] and [onDiscard] are the save-failed card's Retry and Discard.
 * A spot loop ([PracticeState.spotLoop]) shows its progress as the subtitle and the last loop's result under the
 * staff; its "bars are clean" card has [onPlayWhole] ("Play it") and [onKeepLooping].
 * Under the staff one line says how to find the right key after a wrong one ("landmark hint"), with the Listen button
 * ([onListen]: the passage plays for the learner) or, while it plays, Skip ([onSkipListening]). A timed run's count-in
 * shows "3, 2, 1, Play!" over the staff; a Unit 0 passage has no staff and a taller key strip.
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
    onHint: () -> Unit = {},
    onSelectMode: (RunMode) -> Unit = {},
    onPlayWhole: () -> Unit = {},
    onKeepLooping: () -> Unit = {},
    onSlower: () -> Unit = {},
    onKeepTrying: () -> Unit = {},
    onListen: () -> Unit = {},
    onSkipListening: () -> Unit = {},
    onToggleBacking: () -> Unit = {},
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxSize()) {
                TopBar(state, onBack, onSelectRung, onToggleClick, onHint, onSelectMode)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (state.hasChords) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                        BackingChip(state.backingOn, onToggleBacking)
                    }
                }
                StatusCards(
                    state,
                    onBack,
                    onRetrySave,
                    onDiscard,
                    onPlayWhole,
                    onKeepLooping,
                    onSlower,
                    onKeepTrying,
                )
                StaffArea(state)
                HintRow(state, onListen, onSkipListening, Modifier.padding(horizontal = 20.dp).fillMaxWidth())
                state.restartReason?.let {
                    RestartReasonLine(
                        it,
                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                if (state.linkResumed) LinkResumedLine(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                if (state.rung != TempoRung.WAIT) {
                    RunStatus(state, Modifier.padding(horizontal = 20.dp).fillMaxWidth())
                }
                state.spotLoop?.lastLoop?.let {
                    LoopResultLine(
                        it,
                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                KeyStrip(
                    state,
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    tall = state.staffHidden,
                )
            }
            CountInNumeral(state)
        }
    }
}

/** The cards above the staff: too hard, disconnected, save failed and the clean spot loop. */
@Composable
private fun StatusCards(
    state: PracticeState,
    onBack: () -> Unit,
    onRetrySave: () -> Unit,
    onDiscard: () -> Unit,
    onPlayWhole: () -> Unit,
    onKeepLooping: () -> Unit,
    onSlower: () -> Unit,
    onKeepTrying: () -> Unit,
) {
    if (state.offerSlower) {
        MessageCard(
            stringResource(Res.string.practice_too_hard),
            CardAction(stringResource(Res.string.practice_too_hard_slower), onSlower),
            CardAction(stringResource(Res.string.practice_too_hard_keep_trying), onKeepTrying),
            modifier = CARD_MODIFIER,
        )
    }
    if (state.connection == PracticeConnection.DISCONNECTED) {
        MessageCard(
            stringResource(Res.string.practice_disconnected),
            CardAction(stringResource(Res.string.practice_back_to_pianos), onBack),
            modifier = CARD_MODIFIER,
        )
    }
    if (state.saveFailed) {
        MessageCard(
            stringResource(Res.string.practice_save_failed),
            CardAction(stringResource(Res.string.practice_retry), onRetrySave),
            CardAction(stringResource(Res.string.practice_discard), onDiscard),
            modifier = CARD_MODIFIER,
        )
    }
    state.spotLoop?.takeIf { it.offerFull }?.let { loop ->
        SpotCleanCard(loop.bars, onPlayWhole, onKeepLooping, modifier = CARD_MODIFIER)
    }
}

/** Says why a timed run started over by itself: the piano's link dropped and came back. */
@Composable
private fun LinkResumedLine(modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.practice_link_resumed),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun TopBar(
    state: PracticeState,
    onBack: () -> Unit,
    onSelectRung: (TempoRung) -> Unit,
    onToggleClick: () -> Unit,
    onHint: () -> Unit,
    onSelectMode: (RunMode) -> Unit,
) {
    val bpm = state.rung.bpm(state.passage)
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackButton(onClick = onBack)
        Column {
            Text(state.passage.title, fontFamily = displayFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                if (state.spotLoop != null) {
                    spotSubtitle(state.spotLoop)
                } else if (bpm == null) {
                    stringResource(Res.string.practice_mode_wait)
                } else if (state.mode == RunMode.PERFORM) {
                    stringResource(Res.string.practice_mode_perform, bpm.toString())
                } else {
                    stringResource(Res.string.practice_mode_tempo, bpm.toString())
                },
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (!state.sightReading) {
            TempoRung.entries.forEach { rung ->
                val label =
                    rung.percent?.let { stringResource(Res.string.practice_tempo_percent, it.toString()) }
                        ?: stringResource(Res.string.practice_tempo_wait)
                TempoChip(label, selected = rung == state.rung, done = rung in state.doneRungs) { onSelectRung(rung) }
            }
        }
        if (state.spotLoop == null && !state.sightReading) ModeSwitch(state, onSelectMode)
        if (state.showsVerdicts && !state.sightReading && !state.unaided) HintButton(onHint)
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

/**
 * The two-segment "Practice | Perform" switch. Perform is disabled on the WAIT rung, where its description says
 * "Pick a tempo to perform".
 */
@Composable
private fun ModeSwitch(
    state: PracticeState,
    onSelectMode: (RunMode) -> Unit,
) {
    val canPerform = state.rung.canPerform
    val practice = stringResource(Res.string.practice_mode_switch_practice)
    val perform = stringResource(Res.string.practice_mode_switch_perform)
    val performDescription =
        if (canPerform) perform else stringResource(Res.string.practice_mode_switch_perform_disabled)
    Row {
        ModeSegment(practice, practice, RunMode.PRACTICE, state.mode, enabled = true, onSelectMode)
        ModeSegment(perform, performDescription, RunMode.PERFORM, state.mode, canPerform, onSelectMode)
    }
}

/** One segment of the mode switch: plum when it is the [current] mode. */
@Composable
private fun ModeSegment(
    label: String,
    description: String,
    mode: RunMode,
    current: RunMode,
    enabled: Boolean,
    onSelectMode: (RunMode) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val selected = mode == current
    val shape =
        if (mode == RunMode.PRACTICE) {
            RoundedCornerShape(topStart = 15.dp, bottomStart = 15.dp)
        } else {
            RoundedCornerShape(topEnd = 15.dp, bottomEnd = 15.dp)
        }
    val textColor =
        when {
            selected -> colors.onPrimary
            enabled -> colors.onSurfaceVariant
            else -> colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
        }
    Pill(
        label = label,
        description = description,
        selected = selected,
        enabled = enabled,
        shape = shape,
        borderColor = if (selected) colors.primary else colors.outline,
        textColor = textColor,
        onClick = { onSelectMode(mode) },
    )
}

/** The 30 dp radio pill behind [ModeSegment] and [TempoChip]: [selected] fills it plum, [minWidth] widens it. */
@Composable
private fun Pill(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    shape: Shape,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    minWidth: Dp = Dp.Unspecified,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .minimumInteractiveComponentSize()
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = description }
                .height(30.dp)
                .widthIn(min = minWidth)
                .border(1.5.dp, borderColor, shape)
                .background(if (selected) colors.primary else colors.background, shape)
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

/** The 44 dp lightbulb button that shows the key band on the waiting step, read as "Show hint". */
@Composable
private fun HintButton(onHint: () -> Unit) {
    val label = stringResource(Res.string.practice_hint)
    TextButton(
        onClick = onHint,
        modifier = Modifier.size(44.dp).semantics { contentDescription = label },
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
    ) {
        Text(stringResource(Res.string.practice_hint_glyph), fontSize = 20.sp)
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
    Pill(
        label = label,
        description = description,
        selected = selected,
        enabled = true,
        shape = shape,
        borderColor = borderColor,
        textColor = textColor,
        onClick = onClick,
        minWidth = 44.dp,
    )
}

/**
 * The timed run's status line: "Get ready…" while arming, then the beat pulse (one dot per beat of the
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
                // Paused after a mistake the reason line speaks, and the too-hard card asks: no "Get ready" then.
                if (state.restartReason == null) {
                    Text(
                        stringResource(Res.string.practice_ready_timed),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
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
                }
            }

            RunPhase.FINISHED -> {
                // Nothing to show: the screen is about to leave for the results.
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.5f

private val CARD_MODIFIER = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()
