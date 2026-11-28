package pianoanchor.ui.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

private val PEDAL_STROKE = 1.5.dp
private val PEDAL_TICK = 10.dp

/**
 * Draws a pedal mark in the staff's colour and stroke: a horizontal line at [y] from [fromX] to [toX] (pixels), a short
 * upward tick at the start and at the end (each only when asked for), and an inverted-V notch at each of [notchXs],
 * where one pedal span ends and the next starts. The staff and the lesson's See step draw the pedal the same way.
 */
public fun DrawScope.drawPedalLine(
    color: Color,
    y: Float,
    fromX: Float,
    toX: Float,
    notchXs: List<Float> = emptyList(),
    startTick: Boolean = true,
    endTick: Boolean = true,
) {
    val stroke = PEDAL_STROKE.toPx()
    val tick = PEDAL_TICK.toPx()
    var x = fromX
    notchXs.forEach { notch ->
        drawLine(color, Offset(x, y), Offset(notch - tick / 2, y), stroke)
        drawLine(color, Offset(notch - tick / 2, y), Offset(notch, y - tick), stroke)
        drawLine(color, Offset(notch, y - tick), Offset(notch + tick / 2, y), stroke)
        x = notch + tick / 2
    }
    drawLine(color, Offset(x, y), Offset(toX, y), stroke)
    if (startTick) drawLine(color, Offset(fromX, y), Offset(fromX, y - tick), stroke)
    if (endTick) drawLine(color, Offset(toX, y), Offset(toX, y - tick), stroke)
}
