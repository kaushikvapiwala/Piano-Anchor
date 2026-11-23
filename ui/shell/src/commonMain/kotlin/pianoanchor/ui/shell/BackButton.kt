package pianoanchor.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.ui.shell.resources.Res
import pianoanchor.ui.shell.resources.back
import pianoanchor.ui.shell.resources.back_glyph
import pianoanchor.ui.shell.resources.back_glyph_rtl

private val MinTouchTarget = 44.dp
private val GlyphSize = 20.sp

/**
 * The shared back control: an arrow that reads "Back" to TalkBack, mirrored right to left, at least 44 dp and
 * growing with the font.
 */
@Composable
public fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.back)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    TextButton(
        onClick = onClick,
        modifier =
            modifier
                .sizeIn(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
                .semantics { contentDescription = label },
        // No padding: the glyph sits centred in the 44 dp target, as the fixed-size copies did, and only a large font
        // makes the button grow past it.
        contentPadding = PaddingValues(0.dp),
    ) {
        // The wrapper clears the glyph from semantics: the button already reads "Back".
        Box(Modifier.clearAndSetSemantics {}) {
            Text(
                stringResource(if (rtl) Res.string.back_glyph_rtl else Res.string.back_glyph),
                fontSize = GlyphSize,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
