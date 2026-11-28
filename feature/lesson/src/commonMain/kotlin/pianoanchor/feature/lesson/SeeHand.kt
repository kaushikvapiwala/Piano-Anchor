package pianoanchor.feature.lesson

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.SceneHand
import pianoanchor.engine.Tunables
import pianoanchor.engine.lesson.HandTimeline
import pianoanchor.feature.lesson.resources.Res
import pianoanchor.feature.lesson.resources.lesson_see_fingers

/** The room above the key strip the See hand needs: the longest finger plus its press and a little air. */
internal val HAND_SPACE = 64.dp

private const val FINGERS = 5
private const val FINGER_WIDTH_SHARE = 0.8f
private const val FINGER_LENGTH_PITCHES = 2.1f
private const val BADGE_SHARE = 0.45f
private const val BADGE_LIFT = 1.3f
private const val FADE_MS = 300
private val BADGE_TEXT = 11.sp
private val BADGE_STROKE = 1.5.dp

/** The hand in See after the demo: its [alpha] while it holds and fades, and whether the "Your turn" [cue] shows. */
internal class HandFade(
    val alpha: Float,
    val cue: Boolean,
)

/**
 * Holds the hand for `HAND_HOLD_AFTER_MS` once the demo has stopped ([playing] false while [hasHand]), fades it out
 * (at once with [reduceMotion]) and then asks for the cue. A new demo brings the hand back.
 */
@Composable
internal fun rememberHandFade(
    hasHand: Boolean,
    playing: Boolean,
    reduceMotion: Boolean,
): HandFade {
    val alpha = remember { Animatable(1f) }
    var cue by remember { mutableStateOf(false) }
    LaunchedEffect(hasHand, playing, reduceMotion) {
        if (!hasHand || playing) {
            alpha.snapTo(1f)
            cue = false
        } else {
            delay(Tunables.HAND_HOLD_AFTER_MS)
            if (reduceMotion) alpha.snapTo(0f) else alpha.animateTo(0f, tween(FADE_MS))
            cue = true
        }
    }
    return HandFade(alpha.value, cue)
}

/** What the strip draws for the hand: the [timeline] at [elapsedMs] into the demo, [pressing] while it sounds. */
internal class HandOverlay(
    val timeline: HandTimeline,
    val elapsedMs: Long,
    val pressing: Boolean,
    val alpha: Float,
    val reduceMotion: Boolean,
)

/** "Fingers 1, 2, 3 play C, D, E": each finger and note of the demo once, in order. */
@Composable
internal fun seeFingersDescription(timeline: HandTimeline): String {
    val pairs = timeline.presses.map { it.finger to it.note }.distinct()
    return stringResource(
        Res.string.lesson_see_fingers,
        pairs.joinToString(", ") { it.first.toString() },
        // A hand plays white keys only, so the name minus its octave is the letter.
        pairs.joinToString(", ") { it.second.name.dropLast(1) },
    )
}

/**
 * The hand over the strip's keys, decorative: five fingers, finger k over the white key k - 1 places from the
 * thumb's key [thumbIndex] (rightwards for the right hand, leftwards for the left). A finger whose press is active
 * sits [Tunables.HAND_PRESS_DEPTH_DP] lower (not with reduced motion), fills darker and gets a solid badge.
 */
@Composable
internal fun SeeHand(
    overlay: HandOverlay,
    thumbIndex: Int,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = BADGE_TEXT, fontWeight = FontWeight.Bold)
    Canvas(modifier.fillMaxSize()) { drawSeeHand(overlay, thumbIndex, measurer, style) }
}

private fun DrawScope.drawSeeHand(
    overlay: HandOverlay,
    thumbIndex: Int,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val pitch = WHITE_PITCH.toPx()
    val keyTop = HAND_SPACE.toPx()
    val direction = if (overlay.timeline.hand == SceneHand.LEFT) -1 else 1
    val down =
        if (overlay.pressing) {
            overlay.timeline.presses
                .filter { overlay.elapsedMs >= it.startMs && overlay.elapsedMs < it.startMs + it.lengthMs }
                .map { it.finger }
                .toSet()
        } else {
            emptySet()
        }
    val width = WHITE_KEY_WIDTH.toPx() * FINGER_WIDTH_SHARE
    for (finger in 1..FINGERS) {
        val pressed = finger in down
        val x = (thumbIndex + direction * (finger - 1)) * pitch + WHITE_KEY_WIDTH.toPx() / 2
        val sink = if (pressed && !overlay.reduceMotion) Tunables.HAND_PRESS_DEPTH_DP.dp.toPx() else 0f
        val tip = Offset(x, keyTop + sink)
        val base = Offset(x, tip.y - pitch * FINGER_LENGTH_PITCHES * FINGER_LENGTHS[finger - 1])
        drawFinger(
            base,
            Offset(x, (base.y + tip.y) / 2),
            tip,
            width,
            if (pressed) HAND_PRESSED_FILL else HAND_FILL,
            overlay.alpha,
        )
        val radius = width * BADGE_SHARE
        drawBadge(finger, Offset(x, tip.y - radius * BADGE_LIFT), radius, pressed, overlay.alpha, measurer, style)
    }
}

/** A finger number: outlined while the finger is up, solid plum while it presses. */
private fun DrawScope.drawBadge(
    finger: Int,
    center: Offset,
    radius: Float,
    pressed: Boolean,
    alpha: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    drawCircle(if (pressed) HAND_OUTLINE.copy(alpha = alpha) else HAND_FILL.copy(alpha = alpha), radius, center)
    drawCircle(HAND_OUTLINE.copy(alpha = alpha), radius, center, style = Stroke(BADGE_STROKE.toPx()))
    val ink = if (pressed) Color.White else HAND_OUTLINE
    val text = measurer.measure(finger.toString(), style.copy(color = ink.copy(alpha = alpha)))
    drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))
}
