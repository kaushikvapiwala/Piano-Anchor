package pianoanchor.feature.practice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.grading.NoteValue
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_list_join
import pianoanchor.feature.practice.resources.results_hesitation_description
import pianoanchor.feature.practice.resources.results_hesitation_label
import pianoanchor.feature.practice.resources.results_hesitation_longest
import pianoanchor.feature.practice.resources.results_hesitation_none
import pianoanchor.feature.practice.resources.results_hesitation_value
import pianoanchor.feature.practice.resources.results_length_description
import pianoanchor.feature.practice.resources.results_length_every_note_right
import pianoanchor.feature.practice.resources.results_length_held_long
import pianoanchor.feature.practice.resources.results_length_held_short
import pianoanchor.feature.practice.resources.results_length_label
import pianoanchor.feature.practice.resources.results_length_value
import pianoanchor.feature.practice.resources.results_note_half
import pianoanchor.feature.practice.resources.results_note_number
import pianoanchor.feature.practice.resources.results_note_quarter
import pianoanchor.feature.practice.resources.results_note_whole
import pianoanchor.feature.practice.resources.results_pitch_description
import pianoanchor.feature.practice.resources.results_pitch_every_note_right
import pianoanchor.feature.practice.resources.results_pitch_incomplete_chords
import pianoanchor.feature.practice.resources.results_pitch_label
import pianoanchor.feature.practice.resources.results_pitch_missed_notes
import pianoanchor.feature.practice.resources.results_pitch_value
import pianoanchor.feature.practice.resources.results_pitch_wrong_notes
import pianoanchor.feature.practice.resources.results_timing_description
import pianoanchor.feature.practice.resources.results_timing_early_at
import pianoanchor.feature.practice.resources.results_timing_in_time
import pianoanchor.feature.practice.resources.results_timing_label
import pianoanchor.feature.practice.resources.results_timing_late_at
import pianoanchor.feature.practice.resources.results_timing_value
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors

private const val TENTHS_PER_SECOND = 10

/** "PITCH", "3 / 3" and what went wrong; TalkBack reads it as one sentence. */
@Composable
internal fun PitchTile(
    state: ResultsState.Ready,
    modifier: Modifier = Modifier,
) {
    val detail = pitchDetail(state)
    Tile(
        label = stringResource(Res.string.results_pitch_label),
        value =
            stringResource(
                Res.string.results_pitch_value,
                state.correctSteps.toString(),
                state.stepCount.toString(),
            ),
        valueColor = MaterialTheme.pianoAnchorColors.correct,
        detail = detail,
        description =
            stringResource(
                Res.string.results_pitch_description,
                state.correctSteps.toString(),
                state.stepCount.toString(),
                detail,
            ),
        modifier = modifier,
    )
}

/** "TIMING", "±31 ms" (orange when any note was off the beat) and "in time" or the worst note. */
@Composable
internal fun TimingTileView(
    timing: TimingTile,
    modifier: Modifier = Modifier,
) {
    val worst = timing.worst
    val detail =
        when {
            worst == null -> stringResource(Res.string.results_timing_in_time)
            worst.late -> stringResource(Res.string.results_timing_late_at, worst.noteNumber.toString())
            else -> stringResource(Res.string.results_timing_early_at, worst.noteNumber.toString())
        }
    val colors = MaterialTheme.pianoAnchorColors
    Tile(
        label = stringResource(Res.string.results_timing_label),
        value = stringResource(Res.string.results_timing_value, timing.meanMs.toString()),
        valueColor = if (timing.offBeat) colors.timing else colors.correct,
        detail = detail,
        description =
            pluralStringResource(
                Res.plurals.results_timing_description,
                timing.meanMs,
                timing.meanMs.toString(),
                detail,
            ),
        modifier = modifier,
    )
}

/** "NOTE LENGTH", "86%" and "every note held right" or the worst note, e.g. "half note held short". */
@Composable
internal fun LengthTileView(
    length: LengthTile,
    modifier: Modifier = Modifier,
) {
    val worst = length.worst
    val detail =
        if (worst == null) {
            stringResource(Res.string.results_length_every_note_right)
        } else {
            val note = noteName(worst)
            stringResource(
                if (worst.tooLong) Res.string.results_length_held_long else Res.string.results_length_held_short,
                note,
            )
        }
    Tile(
        label = stringResource(Res.string.results_length_label),
        value = stringResource(Res.string.results_length_value, length.heldRightPercent.toString()),
        valueColor = MaterialTheme.pianoAnchorColors.correct,
        detail = detail,
        description =
            stringResource(Res.string.results_length_description, length.heldRightPercent.toString(), detail),
        modifier = modifier,
    )
}

/** "half note" for a half note, else "note 4". */
@Composable
private fun noteName(worst: LengthWorst): String =
    when (worst.value) {
        NoteValue.WHOLE -> stringResource(Res.string.results_note_whole)
        NoteValue.HALF -> stringResource(Res.string.results_note_half)
        NoteValue.QUARTER -> stringResource(Res.string.results_note_quarter)
        null -> stringResource(Res.string.results_note_number, worst.noteNumber.toString())
    }

/** "HESITATION", "1 pause" or "no pauses", and the longest pause: "1.4 s before D4". */
@Composable
internal fun HesitationTileView(
    hesitation: HesitationTile,
    modifier: Modifier = Modifier,
) {
    val value =
        if (hesitation.count == 0) {
            stringResource(Res.string.results_hesitation_none)
        } else {
            pluralStringResource(Res.plurals.results_hesitation_value, hesitation.count, hesitation.count.toString())
        }
    val longest = hesitation.longest
    val detail =
        longest?.let {
            val seconds = "${it.tenthsOfSecond / TENTHS_PER_SECOND}.${it.tenthsOfSecond % TENTHS_PER_SECOND}"
            stringResource(Res.string.results_hesitation_longest, seconds, it.noteName)
        }
    val description =
        stringResource(
            Res.string.results_hesitation_description,
            listOfNotNull(value, detail).joinToString(stringResource(Res.string.practice_list_join)),
        )
    Tile(
        label = stringResource(Res.string.results_hesitation_label),
        value = value,
        valueColor = MaterialTheme.pianoAnchorColors.correct,
        detail = detail,
        description = description,
        modifier = modifier,
    )
}

/** One tile of the grid: a [label], a big coloured [value] and a [detail] line (none when null). */
@Composable
private fun Tile(
    label: String,
    value: String,
    valueColor: Color,
    detail: String?,
    description: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.pianoAnchorColors.tile,
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                fontFamily = displayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = valueColor,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "every note right", or the slips that are not zero: "1 wrong note", "2 wrong notes, 1 chord incomplete". */
@Composable
private fun pitchDetail(state: ResultsState.Ready): String {
    val slips =
        listOfNotNull(
            slip(Res.plurals.results_pitch_wrong_notes, state.wrongNotes),
            slip(Res.plurals.results_pitch_missed_notes, state.missedNotes),
            slip(Res.plurals.results_pitch_incomplete_chords, state.incompleteChords),
        )
    return if (slips.isEmpty()) {
        stringResource(Res.string.results_pitch_every_note_right)
    } else {
        slips.joinToString(stringResource(Res.string.practice_list_join))
    }
}

/** "[count] …" from [plural], or null when [count] is zero. */
@Composable
private fun slip(
    plural: PluralStringResource,
    count: Int,
): String? = if (count > 0) pluralStringResource(plural, count, count.toString()) else null
