package pianoanchor.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.Tunables
import pianoanchor.engine.calibration.CalibrationResult
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.calibration_accepted_after
import pianoanchor.feature.practice.resources.calibration_accepted_before
import pianoanchor.feature.practice.resources.calibration_cancel
import pianoanchor.feature.practice.resources.calibration_counter
import pianoanchor.feature.practice.resources.calibration_counter_description
import pianoanchor.feature.practice.resources.calibration_done
import pianoanchor.feature.practice.resources.calibration_get_ready
import pianoanchor.feature.practice.resources.calibration_intro
import pianoanchor.feature.practice.resources.calibration_out_of_range
import pianoanchor.feature.practice.resources.calibration_play_now
import pianoanchor.feature.practice.resources.calibration_retry
import pianoanchor.feature.practice.resources.calibration_save_failed
import pianoanchor.feature.practice.resources.calibration_spread
import pianoanchor.feature.practice.resources.calibration_start
import pianoanchor.feature.practice.resources.calibration_title
import pianoanchor.feature.practice.resources.calibration_too_few
import pianoanchor.feature.practice.resources.calibration_try_again
import pianoanchor.feature.practice.resources.calibration_uneven
import pianoanchor.feature.practice.resources.practice_disconnected
import pianoanchor.ui.shell.BackButton
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors
import kotlin.math.abs
import kotlin.math.roundToInt

private const val NANOS_PER_MILLI = 1_000_000.0
private const val MILLIS_PER_SECOND = 1_000.0

/** The largest offset a calibration stores, in seconds, as the "more than 0.4 s" line says it. */
private val MAX_OFFSET_SECONDS = Tunables.CALIBRATION_MAX_OFFSET_MS / MILLIS_PER_SECOND

/**
 * The calibration screen (Living Studio, `docs/ui/living-studio.md`): the title and instruction, then Start, the pulse
 * with the tap counter while the clicks play, and the result with Done (accepted), Retry (accepted but not saved),
 * Try again and Cancel. Stateless: see [CalibrationViewModel]. [onCancel] is also the back button; [onRetrySave]
 * stores the accepted offset again; [onDone] leaves after an accepted result.
 */
@Composable
public fun CalibrationScreen(
    state: CalibrationState,
    onStart: () -> Unit,
    onRetrySave: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.pianoAnchorColors.page) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackButton(onClick = onCancel)
                Column {
                    Text(
                        stringResource(Res.string.calibration_title),
                        fontFamily = displayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(
                            Res.string.calibration_intro,
                            Tunables.CALIBRATION_CLICKS.toString(),
                            CalibrationState.DURATION_SECONDS.toString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.disconnected) {
                MessageCard(
                    stringResource(Res.string.practice_disconnected),
                    CardAction(stringResource(Res.string.calibration_cancel), onCancel),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (state.phase) {
                        CalibrationPhase.READY -> PillButton(stringResource(Res.string.calibration_start), onStart)
                        CalibrationPhase.RUNNING -> Running(state)
                        CalibrationPhase.DONE -> Done(state, onStart, onRetrySave, onDone, onCancel)
                    }
                }
            }
        }
    }
}

/** The pulse dot (filled for half a beat after each click), "get ready" or the prompt, and the "7 / 12" counter. */
@Composable
private fun Running(state: CalibrationState) {
    val colors = MaterialTheme.colorScheme
    val counted = state.countedTaps.toString()
    val total = CalibrationState.COUNTED_CLICKS.toString()
    val counterDescription = stringResource(Res.string.calibration_counter_description, counted, total)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier =
                Modifier
                    .size(28.dp)
                    .border(2.dp, colors.primary, CircleShape)
                    .background(if (state.pulse) colors.primary else colors.surface, CircleShape),
        ) {}
        Text(
            stringResource(if (state.warmingUp) Res.string.calibration_get_ready else Res.string.calibration_play_now),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(Res.string.calibration_counter, counted, total),
            fontFamily = displayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = colors.primary,
            modifier = Modifier.semantics { contentDescription = counterDescription },
        )
    }
}

/**
 * The result line (read out as it appears) and the buttons: Done when accepted and saved; Retry first when the save
 * failed; else Try again, Cancel.
 */
@Composable
private fun Done(
    state: CalibrationState,
    onTryAgain: () -> Unit,
    onRetrySave: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val saved = state.result is CalibrationResult.Accepted && !state.saveFailed
    Text(
        resultText(state),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    spreadNanos(state.result)?.let {
        val ms = (it / NANOS_PER_MILLI).roundToInt().toString()
        Text(
            stringResource(Res.string.calibration_spread, ms),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (saved) {
            PillButton(stringResource(Res.string.calibration_done), onDone)
        } else {
            if (state.saveFailed) {
                PillButton(stringResource(Res.string.calibration_retry), onRetrySave)
                PillOutlinedButton(stringResource(Res.string.calibration_try_again), onTryAgain)
            } else {
                PillButton(stringResource(Res.string.calibration_try_again), onTryAgain)
            }
            PillOutlinedButton(stringResource(Res.string.calibration_cancel), onCancel)
        }
    }
}

/** The spread of the taps, when the result has one to show (accepted or too uneven). */
private fun spreadNanos(result: CalibrationResult?): Long? =
    when (result) {
        is CalibrationResult.Accepted -> result.spreadNanos
        is CalibrationResult.TooUneven -> result.spreadNanos
        else -> null
    }

@Composable
private fun resultText(state: CalibrationState): String =
    when (val result = state.result) {
        is CalibrationResult.Accepted -> {
            if (state.saveFailed) {
                stringResource(Res.string.calibration_save_failed)
            } else {
                val ms = (result.offsetNanos / NANOS_PER_MILLI).roundToInt()
                val text = if (ms < 0) Res.string.calibration_accepted_before else Res.string.calibration_accepted_after
                stringResource(text, abs(ms).toString())
            }
        }

        is CalibrationResult.TooUneven -> {
            stringResource(Res.string.calibration_uneven, Tunables.CALIBRATION_MAX_SPREAD_MS.toString())
        }

        is CalibrationResult.TooFewTaps -> {
            stringResource(Res.string.calibration_too_few)
        }

        is CalibrationResult.OutOfRange -> {
            stringResource(Res.string.calibration_out_of_range, MAX_OFFSET_SECONDS.toString())
        }

        // Only a finished run is shown with a result: DONE always has one.
        null -> {
            ""
        }
    }

@Composable
private fun PillButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(22.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PillOutlinedButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(44.dp), shape = RoundedCornerShape(22.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
