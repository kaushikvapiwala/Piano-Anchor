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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
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
import pianoanchor.feature.practice.resources.practice_last_key_wrong_timed
import pianoanchor.ui.shell.PianoAnchorColors
import pianoanchor.ui.shell.pianoAnchorColors
import kotlin.math.abs

private val WHITE_KEY = 60.dp to 76.dp
private val WHITE_PITCH = 62.dp
private val BLACK_KEY = 34.dp to 46.dp
private val TARGET_BAND = 18.dp
private const val KEY_COLOR_MS = 150
private const val GLOW_ALPHA = 0.55f

/** The key heights when the staff is hidden and the strip fills its room. */
private val TALL_WHITE_HEIGHT = 240.dp
private val TALL_BLACK_HEIGHT = 150.dp
private val GLOW_BORDER = 3.dp

/**
 * The live key strip: the label row with the last key's verdict, then two octaves of keys (`keyStripRange`). Target
 * keys of the waiting step carry an amber band while [PracticeState.hintVisible];
 * held and flashing keys light up correct or wrong, or neutral in a perform take.
 */
@Composable
internal fun KeyStrip(
    state: PracticeState,
    modifier: Modifier = Modifier,
    tall: Boolean = false,
) {
    val waiting = state.waitingNotes
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.practice_keys_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            state.lastKey?.let { LastKeyText(it, timed = state.rung != TempoRung.WAIT) }
        }
        Keys(state, if (state.hintVisible) waiting else emptySet(), tall)
    }
}

/**
 * "E4 · correct, moving on"; TalkBack announces "Correct". A wrong key in wait mode says nothing here: the hint line
 * under the staff tells how to find the right one. A wrong timed note reads "F4 · wrong note", announced "Wrong, F4".
 */
@Composable
private fun LastKeyText(
    key: KeyFlash,
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
                return
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
    tall: Boolean,
) {
    val colors = MaterialTheme.pianoAnchorColors
    val notes = keyStripRange(state.steps.flatMap { it.notes }.minBy { it.number }).map(::MidiNote)
    val whites = notes.filterNot { it.isBlack }
    val stripWidth = WHITE_PITCH * whites.size - (WHITE_PITCH - WHITE_KEY.first)
    val whiteHeight = if (tall) TALL_WHITE_HEIGHT else WHITE_KEY.second
    val blackHeight = if (tall) TALL_BLACK_HEIGHT else BLACK_KEY.second
    Box(Modifier.horizontalScroll(rememberScrollState()).size(stripWidth, whiteHeight)) {
        whites.forEachIndexed { i, note ->
            Key(note, state, note in waiting, colors, WHITE_KEY.first, whiteHeight, WHITE_PITCH * i, black = false)
        }
        notes.filter { it.isBlack }.forEach { note ->
            val whitesBelow = whites.count { it.number < note.number }
            val x = WHITE_PITCH * whitesBelow - (WHITE_PITCH - WHITE_KEY.first) / 2 - BLACK_KEY.first / 2
            Key(note, state, note in waiting, colors, BLACK_KEY.first, blackHeight, x, black = true)
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
    val lit = state.isLit(note)
    val glow = note in state.glowKeys && !lit
    val wrong = state.isWrong(note)
    val idle = if (black) colors.blackKey else colors.whiteKey
    val accent = MaterialTheme.colorScheme.primary
    val fill by animateColorAsState(
        targetValue =
            if (glow) {
                accent
                    .copy(
                        alpha = GLOW_ALPHA,
                    ).compositeOver(idle)
            } else {
                litColor(lit, wrong, state.showsVerdicts, idle, colors)
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
                .then(outline(glow, black, accent, colors, shape))
                .semantics { contentDescription = name },
    ) {
        if (target && !lit && !glow) {
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

/** Held, flashing or playing for the learner (hear it first). */
private fun PracticeState.isLit(note: MidiNote) = note in keysDown || flash?.note == note || note in demoKeys

/** Pressed wrong: held since or flashing as wrong. */
private fun PracticeState.isWrong(note: MidiNote) = note in wrongKeys || (flash?.note == note && !flash.correct)

/** A glowing key gets a thick accent outline; a white key its thin border; a black key none. */
private fun outline(
    glow: Boolean,
    black: Boolean,
    accent: Color,
    colors: PianoAnchorColors,
    shape: Shape,
): Modifier =
    when {
        glow -> Modifier.border(GLOW_BORDER, accent, shape)
        black -> Modifier
        else -> Modifier.border(1.dp, colors.keyBorder, shape)
    }

/**
 * A key's fill: [idle] unless [lit]. A perform take lights pressed keys in a neutral colour (the instrument responds,
 * nothing is judged); otherwise a [wrong] key is the wrong colour and the rest correct.
 */
@Composable
private fun litColor(
    lit: Boolean,
    wrong: Boolean,
    verdicts: Boolean,
    idle: Color,
    colors: PianoAnchorColors,
): Color =
    when {
        !lit -> idle
        !verdicts -> MaterialTheme.colorScheme.outline
        wrong -> colors.wrongKey
        else -> colors.correct
    }
