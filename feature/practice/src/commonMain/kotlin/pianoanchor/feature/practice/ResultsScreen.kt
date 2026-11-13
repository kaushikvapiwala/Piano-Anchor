package pianoanchor.feature.practice

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.Band
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_back
import pianoanchor.feature.practice.resources.practice_list_join
import pianoanchor.feature.practice.resources.results_caption
import pianoanchor.feature.practice.resources.results_headline_clean
import pianoanchor.feature.practice.resources.results_headline_good
import pianoanchor.feature.practice.resources.results_headline_keep_practising
import pianoanchor.feature.practice.resources.results_not_found
import pianoanchor.feature.practice.resources.results_pitch_description
import pianoanchor.feature.practice.resources.results_pitch_every_note_right
import pianoanchor.feature.practice.resources.results_pitch_incomplete_chords
import pianoanchor.feature.practice.resources.results_pitch_label
import pianoanchor.feature.practice.resources.results_pitch_value
import pianoanchor.feature.practice.resources.results_pitch_wrong_notes
import pianoanchor.feature.practice.resources.results_try_again
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors

/** Columns of the mock's tile grid; Phase 1 fills only the first (Pitch). */
private const val TILE_COLUMNS = 4

/**
 * The results screen (Living Studio mock, `docs/ui/living-studio.md`): the run's caption and headline, the Pitch
 * tile and "Try again". Stateless: see [ResultsViewModel]. [onBack] leaves for the pianos; [onTryAgain] practises
 * the passage again.
 */
@Composable
public fun ResultsScreen(
    state: ResultsState,
    onBack: () -> Unit,
    onTryAgain: () -> Unit,
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
                        message = stringResource(Res.string.results_not_found),
                        action = stringResource(Res.string.practice_back),
                        onAction = onBack,
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
            stringResource(Res.string.results_caption, state.passageTitle, state.runNumber.toString()),
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PitchTile(state, Modifier.weight(1f))
        repeat(TILE_COLUMNS - 1) { Spacer(Modifier.weight(1f)) }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Button(onClick = onTryAgain, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(22.dp)) {
            Text(stringResource(Res.string.results_try_again), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** "PITCH", "3 / 3" and what went wrong; TalkBack reads it as one sentence. */
@Composable
private fun PitchTile(
    state: ResultsState.Ready,
    modifier: Modifier = Modifier,
) {
    val detail = pitchDetail(state)
    val description =
        stringResource(
            Res.string.results_pitch_description,
            state.correctSteps.toString(),
            state.stepCount.toString(),
            detail,
        )
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.pianoAnchorColors.tile,
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                stringResource(Res.string.results_pitch_label),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    Res.string.results_pitch_value,
                    state.correctSteps.toString(),
                    state.stepCount.toString(),
                ),
                fontFamily = displayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.pianoAnchorColors.correct,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "every note right", or the slips that are not zero: "1 wrong note", "2 wrong notes, 1 chord incomplete". */
@Composable
private fun pitchDetail(state: ResultsState.Ready): String {
    val wrong = state.wrongNotes
    val incomplete = state.incompleteChords
    val slips =
        listOfNotNull(
            if (wrong >
                0
            ) {
                pluralStringResource(Res.plurals.results_pitch_wrong_notes, wrong, wrong.toString())
            } else {
                null
            },
            if (incomplete > 0) {
                pluralStringResource(Res.plurals.results_pitch_incomplete_chords, incomplete, incomplete.toString())
            } else {
                null
            },
        )
    return if (slips.isEmpty()) {
        stringResource(Res.string.results_pitch_every_note_right)
    } else {
        slips.joinToString(stringResource(Res.string.practice_list_join))
    }
}
