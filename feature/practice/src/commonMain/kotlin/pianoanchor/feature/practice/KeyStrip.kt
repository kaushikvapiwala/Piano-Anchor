package pianoanchor.feature.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.MidiNote
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_announce_correct
import pianoanchor.feature.practice.resources.practice_announce_early
import pianoanchor.feature.practice.resources.practice_announce_late
import pianoanchor.feature.practice.resources.practice_announce_wrong
import pianoanchor.feature.practice.resources.practice_keys_label
import pianoanchor.feature.practice.resources.practice_last_key_correct
import pianoanchor.feature.practice.resources.practice_last_key_early
import pianoanchor.feature.practice.resources.practice_last_key_late
import pianoanchor.feature.practice.resources.practice_last_key_on_time
import pianoanchor.feature.practice.resources.practice_last_key_wrong
import pianoanchor.feature.practice.resources.practice_last_key_wrong_timed
import pianoanchor.ui.shell.PianoAnchorColors
import pianoanchor.ui.shell.pianoAnchorColors
import kotlin.math.abs

private val WHITE_KEY = 60.dp to 76.dp
private val WHITE_PITCH = 62.dp
private val BLACK_KEY = 34.dp to 46.dp
private val TARGET_BAND = 18.dp
private const val KEY_COLOR_MS = 150

/**
 * The live key strip: the label row with the last key's verdict, then two octaves of keys (`keyStripRange`). Target
 * keys of the waiting step carry an amber band; held and flashing keys light up correct or wrong.
 */
@Composable
internal fun KeyStrip(
    state: PracticeState,
    modifier: Modifier = Modifier,
) {
    val waiting =
        state.steps
            .getOrNull(state.currentStep)
            ?.takeIf { it.status == StepStatus.WAITING }
            ?.notes
            .orEmpty()
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.practice_keys_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            state.lastKey?.let { LastKeyText(it, waiting, timed = state.rung != TempoRung.WAIT) }
        }
        Keys(state, waiting)
    }
}

/** "E4 · correct, moving on" or "F4 · waiting for E4"; TalkBack announces "Correct" or "Wrong, F4". */
@Composable
private fun LastKeyText(
    key: KeyFlash,
    waiting: Set<MidiNote>,
    timed: Boolean,
) {
    val name = key.note.name
    val ms = key.deviationMs
    val (text, announcement) =
        when {
            !key.correct && timed -> {
                stringResource(Res.string.practice_last_key_wrong_timed, name) to
                    stringResource(Res.string.practice_announce_wrong, name)
            }

            !key.correct -> {
                stringResource(Res.string.practice_last_key_wrong, name, waiting.chordName()) to
                    stringResource(Res.string.practice_announce_wrong, name)
            }

            ms != null -> {
                val late = ms > 0
                val amount = abs(ms).toString()
                stringResource(
                    if (late) Res.string.practice_last_key_late else Res.string.practice_last_key_early,
                    name,
                    amount,
                ) to
                    stringResource(
                        if (late) Res.string.practice_announce_late else Res.string.practice_announce_early,
                        amount,
                    )
            }

            timed -> {
                stringResource(Res.string.practice_last_key_on_time, name) to
                    stringResource(Res.string.practice_announce_correct)
            }

            else -> {
                stringResource(Res.string.practice_last_key_correct, name) to
                    stringResource(Res.string.practice_announce_correct)
            }
        }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            },
    )
}

@Composable
private fun Keys(
    state: PracticeState,
    waiting: Set<MidiNote>,
) {
    val colors = MaterialTheme.pianoAnchorColors
    val notes = keyStripRange(state.steps.flatMap { it.notes }.minBy { it.number }).map(::MidiNote)
    val whites = notes.filterNot { it.isBlack }
    val stripWidth = WHITE_PITCH * whites.size - (WHITE_PITCH - WHITE_KEY.first)
    Box(Modifier.horizontalScroll(rememberScrollState()).size(stripWidth, WHITE_KEY.second)) {
        whites.forEachIndexed { i, note ->
            Key(note, state, note in waiting, colors, WHITE_KEY.first, WHITE_KEY.second, WHITE_PITCH * i, black = false)
        }
        notes.filter { it.isBlack }.forEach { note ->
            val whitesBelow = whites.count { it.number < note.number }
            val x = WHITE_PITCH * whitesBelow - (WHITE_PITCH - WHITE_KEY.first) / 2 - BLACK_KEY.first / 2
            Key(note, state, note in waiting, colors, BLACK_KEY.first, BLACK_KEY.second, x, black = true)
        }
    }
}

@Composable
private fun Key(
    note: MidiNote,
    state: PracticeState,
    target: Boolean,
    colors: PianoAnchorColors,
    width: Dp,
    height: Dp,
    x: Dp,
    black: Boolean,
) {
    val lit = note in state.keysDown || state.flash?.note == note
    val wrong = note in state.wrongKeys || state.flash?.let { it.note == note && !it.correct } == true
    val idle = if (black) colors.blackKey else colors.whiteKey
    val fill by animateColorAsState(
        targetValue =
            when {
                !lit -> idle
                wrong -> colors.wrongKey
                else -> colors.correct
            },
        animationSpec = tween(KEY_COLOR_MS),
    )
    val shape = RoundedCornerShape(bottomStart = if (black) 7.dp else 10.dp, bottomEnd = if (black) 7.dp else 10.dp)
    val name = note.name
    Box(
        modifier =
            Modifier
                .offset(x = x)
                .size(width, height)
                .clip(shape)
                .background(fill)
                .then(if (black) Modifier else Modifier.border(1.dp, colors.keyBorder, shape))
                .semantics { contentDescription = name },
    ) {
        if (target && !lit) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(TARGET_BAND)
                    .background(colors.targetBand),
            )
        }
    }
}
