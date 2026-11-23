package pianoanchor.feature.lesson

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import pianoanchor.core.model.SceneAction
import pianoanchor.core.model.SceneHand

private val STROKE = 2.dp
private const val FINGERS = 5
private const val MIDDLE_FINGER = 3
private const val PALM_HEIGHT_SHARE = 0.22f
private const val PALM_OVERLAP_SHARE = 0.02f
private const val PALM_CORNER_SHARE = 0.3f
private const val FINGER_LENGTH_SHARE = 0.28f
private const val CURL_SHORTENING = 0.2f
private const val CURL_BOW = 0.5f
private const val CURL_INWARD = 0.2f

/** Each finger's length relative to the middle finger, thumb to little finger. */
private val FINGER_LENGTHS = listOf(0.7f, 0.9f, 1f, 0.9f, 0.75f)

/**
 * The hand over the keys: a rounded palm and five finger arcs, the same shape in every scene, mirrored for the left
 * hand; a curved pose changes the finger arcs only. A flat pose fading into a curved one blends the two. The hand sits
 * over the key of its pose and shows only while a pose is active; finger numbers sit on the fingertips.
 */
internal fun DrawScope.drawHand(
    hand: SceneHand,
    active: List<ActiveAction>,
    stage: Stage,
    ink: Color,
    palette: ScenePalette,
    measurer: TextMeasurer,
    chipStyle: TextStyle,
) {
    val poses = active.mapNotNull { a -> (a.action as? SceneAction.HandPose)?.let { it to a.alpha } }
    if (poses.isEmpty()) return
    val weight = poses.sumOf { it.second.toDouble() }.toFloat()
    val alpha = minOf(1f, weight)
    val curl = poses.filter { it.first.curved }.sumOf { it.second.toDouble() }.toFloat() / weight
    val over = poses.maxBy { it.second }.first.overNote
    // The thumb (finger 1) sits over the pose's key, the other fingers one white key further each (left: reversed).
    val mirror = if (hand == SceneHand.LEFT) -1f else 1f
    val spacing = stage.whiteWidth
    val palmWidth = spacing * FINGERS
    val centerX = stage.keyRect(over).center.x + (FINGERS / 2) * spacing * mirror
    val palmHeight = stage.size.height * PALM_HEIGHT_SHARE
    val palmBottom = stage.keyboardTop + stage.size.height * PALM_OVERLAP_SHARE
    val stroke = Stroke(STROKE.toPx(), cap = StrokeCap.Round)
    val color = ink.copy(alpha = alpha)
    drawRoundRect(
        color,
        Offset(centerX - palmWidth / 2, palmBottom - palmHeight),
        Size(palmWidth, palmHeight),
        CornerRadius(palmHeight * PALM_CORNER_SHARE),
        stroke,
    )
    val tips = mutableMapOf<Int, Offset>()
    for (finger in 1..FINGERS) {
        val offset = (finger - MIDDLE_FINGER) * spacing * mirror
        val baseX = centerX + offset
        val length = stage.size.height * FINGER_LENGTH_SHARE * FINGER_LENGTHS[finger - 1] * (1 - CURL_SHORTENING * curl)
        val tip = Offset(baseX - offset * CURL_INWARD * curl, palmBottom + length)
        val control = Offset(baseX + offset.sign() * spacing * CURL_BOW * curl, palmBottom + length / 2)
        drawPath(
            Path().apply {
                moveTo(baseX, palmBottom)
                quadraticTo(control.x, control.y, tip.x, tip.y)
            },
            color,
            style = stroke,
        )
        tips[finger] = tip
    }
    active.forEach { a ->
        val finger = (a.action as? SceneAction.ShowFinger)?.finger ?: return@forEach
        drawChip(finger, tips.getValue(finger), a.alpha, stage, palette, measurer, chipStyle)
    }
}

private fun Float.sign(): Float =
    if (this < 0) {
        -1f
    } else if (this > 0) {
        1f
    } else {
        0f
    }
