package pianoanchor.feature.practice

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.BarRange
import pianoanchor.engine.Tunables
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_list_join
import pianoanchor.feature.practice.resources.practice_spot_clean_bar
import pianoanchor.feature.practice.resources.practice_spot_clean_bars
import pianoanchor.feature.practice.resources.practice_spot_keep_looping
import pianoanchor.feature.practice.resources.practice_spot_play_it
import pianoanchor.feature.practice.resources.practice_spot_result_clean
import pianoanchor.feature.practice.resources.practice_spot_result_not_clean
import pianoanchor.feature.practice.resources.practice_spot_subtitle_bar
import pianoanchor.feature.practice.resources.practice_spot_subtitle_bars
import pianoanchor.feature.practice.resources.results_loop_bar
import pianoanchor.feature.practice.resources.results_loop_bars
import pianoanchor.feature.practice.resources.results_pitch_incomplete_chords
import pianoanchor.feature.practice.resources.results_pitch_wrong_notes

/** [one] for a single bar (formatted with its number), else [many] (first and last bar); [extra] follows either. */
@Composable
private fun barsText(
    bars: BarRange,
    one: StringResource,
    many: StringResource,
    vararg extra: String,
): String =
    if (bars.bars == 1) {
        stringResource(one, bars.first.toString(), *extra)
    } else {
        stringResource(many, bars.first.toString(), bars.last.toString(), *extra)
    }

/** The Results action: "Loop bars 5–6", or "Loop bar 3" for one bar. */
@Composable
internal fun loopBarsLabel(bars: BarRange): String =
    barsText(bars, Res.string.results_loop_bar, Res.string.results_loop_bars)

/** The practice subtitle of a spot loop: "Spot loop · bars 5–6 · clean 0 of 2". */
@Composable
internal fun spotSubtitle(loop: SpotLoopState): String =
    barsText(
        loop.bars,
        Res.string.practice_spot_subtitle_bar,
        Res.string.practice_spot_subtitle_bars,
        loop.cleanLoops.toString(),
        Tunables.SPOT_LOOP_CLEAN_TARGET.toString(),
    )

/** The short line after a loop: "Clean", or its slips ("2 wrong notes"); "Not clean" when only timing was off. */
@Composable
internal fun loopResultText(result: LoopResult): String {
    val slips =
        listOfNotNull(
            slip(Res.plurals.results_pitch_wrong_notes, result.wrongNotes),
            slip(Res.plurals.results_pitch_incomplete_chords, result.incompleteChords),
        )
    return when {
        result.clean -> stringResource(Res.string.practice_spot_result_clean)
        slips.isEmpty() -> stringResource(Res.string.practice_spot_result_not_clean)
        else -> slips.joinToString(stringResource(Res.string.practice_list_join))
    }
}

/** The result line of the last loop, announced to TalkBack when it changes. */
@Composable
internal fun LoopResultLine(
    result: LoopResult,
    modifier: Modifier = Modifier,
) {
    Text(
        loopResultText(result),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.labelLarge,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The card after enough clean loops: offers the whole piece ([onPlayIt]) or more loops ([onKeepLooping]). */
@Composable
internal fun SpotCleanCard(
    bars: BarRange,
    onPlayIt: () -> Unit,
    onKeepLooping: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = barsText(bars, Res.string.practice_spot_clean_bar, Res.string.practice_spot_clean_bars)
    MessageCard(
        message,
        CardAction(stringResource(Res.string.practice_spot_play_it), onPlayIt),
        CardAction(stringResource(Res.string.practice_spot_keep_looping), onKeepLooping),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
