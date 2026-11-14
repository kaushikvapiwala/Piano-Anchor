package pianoanchor.feature.practice

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.Band
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_back_to_pianos
import pianoanchor.feature.practice.resources.practice_retry
import pianoanchor.feature.practice.resources.results_caption
import pianoanchor.feature.practice.resources.results_caption_tempo
import pianoanchor.feature.practice.resources.results_headline_clean
import pianoanchor.feature.practice.resources.results_headline_good
import pianoanchor.feature.practice.resources.results_headline_keep_practising
import pianoanchor.feature.practice.resources.results_not_found
import pianoanchor.feature.practice.resources.results_try_again
import pianoanchor.feature.practice.resources.results_unavailable
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors

/** Columns of the mock's tile grid: Pitch, Timing, Note length, Hesitation. */
private const val TILE_COLUMNS = 4

/**
 * The results screen (Living Studio mock, `docs/ui/living-studio.md`): the run's caption and headline, the Pitch
 * tile and "Try again". Stateless: see [ResultsViewModel]. [onBack] leaves for the pianos; [onTryAgain] practises
 * the passage again; [onRetry] reads the run again after [ResultsState.Unavailable].
 */
@Composable
public fun ResultsScreen(
    state: ResultsState,
    onBack: () -> Unit,
    onTryAgain: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.pianoAnchorColors.page) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackButton(onBack)
                if (state is ResultsState.Ready) Header(state)
            }
            when (state) {
                ResultsState.Loading -> {
                    // The read takes a moment: show the back button only, with no spinner (screenshots stay idle).
                }

                ResultsState.NotFound -> {
                    ErrorCard(
                        stringResource(Res.string.results_not_found),
                        CardAction(stringResource(Res.string.practice_back_to_pianos), onBack),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                ResultsState.Unavailable -> {
                    ErrorCard(
                        stringResource(Res.string.results_unavailable),
                        CardAction(stringResource(Res.string.practice_retry), onRetry),
                        CardAction(stringResource(Res.string.practice_back_to_pianos), onBack),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is ResultsState.Ready -> {
                    Ready(state, onTryAgain)
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
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val tiles =
            listOf<(@Composable (Modifier) -> Unit)?>(
                { PitchTile(state, it) },
                state.timing?.let { timing -> { TimingTileView(timing, it) } },
                state.length?.let { length -> { LengthTileView(length, it) } },
                state.hesitation?.let { hesitation -> { HesitationTileView(hesitation, it) } },
            )
        check(tiles.size == TILE_COLUMNS)
        tiles.forEach { tile ->
            if (tile ==
                null
            ) {
                Spacer(Modifier.weight(1f))
            } else {
                tile(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Button(onClick = onTryAgain, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(22.dp)) {
            Text(stringResource(Res.string.results_try_again), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
