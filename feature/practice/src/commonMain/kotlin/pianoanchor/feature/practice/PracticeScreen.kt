package pianoanchor.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.feature.practice.resources.Res
import pianoanchor.feature.practice.resources.practice_back
import pianoanchor.feature.practice.resources.practice_back_glyph
import pianoanchor.feature.practice.resources.practice_back_to_pianos
import pianoanchor.feature.practice.resources.practice_disconnected
import pianoanchor.feature.practice.resources.practice_end_run
import pianoanchor.feature.practice.resources.practice_mode_wait
import pianoanchor.feature.practice.resources.practice_tempo_disabled
import pianoanchor.feature.practice.resources.practice_tempo_percent
import pianoanchor.feature.practice.resources.practice_tempo_wait
import pianoanchor.ui.shell.displayFontFamily

/** Tempo rungs after Wait, in percent of the written tempo: shown, not available until timing exists. */
private val LATER_TEMPO_RUNGS = listOf(60, 70, 85, 100)

/**
 * The wait-mode practice screen (Living Studio mock, `docs/ui/living-studio.md`): top bar, the staff with the
 * passage and a cursor on the waiting note, and the live key strip. Stateless: see [PracticeViewModel]. [onBack]
 * leaves the run (the back button, "End run" and the disconnected card's "Back to pianos").
 */
@Composable
public fun PracticeScreen(
    state: PracticeState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.safeDrawingPadding()) {
            TopBar(state.passageTitle, onBack)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (state.connection == PracticeConnection.DISCONNECTED) DisconnectedCard(onBack)
            StaffView(
                steps = state.steps,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp).fillMaxWidth().height(196.dp),
            )
            KeyStrip(state, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth())
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
) {
    val backLabel = stringResource(Res.string.practice_back)
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp).semantics { contentDescription = backLabel },
            contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        ) {
            Text(
                stringResource(Res.string.practice_back_glyph),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column {
            Text(title, fontFamily = displayFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                stringResource(Res.string.practice_mode_wait),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        TempoChip(stringResource(Res.string.practice_tempo_wait), selected = true)
        LATER_TEMPO_RUNGS.forEach { TempoChip(stringResource(Res.string.practice_tempo_percent, it.toString())) }
        Button(
            onClick = onBack,
            modifier = Modifier.height(36.dp),
            shape = RoundedCornerShape(18.dp),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Text(stringResource(Res.string.practice_end_run), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** One rung of the tempo ladder; only the [selected] one (Wait) is available in Phase 1. */
@Composable
private fun TempoChip(
    label: String,
    selected: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val description = if (selected) label else stringResource(Res.string.practice_tempo_disabled, label)
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier =
            Modifier
                .height(30.dp)
                .widthIn(min = 44.dp)
                .border(1.5.dp, if (selected) colors.primary else colors.outline, shape)
                .background(if (selected) colors.primary else colors.background, shape)
                .padding(horizontal = 10.dp)
                .semantics {
                    contentDescription = description
                    this.selected = selected
                    if (!selected) disabled()
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
        )
    }
}

/** The living-studio error card: the piano is gone, the run was discarded. */
@Composable
private fun DisconnectedCard(onBack: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
            Text(stringResource(Res.string.practice_disconnected), modifier = Modifier.weight(1f))
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) {
                Text(stringResource(Res.string.practice_back_to_pianos))
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.6f
