package pianoanchor.feature.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.RunMode
import pianoanchor.engine.grading.Observation
import pianoanchor.engine.grading.Tip
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_back_to_pianos
import pianoanchor.feature.practice.resources.practice_retry
import pianoanchor.feature.practice.resources.results_caption
import pianoanchor.feature.practice.resources.results_caption_performed
import pianoanchor.feature.practice.resources.results_caption_tempo
import pianoanchor.feature.practice.resources.results_details
import pianoanchor.feature.practice.resources.results_details_collapsed
import pianoanchor.feature.practice.resources.results_details_expanded
import pianoanchor.feature.practice.resources.results_finish_lesson
import pianoanchor.feature.practice.resources.results_headline_clean
import pianoanchor.feature.practice.resources.results_headline_good
import pianoanchor.feature.practice.resources.results_headline_keep_practising
import pianoanchor.feature.practice.resources.results_not_found
import pianoanchor.feature.practice.resources.results_obs_clean
import pianoanchor.feature.practice.resources.results_obs_notes_long
import pianoanchor.feature.practice.resources.results_obs_notes_short
import pianoanchor.feature.practice.resources.results_obs_off_beat
import pianoanchor.feature.practice.resources.results_obs_paused
import pianoanchor.feature.practice.resources.results_obs_paused_unknown
import pianoanchor.feature.practice.resources.results_obs_started_early
import pianoanchor.feature.practice.resources.results_obs_started_late
import pianoanchor.feature.practice.resources.results_obs_wrong_notes
import pianoanchor.feature.practice.resources.results_tip_hold_full_clicks
import pianoanchor.feature.practice.resources.results_tip_let_go_sooner
import pianoanchor.feature.practice.resources.results_tip_listen_to_click
import pianoanchor.feature.practice.resources.results_tip_next_tempo
import pianoanchor.feature.practice.resources.results_tip_practise_the_pause
import pianoanchor.feature.practice.resources.results_tip_slow_down
import pianoanchor.feature.practice.resources.results_tip_wait_for_count_in
import pianoanchor.feature.practice.resources.results_try_again
import pianoanchor.feature.practice.resources.results_unavailable
import pianoanchor.ui.shell.BackButton
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors

/**
 * The results screen (Living Studio mock, `docs/ui/living-studio.md`): the run's caption and headline, the Pitch
 * tile and "Try again". Stateless: see [ResultsViewModel]. [onBack] leaves for the pianos; [onTryAgain] practises
 * the passage again; [onFinishLesson], set only when the run started from a lesson, adds "Finish lesson" (back to
 * the lesson's recap); [onRetry] reads the run again after [ResultsState.Unavailable]; [onLoopBars] starts a spot
 * loop on the bars of [ResultsState.Ready.spot], offered as "Loop bars 5–6" under "Try again" when there are any.
 */
@Composable
public fun ResultsScreen(
    state: ResultsState,
    onBack: () -> Unit,
    onTryAgain: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onLoopBars: (BarRange) -> Unit = {},
    onFinishLesson: (() -> Unit)? = null,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.pianoAnchorColors.page) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackButton(onClick = onBack)
                if (state is ResultsState.Ready) Header(state)
            }
            when (state) {
                ResultsState.Loading -> {
                    // The read takes a moment: show the back button only, with no spinner (screenshots stay idle).
                }

                ResultsState.NotFound -> {
                    MessageCard(
                        stringResource(Res.string.results_not_found),
                        CardAction(stringResource(Res.string.practice_back_to_pianos), onBack),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                ResultsState.Unavailable -> {
                    MessageCard(
                        stringResource(Res.string.results_unavailable),
                        CardAction(stringResource(Res.string.practice_retry), onRetry),
                        CardAction(stringResource(Res.string.practice_back_to_pianos), onBack),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is ResultsState.Ready -> {
                    Ready(state, onTryAgain, onLoopBars, onFinishLesson)
                }
            }
        }
    }
}

@Composable
private fun Header(state: ResultsState.Ready) {
    Column {
        Text(
            caption(state),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(headline(state.band)),
            fontFamily = displayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun caption(state: ResultsState.Ready): String =
    if (state.tempoBpm == null) {
        stringResource(Res.string.results_caption, state.passageTitle, state.runNumber.toString())
    } else if (state.mode == RunMode.PERFORM) {
        stringResource(
            Res.string.results_caption_performed,
            state.passageTitle,
            state.runNumber.toString(),
            state.tempoBpm.toString(),
        )
    } else {
        stringResource(
            Res.string.results_caption_tempo,
            state.passageTitle,
            state.runNumber.toString(),
            state.tempoBpm.toString(),
        )
    }

private fun headline(band: Band): StringResource =
    when (band) {
        Band.CLEAN -> Res.string.results_headline_clean
        Band.GOOD -> Res.string.results_headline_good
        Band.KEEP_PRACTISING -> Res.string.results_headline_keep_practising
    }

@Composable
private fun Ready(
    state: ResultsState.Ready,
    onTryAgain: () -> Unit,
    onLoopBars: (BarRange) -> Unit,
    onFinishLesson: (() -> Unit)?,
) {
    Words(state)
    var expanded by rememberSaveable { mutableStateOf(false) }
    DetailsToggle(expanded) { expanded = !expanded }
    if (expanded) {
        Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val tile = Modifier.weight(1f).fillMaxHeight()
            val blank = Modifier.weight(1f)
            PitchTile(state, tile)
            if (state.timing != null) TimingTileView(state.timing, tile) else Spacer(blank)
            if (state.length != null) LengthTileView(state.length, tile) else Spacer(blank)
            if (state.hesitation != null) HesitationTileView(state.hesitation, tile) else Spacer(blank)
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onFinishLesson != null) {
                Button(onClick = onFinishLesson, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(22.dp)) {
                    Text(
                        stringResource(Res.string.results_finish_lesson),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (onFinishLesson == null) {
                Button(onClick = onTryAgain, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(22.dp)) {
                    Text(
                        stringResource(Res.string.results_try_again),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onTryAgain,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Text(
                        stringResource(Res.string.results_try_again),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            state.spot?.let { spot ->
                OutlinedButton(
                    onClick = { onLoopBars(spot) },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Text(loopBarsLabel(spot), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The observations as body text and the tip as a highlighted line. */
@Composable
private fun Words(state: ResultsState.Ready) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (observation in state.words.observations) {
            Text(observationText(observation, state), style = MaterialTheme.typography.bodyMedium)
        }
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                stringResource(tipText(state.words.tip)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun observationText(
    observation: Observation,
    state: ResultsState.Ready,
): String =
    when (observation) {
        Observation.STARTED_LATE -> {
            val beats = state.shiftBeats
            val shown = if (beats % 1.0 == 0.0) beats.toInt().toString() else beats.toString()
            pluralStringResource(Res.plurals.results_obs_started_late, if (beats == 1.0) 1 else 2, shown)
        }

        Observation.STARTED_EARLY -> {
            stringResource(Res.string.results_obs_started_early)
        }

        Observation.WRONG_NOTES -> {
            val count = state.wrongNotes + state.missedNotes + state.incompleteChords
            pluralStringResource(Res.plurals.results_obs_wrong_notes, count, count)
        }

        Observation.NOTES_SHORT -> {
            stringResource(Res.string.results_obs_notes_short)
        }

        Observation.NOTES_LONG -> {
            stringResource(Res.string.results_obs_notes_long)
        }

        Observation.OFF_BEAT -> {
            stringResource(Res.string.results_obs_off_beat)
        }

        Observation.PAUSED -> {
            val note = state.hesitation?.longest?.noteName
            if (note == null) {
                stringResource(Res.string.results_obs_paused_unknown)
            } else {
                stringResource(Res.string.results_obs_paused, note)
            }
        }

        Observation.CLEAN -> {
            stringResource(Res.string.results_obs_clean)
        }
    }

private fun tipText(tip: Tip): StringResource =
    when (tip) {
        Tip.WAIT_FOR_COUNT_IN -> Res.string.results_tip_wait_for_count_in
        Tip.SLOW_DOWN -> Res.string.results_tip_slow_down
        Tip.HOLD_FULL_CLICKS -> Res.string.results_tip_hold_full_clicks
        Tip.LET_GO_SOONER -> Res.string.results_tip_let_go_sooner
        Tip.LISTEN_TO_CLICK -> Res.string.results_tip_listen_to_click
        Tip.PRACTISE_THE_PAUSE -> Res.string.results_tip_practise_the_pause
        Tip.NEXT_TEMPO -> Res.string.results_tip_next_tempo
    }

/** The "Details" toggle: a 44 dp-high button that reads "Expanded" or "Collapsed" to TalkBack. */
@Composable
private fun DetailsToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val state =
        stringResource(if (expanded) Res.string.results_details_expanded else Res.string.results_details_collapsed)
    Text(
        stringResource(Res.string.results_details),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state }
                .padding(vertical = 12.dp),
    )
}
