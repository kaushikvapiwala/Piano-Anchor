package pianoanchor.feature.lesson

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.lesson.NoteLabels
import pianoanchor.engine.lesson.NoteNameRule
import pianoanchor.feature.lesson.resources.Res
import pianoanchor.feature.lesson.resources.lesson_see_your_turn
import pianoanchor.ui.shell.KeyLetters
import pianoanchor.ui.shell.keyLetterColor
import pianoanchor.ui.shell.keyNameText
import pianoanchor.ui.shell.pianoAnchorColors
import pianoanchor.ui.shell.rememberReduceMotion

internal val WHITE_KEY_WIDTH = 24.dp
internal val WHITE_PITCH = 26.dp
private val BLACK_KEY_WIDTH = 16.dp
private val WHITE_KEY_HEIGHT = 76.dp
private val BLACK_KEY_HEIGHT = 46.dp
private val GLOW_BORDER = 3.dp
private val LETTER_BOTTOM = 2.dp
private const val KEY_COLOR_MS = 150
private const val GLOW_ALPHA = 0.55f
private const val NOTES_PER_OCTAVE = 12
private const val MIN_SPAN = 24
private const val LOWEST_KEY = 21
private const val HIGHEST_KEY = 108
private const val MIDDLE_C = 60
private val BLACK_PITCH_CLASSES = setOf(1, 3, 6, 8, 10)

private val MidiNote.isBlack: Boolean get() = number % NOTES_PER_OCTAVE in BLACK_PITCH_CLASSES

/**
 * The keys the strip shows for [state]: whole octaves (C to B) around the notes of its Try and Use, at least two, plus
 * the hand position of the concept's card scene (its stage keys when it draws a hand), so the See hand always has its
 * five keys on the strip.
 */
internal fun lessonKeyRange(state: LessonState): IntRange =
    keyRange(state.check, passageNotes(state) + handPosition(state))

private fun handPosition(state: LessonState): List<Int> =
    state.scene
        ?.stage
        ?.takeIf { it.hand != null }
        ?.keys
        ?.let { listOf(it.first, it.last) }
        .orEmpty()

/** The keys a strip shows for [check] and any [extra] notes: whole octaves (C to B) around them, at least two. */
internal fun keyRange(
    check: TryCheck?,
    extra: List<Int> = emptyList(),
): IntRange {
    val notes =
        when (check) {
            null, TryCheck.AnyKey -> listOf(MIDDLE_C)
            is TryCheck.PlayKey -> listOf(check.note)
            is TryCheck.FindAll -> listOf(check.range.first, check.range.last)
            is TryCheck.Sequence -> check.notes
            is TryCheck.Repeat -> listOf(check.note)
            is TryCheck.Hold -> listOf(check.note)
            is TryCheck.SoftThenLoud -> listOf(check.note)
            is TryCheck.StressBeatOne -> listOf(check.note)
            is TryCheck.Rhythm -> listOf(check.note)
            is TryCheck.Chord -> check.notes.toList()
            is TryCheck.Legato, is TryCheck.PedalHold, is TryCheck.PedalChange -> pedalCheckNotes(check)
            is TryCheck.Passage -> emptyList()
        } + extra
    var low = notes.min() / NOTES_PER_OCTAVE * NOTES_PER_OCTAVE
    val high = notes.max() / NOTES_PER_OCTAVE * NOTES_PER_OCTAVE + NOTES_PER_OCTAVE - 1
    if (high - low + 1 < MIN_SPAN) low -= NOTES_PER_OCTAVE
    return low.coerceAtLeast(LOWEST_KEY)..high.coerceAtMost(HIGHEST_KEY)
}

/** The notes of a legato or pedal check. */
private fun pedalCheckNotes(check: TryCheck): List<Int> =
    when (check) {
        is TryCheck.Legato -> check.notes
        is TryCheck.PedalChange -> check.chords.flatten()
        else -> (check as TryCheck.PedalHold).chord.toList()
    }

/** The pitches of the Use passage when the Try is that passage; the Use step lights nothing else. */
private fun passageNotes(state: LessonState): List<Int> =
    if (state.tryMergedIntoUse) {
        state.usePassage
            ?.steps
            ?.flatMap { step -> step.pitches.map { it.number } }
            .orEmpty()
    } else {
        emptyList()
    }

/**
 * The key strip along the bottom: keys light while they sound or are held, the key of a wrong press shows grey and
 * the expected key glows in the accent ([LessonState.glowKeys]). In See, the demo's hand ([LessonState.seeHand]) plays
 * over the keys, then fades and "Your turn" shows; the strip's description names the fingers and notes. [reduceMotion]
 * keeps the hand still (only the pressed finger's fill and badge change); [fade] is its hold and fade after the demo.
 */
@Composable
internal fun LessonKeyStrip(
    state: LessonState,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
    fade: HandFade =
        rememberHandFade(state.seeHand != null && state.step == LessonStep.SEE, state.playing, reduceMotion),
) {
    val wrong = state.tryProgress?.lastWrong.takeIf { state.step == LessonStep.TRY }
    val timeline = state.seeHand.takeIf { state.step == LessonStep.SEE }
    val overlay =
        timeline
            ?.let { HandOverlay(it, state.seeElapsedMs, state.playing, fade.alpha, reduceMotion) }
            .takeUnless { fade.cue }
    val description = timeline?.let { seeFingersDescription(it) }
    Box(modifier.then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })) {
        KeyStrip(
            lessonKeyRange(state),
            state.litKeys,
            state.glowKeys,
            wrong,
            labels = state.noteLabels,
            hand = overlay,
            handSpace = if (timeline == null) 0.dp else HAND_SPACE,
        )
        if (fade.cue) {
            Text(
                stringResource(Res.string.lesson_see_your_turn),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * A strip of the keys in [range]: [litKeys] light up, [glowKeys] glow in the accent and the [wrong] key shows grey.
 * [labels] decide the letters: the glowing keys are the ones to play, and a wrong key letters itself and them. A
 * [hand] is drawn over the keys in the [handSpace] above them.
 */
@Composable
internal fun KeyStrip(
    range: IntRange,
    litKeys: Set<Int>,
    glowKeys: Set<Int>,
    wrong: Int?,
    modifier: Modifier = Modifier,
    labels: NoteLabels = NoteLabels(),
    hand: HandOverlay? = null,
    handSpace: Dp = 0.dp,
) {
    val notes = range.map(::MidiNote)
    // The hint shows only while the check is live (a glow); a stale wrong key letters nothing.
    val hinted = if (wrong == null || glowKeys.isEmpty()) emptySet() else glowKeys + wrong
    val whites = notes.filterNot { it.isBlack }
    val width = WHITE_PITCH * whites.size - (WHITE_PITCH - WHITE_KEY_WIDTH)
    Box(modifier.horizontalScroll(rememberScrollState()).size(width, WHITE_KEY_HEIGHT + handSpace)) {
        Box(Modifier.align(Alignment.BottomStart).size(width, WHITE_KEY_HEIGHT)) {
            whites.forEachIndexed { i, note ->
                Key(
                    note,
                    litKeys,
                    glowKeys,
                    wrong,
                    labels.label(note.number, glowKeys, hinted)?.let { keyNameText(it) },
                    WHITE_KEY_WIDTH,
                    WHITE_KEY_HEIGHT,
                    WHITE_PITCH * i,
                    black = false,
                )
            }
            notes.filter { it.isBlack }.forEach { note ->
                val whitesBelow = whites.count { it.number < note.number }
                val x = WHITE_PITCH * whitesBelow - (WHITE_PITCH - WHITE_KEY_WIDTH) / 2 - BLACK_KEY_WIDTH / 2
                Key(
                    note,
                    litKeys,
                    glowKeys,
                    wrong,
                    labels.label(note.number, glowKeys, hinted)?.let { keyNameText(it) },
                    BLACK_KEY_WIDTH,
                    BLACK_KEY_HEIGHT,
                    x,
                    black = true,
                )
            }
        }
        val thumb = whites.indexOfFirst { it.number == hand?.timeline?.thumbNote?.number }
        if (hand != null && thumb >= 0) SeeHand(hand, thumb)
    }
}

@Composable
private fun Key(
    note: MidiNote,
    litKeys: Set<Int>,
    glowKeys: Set<Int>,
    wrong: Int?,
    letter: String?,
    width: Dp,
    height: Dp,
    x: Dp,
    black: Boolean,
) {
    val colors = MaterialTheme.pianoAnchorColors
    val accent = MaterialTheme.colorScheme.primary
    val idle = if (black) colors.blackKey else colors.whiteKey
    val lit = note.number in litKeys
    val glow = note.number in glowKeys && !lit
    val target =
        when {
            note.number == wrong -> colors.wrongKey
            lit -> colors.correct
            glow -> accent.copy(alpha = GLOW_ALPHA).compositeOver(idle)
            else -> idle
        }
    val fill by animateColorAsState(target, tween(KEY_COLOR_MS))
    val shape = RoundedCornerShape(bottomStart = if (black) 6.dp else 8.dp, bottomEnd = if (black) 6.dp else 8.dp)
    val name = note.name
    val outline =
        when {
            glow -> Modifier.border(GLOW_BORDER, accent, shape)
            black -> Modifier
            else -> Modifier.border(1.dp, colors.keyBorder, shape)
        }
    Box(
        Modifier
            .offset(x = x)
            .size(width, height)
            .clip(shape)
            .background(fill)
            .then(outline)
            .semantics { contentDescription = name },
    ) {
        if (letter != null) {
            KeyLetters(
                letter,
                flatText(note, black),
                keyLetterColor(
                    fill,
                    if (black) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                width,
                Modifier.align(Alignment.BottomCenter).padding(bottom = LETTER_BOTTOM),
            )
        }
    }
}

/** The flat name of black key [note] as text (shown under its sharp name), or null on a white key. */
@Composable
private fun flatText(
    note: MidiNote,
    black: Boolean,
): String? = NoteNameRule.flatLabel(note.number).takeIf { black }?.let { keyNameText(it) }
