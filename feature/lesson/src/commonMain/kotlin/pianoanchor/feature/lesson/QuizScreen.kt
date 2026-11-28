package pianoanchor.feature.lesson

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.MidiNote
import pianoanchor.engine.guidance.wrongKeyHint
import pianoanchor.engine.lesson.QuizPrompt
import pianoanchor.engine.lesson.TryProgress
import pianoanchor.feature.lesson.resources.Res
import pianoanchor.feature.lesson.resources.lesson_disconnected
import pianoanchor.feature.lesson.resources.lesson_done
import pianoanchor.feature.lesson.resources.lesson_skip
import pianoanchor.feature.lesson.resources.lesson_try_done
import pianoanchor.feature.lesson.resources.lesson_try_progress
import pianoanchor.feature.lesson.resources.lesson_try_wrong_any
import pianoanchor.feature.lesson.resources.quiz_header
import pianoanchor.feature.lesson.resources.quiz_not_saved
import pianoanchor.feature.lesson.resources.quiz_staff_description
import pianoanchor.feature.lesson.resources.quiz_summary
import pianoanchor.feature.lesson.resources.quiz_summary_one
import pianoanchor.ui.shell.BackButton
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors
import pianoanchor.ui.shell.wrongKeyHintText

private const val LEFT_PANE_WEIGHT = 0.3f
private const val RIGHT_PANE_WEIGHT = 0.7f
private val COUNT_IN_SIZE = 56.sp
private val BUTTON_HEIGHT = 44.dp
private val STAFF_WIDTH = 160.dp
private val STAFF_HEIGHT = 96.dp
private val STAFF_STROKE = 2.dp
private const val STAFF_SPAN = TREBLE_HIGH - TREBLE_LOW
private const val NOTEHEAD_WIDTH_UNITS = 3f
private const val NOTEHEAD_HEIGHT_UNITS = 2.2f
private const val NOTEHEAD_ANGLE = -18f
private const val LEDGER_HALF_UNITS = 2.6f

/**
 * The review quizzes, landscape (`docs/ui/living-studio.md`, "Quiz"): a small "Quiz 2 of 3" header on the left, the
 * prompt, the progress and the gentle wrong-key hint on the right, and the key strip along the bottom. "Skip" is
 * always visible; after the last quiz a one-line summary and "Done". Stateless.
 */
@Composable
public fun QuizScreen(
    state: QuizState,
    actions: QuizActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.pianoAnchorColors.page) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.weight(LEFT_PANE_WEIGHT).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BackButton(actions.onBack)
                        Text(
                            stringResource(Res.string.quiz_header, state.number.toString(), state.count.toString()),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = displayFontFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    val prompt = state.quiz?.prompt
                    if (prompt is QuizPrompt.PlayKey && prompt.onStaff) MiniStaff(prompt.note)
                }
                Column(
                    Modifier.weight(RIGHT_PANE_WEIGHT).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val summary = state.summary
                    if (summary ==
                        null
                    ) {
                        QuizPane(state, actions)
                    } else {
                        SummaryPane(summary, state.storageFailed, actions, state.pedalHint)
                    }
                }
            }
            if (state.summary ==
                null
            ) {
                KeyStrip(
                    state.keyRange,
                    state.litKeys,
                    state.glowKeys,
                    state.progress?.lastWrong,
                    Modifier.padding(top = 6.dp),
                    state.noteLabels,
                )
            }
        }
    }
}

@Composable
private fun QuizPane(
    state: QuizState,
    actions: QuizActions,
) {
    val progress = state.progress
    state.quiz?.let { Text(quizPromptText(it.prompt), style = MaterialTheme.typography.titleLarge) }
    if (state.connection == LessonConnection.DISCONNECTED) {
        Text(
            stringResource(Res.string.lesson_disconnected),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (progress != null) {
        Text(
            if (progress.finished) {
                stringResource(Res.string.lesson_try_done)
            } else {
                stringResource(Res.string.lesson_try_progress, progress.done.toString(), progress.total.toString())
            },
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = displayFontFamily,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        (wrongKeyText(progress) ?: timingLine(progress, state.quiz?.check))?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    state.countIn?.let {
        Text(
            it.toString(),
            fontFamily = displayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = COUNT_IN_SIZE,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Assertive
                    contentDescription = ""
                },
        )
    }
    if (state.storageFailed) Text(stringResource(Res.string.quiz_not_saved), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = actions.onSkip, modifier = Modifier.heightIn(min = BUTTON_HEIGHT)) {
        Text(stringResource(Res.string.lesson_skip))
    }
}

/** The line after a wrong key: the landmark hint when one key is next, else a plain "try again". */
@Composable
private fun wrongKeyText(progress: TryProgress): String? {
    val wrong = progress.lastWrong ?: return null
    val expected = progress.expected
    return if (expected == null) {
        stringResource(Res.string.lesson_try_wrong_any, MidiNote(wrong).name)
    } else {
        wrongKeyHintText(wrongKeyHint(wrong, expected))
    }
}

@Composable
private fun SummaryPane(
    summary: QuizSummary,
    storageFailed: Boolean,
    actions: QuizActions,
    pedalHint: Boolean,
) {
    Text(
        if (summary.count == 1) {
            stringResource(Res.string.quiz_summary_one, summary.clean.toString())
        } else {
            stringResource(Res.string.quiz_summary, summary.count.toString(), summary.clean.toString())
        },
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (storageFailed) Text(stringResource(Res.string.quiz_not_saved), style = MaterialTheme.typography.bodySmall)
    PrimaryButton(stringResource(Res.string.lesson_done), actions.onDone, pedalHint)
}

/** The treble staff with [note] on it (a notehead and ledger lines): "which key is this note?" */
@Composable
private fun MiniStaff(note: Int) {
    val line = MaterialTheme.pianoAnchorColors.staffLine
    val accent = MaterialTheme.colorScheme.primary
    val description = stringResource(Res.string.quiz_staff_description, MidiNote(note).name)
    Canvas(Modifier.size(STAFF_WIDTH, STAFF_HEIGHT).semantics { contentDescription = description }) {
        val unit = size.height / STAFF_SPAN

        fun y(position: Int) = (TREBLE_HIGH - position) * unit
        val stroke = STAFF_STROKE.toPx()
        TREBLE_LINES.forEach { drawLine(line, Offset(0f, y(it)), Offset(size.width, y(it)), stroke) }
        val position = staffPosition(note)
        val x = size.width / 2
        val half = unit * LEDGER_HALF_UNITS
        ledgerLines(
            position,
            TREBLE_LINES,
        ).forEach { drawLine(line, Offset(x - half, y(it)), Offset(x + half, y(it)), stroke) }
        val width = unit * NOTEHEAD_WIDTH_UNITS
        val height = unit * NOTEHEAD_HEIGHT_UNITS
        rotate(NOTEHEAD_ANGLE, Offset(x, y(position))) {
            drawOval(accent, Offset(x - width / 2, y(position) - height / 2), Size(width, height))
        }
    }
}
