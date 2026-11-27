package pianoanchor.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.onboarding.resources.Res
import pianoanchor.feature.onboarding.resources.onboarding_placement_header
import pianoanchor.feature.onboarding.resources.onboarding_placement_passed
import pianoanchor.feature.onboarding.resources.onboarding_placement_start_here
import pianoanchor.feature.onboarding.resources.onboarding_placement_stop
import pianoanchor.feature.onboarding.resources.onboarding_start_title
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors

/** The start step's title; after a piece of the placement test, its header and the verdict. */
@Composable
internal fun StartPointLeft(state: OnboardingState) {
    val piece = state.piece
    if (piece == null) {
        Title(Res.string.onboarding_start_title)
        return
    }
    Text(
        pieceHeader(piece),
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        modifier = Modifier.semantics { heading() },
    )
    val verdict =
        when (state.verdict) {
            PlacementVerdict.PASSED -> Res.string.onboarding_placement_passed
            PlacementVerdict.START_HERE -> Res.string.onboarding_placement_start_here
            null -> return
        }
    Text(
        stringResource(verdict),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun pieceHeader(piece: PlacementPiece): String =
    stringResource(
        Res.string.onboarding_placement_header,
        piece.number.toString(),
        piece.total.toString(),
        piece.title,
    )

/** "Piece n of 5 · title" with "Stop here", over the hosted run. */
@Composable
internal fun PlacementRun(
    piece: PlacementPiece,
    onStopHere: () -> Unit,
    modifier: Modifier,
    runSlot: @Composable (PlacementPiece) -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.pianoAnchorColors.page) {
        Column(Modifier.safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pieceHeader(piece),
                    fontFamily = displayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(onClick = onStopHere, modifier = Modifier.heightIn(min = TouchTarget)) {
                    Text(stringResource(Res.string.onboarding_placement_stop))
                }
            }
            Box(Modifier.weight(1f)) { runSlot(piece) }
        }
    }
}
