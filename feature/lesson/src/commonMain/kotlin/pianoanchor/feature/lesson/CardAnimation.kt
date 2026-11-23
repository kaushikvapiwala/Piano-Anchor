package pianoanchor.feature.lesson

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import pianoanchor.core.model.CardScene
import pianoanchor.core.model.SceneAction
import pianoanchor.ui.shell.PianoAnchorColors
import pianoanchor.ui.shell.pianoAnchorColors

/** The stage is 16:9, the keyboard the bottom 40% of it. */
private const val STAGE_RATIO = 16f / 9f
private const val KEYBOARD_SHARE = 0.4f
private const val BLACK_WIDTH_SHARE = 0.6f
private const val BLACK_HEIGHT_SHARE = 0.6f
private const val LIT_ALPHA = 0.6f
private const val PULSE_RADIUS_SHARE = 0.045f
private const val ACCENT_PULSE_SHARE = 0.06f
private const val CAPTION_TOP_SHARE = 0.04f
private const val STAGE_CORNER_SHARE = 0.04f
private val STROKE = 2.dp
private val BLACK_PITCH_CLASSES = setOf(1, 3, 6, 8, 10)
private const val SEMITONES = 12

internal fun isBlackKey(note: Int): Boolean = note % SEMITONES in BLACK_PITCH_CLASSES

/**
 * A concept card's looping animation: [scene] on a 16:9 stage, one loop plus its rest, repeating. With [reduceMotion]
 * (system animations off) it holds the frame at the middle of the loop. TalkBack reads the scene's description.
 */
@Composable
internal fun CardAnimation(
    scene: CardScene,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    if (reduceMotion) {
        CardFrame(scene, { CARD_SCENE_MIDPOINT_MS }, modifier)
    } else {
        val clock =
            rememberInfiniteTransition(label = "card scene").animateFloat(
                initialValue = 0f,
                targetValue = CARD_SCENE_CYCLE_MS.toFloat(),
                animationSpec =
                    infiniteRepeatable(
                        tween(CARD_SCENE_CYCLE_MS, easing = LinearEasing),
                        RepeatMode.Restart,
                    ),
                label = "card scene clock",
            )
        // The clock is read inside the draw scope, so a frame redraws the canvas without recomposing.
        CardFrame(scene, { clock.value.toInt() }, modifier)
    }
}

/** One frame of [scene] at the time [timeMs] gives (ms into its cycle), read only when drawing. */
@Composable
internal fun CardFrame(
    scene: CardScene,
    timeMs: () -> Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.pianoAnchorColors
    val palette = ScenePalette(colors, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
    val outline = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val handInk = MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.onSurface
    val captionStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = ink)
    val chipStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = palette.onAccent)
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(STAGE_RATIO)
            .clearAndSetSemantics {
                contentDescription = scene.description
                role = Role.Image
            },
    ) {
        val active = activeActions(scene, timeMs())
        val stage = Stage(size, scene.stage.keys)
        drawRoundRect(colors.tile, cornerRadius = CornerRadius(size.height * STAGE_CORNER_SHARE))
        scene.stage.staff?.let { drawStaff(it, stage, colors.staffLine) }
        drawKeys(stage, colors)
        active.forEach { drawAction(it, scene, stage, palette, outline, ring, measurer, captionStyle, chipStyle) }
        scene.stage.hand?.let { drawHand(it, active, stage, handInk, palette, measurer, chipStyle) }
    }
}

/** The colours of the stage from the theme: the accent and what sits on it, with the practice staff's and keys'. */
internal class ScenePalette(
    val colors: PianoAnchorColors,
    val accent: Color,
    val onAccent: Color,
)

/** The stage's geometry: the keyboard along the bottom 40%, white keys evenly across the width. */
internal class Stage(
    val size: Size,
    val keys: IntRange,
) {
    private val whites = keys.filterNot(::isBlackKey)
    val whiteWidth = size.width / whites.size
    val keyboardTop = size.height * (1 - KEYBOARD_SHARE)
    val blackWidth = whiteWidth * BLACK_WIDTH_SHARE
    val blackHeight = (size.height - keyboardTop) * BLACK_HEIGHT_SHARE

    /** The rectangle of the key [note]. */
    fun keyRect(note: Int): Rect =
        if (isBlackKey(note)) {
            val boundary = whites.count { it < note } * whiteWidth
            Rect(boundary - blackWidth / 2, keyboardTop, boundary + blackWidth / 2, keyboardTop + blackHeight)
        } else {
            Rect(whites.indexOf(note) * whiteWidth, keyboardTop, (whites.indexOf(note) + 1) * whiteWidth, size.height)
        }
}

private fun DrawScope.drawKeys(
    stage: Stage,
    colors: PianoAnchorColors,
) {
    val stroke = Stroke(STROKE.toPx())
    stage.keys.filterNot(::isBlackKey).forEach {
        val rect = stage.keyRect(it)
        drawRect(colors.whiteKey, rect.topLeft, rect.size)
        drawRect(colors.keyBorder, rect.topLeft, rect.size, style = stroke)
    }
    stage.keys.filter(::isBlackKey).forEach {
        val rect = stage.keyRect(it)
        drawRect(colors.blackKey, rect.topLeft, rect.size)
    }
}

private const val CHIP_LIFT = 1.5f

private fun DrawScope.drawLitKey(
    action: SceneAction.LightKey,
    alpha: Float,
    stage: Stage,
    palette: ScenePalette,
    ring: Color,
    measurer: TextMeasurer,
    chipStyle: TextStyle,
) {
    val rect = stage.keyRect(action.note)
    val fill = palette.accent.copy(alpha = alpha * if (action.strong) 1f else LIT_ALPHA)
    drawRect(fill, rect.topLeft, rect.size)
    if (action.strong) drawRect(ring.copy(alpha = alpha), rect.topLeft, rect.size, style = Stroke(STROKE.toPx()))
    action.finger?.let {
        val center = Offset(rect.center.x, rect.bottom - chipRadius(stage) * CHIP_LIFT)
        drawChip(it, center, alpha, stage, palette, measurer, chipStyle)
    }
}

@Suppress("LongParameterList")
private fun DrawScope.drawAction(
    active: ActiveAction,
    scene: CardScene,
    stage: Stage,
    palette: ScenePalette,
    outline: Color,
    ring: Color,
    measurer: TextMeasurer,
    captionStyle: TextStyle,
    chipStyle: TextStyle,
) {
    val alpha = active.alpha
    when (val action = active.action) {
        is SceneAction.LightKey -> {
            drawLitKey(action, alpha, stage, palette, ring, measurer, chipStyle)
        }

        is SceneAction.Highlight -> {
            action.notes.forEach {
                val rect = stage.keyRect(it)
                drawRect(outline.copy(alpha = alpha), rect.topLeft, rect.size, style = Stroke(STROKE.toPx()))
            }
        }

        is SceneAction.Pulse -> {
            val radius = stage.size.height * if (action.accent) ACCENT_PULSE_SHARE else PULSE_RADIUS_SHARE
            val center = Offset(stage.size.width - radius * 2, radius * 2)
            drawCircle(palette.accent.copy(alpha = alpha), radius, center)
            if (action.accent) drawCircle(ring.copy(alpha = alpha), radius, center, style = Stroke(STROKE.toPx()))
        }

        is SceneAction.Caption -> {
            val text =
                measurer.measure(
                    action.text,
                    captionStyle.copy(color = captionStyle.color.copy(alpha = alpha)),
                    TextOverflow.Ellipsis,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = stage.size.width.toInt()),
                )
            drawText(
                text,
                topLeft =
                    Offset(
                        (stage.size.width - text.size.width) / 2,
                        stage.size.height * CAPTION_TOP_SHARE,
                    ),
            )
        }

        is SceneAction.HandPose, is SceneAction.ShowFinger -> {
            // Drawn together by drawHand, which needs every active pose and finger.
        }

        is SceneAction.StaffNote -> {
            drawStaffNote(active.cue, scene, stage, alpha, palette)
        }
    }
}

/** A finger number: an on-accent chip. */
internal fun DrawScope.drawChip(
    finger: Int,
    center: Offset,
    alpha: Float,
    stage: Stage,
    palette: ScenePalette,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    drawCircle(palette.accent.copy(alpha = alpha), chipRadius(stage), center)
    val text = measurer.measure(finger.toString(), style.copy(color = style.color.copy(alpha = alpha)))
    drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))
}

private const val CHIP_RADIUS_SHARE = 0.05f

internal fun chipRadius(stage: Stage): Float = stage.size.height * CHIP_RADIUS_SHARE
