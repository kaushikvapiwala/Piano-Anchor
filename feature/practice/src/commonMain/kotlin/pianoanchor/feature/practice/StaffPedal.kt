package pianoanchor.feature.practice

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import pianoanchor.ui.shell.drawPedalLine

/** How far below its staff's bottom line the pedal line runs (clear of the cursor's overhang). */
internal val PEDAL_BELOW = 28.dp

/** The pedal lines of [layout] at [lineY], under the staff the engraving names. */
internal fun DrawScope.drawPedalMarks(
    layout: StaffLayout,
    color: Color,
    lineY: Float,
) = layout.pedal.forEach { line ->
    drawPedalLine(color, lineY, line.x1.dp.toPx(), line.x2.dp.toPx(), line.notchXs.map { it.dp.toPx() })
}
