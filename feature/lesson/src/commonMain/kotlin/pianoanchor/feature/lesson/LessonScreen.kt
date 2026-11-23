package pianoanchor.feature.lesson

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.LessonStep
import pianoanchor.feature.lesson.resources.Res
import pianoanchor.feature.lesson.resources.lesson_card_how
import pianoanchor.feature.lesson.resources.lesson_card_what
import pianoanchor.feature.lesson.resources.lesson_card_when
import pianoanchor.feature.lesson.resources.lesson_card_why
import pianoanchor.feature.lesson.resources.lesson_dot
import pianoanchor.feature.lesson.resources.lesson_dot_current
import pianoanchor.feature.lesson.resources.lesson_dot_in_use
import pianoanchor.feature.lesson.resources.lesson_step_recap
import pianoanchor.feature.lesson.resources.lesson_step_see
import pianoanchor.feature.lesson.resources.lesson_step_try
import pianoanchor.feature.lesson.resources.lesson_step_try_in_use
import pianoanchor.feature.lesson.resources.lesson_step_use
import pianoanchor.feature.lesson.resources.lesson_step_why
import pianoanchor.ui.shell.BackButton
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors

private const val LEFT_PANE_WEIGHT = 0.4f
private const val RIGHT_PANE_WEIGHT = 0.6f
private val DOT_SIZE = 14.dp
private val DOT_TARGET = 48.dp

/**
 * The five-step lesson, landscape (`docs/ui/living-studio.md`, "Lesson"): the concept's card on the left (title, the
 * four fields, the row of step dots), the step's content on the right, and the key strip along the bottom. Only the
 * right pane changes between steps. The dots are buttons that jump to any step. Stateless; the card is static.
 */
@Composable
public fun LessonScreen(
    state: LessonState,
    actions: LessonActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.pianoAnchorColors.page) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CardPane(state, actions, Modifier.weight(LEFT_PANE_WEIGHT).fillMaxHeight())
                Box(Modifier.weight(RIGHT_PANE_WEIGHT).fillMaxHeight()) { StepPane(state, actions) }
            }
            LessonKeyStrip(state, Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun CardPane(
    state: LessonState,
    actions: LessonActions,
    modifier: Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(actions.onBack)
            Text(
                state.skill.title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = displayFontFamily,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CardField(Res.string.lesson_card_what, state.card.what)
            CardField(Res.string.lesson_card_why, state.card.why)
            CardField(Res.string.lesson_card_how, state.card.how)
            CardField(Res.string.lesson_card_when, state.card.whenMet)
        }
        StepDots(state, actions.onGoTo)
    }
}

@Composable
private fun CardField(
    label: StringResource,
    text: String,
) {
    Column {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** The five dots, each a button that jumps to its step; TalkBack reads "Step 3 of 5, Try, current". */
@Composable
private fun StepDots(
    state: LessonState,
    onGoTo: (LessonStep) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (step in LessonStep.entries) {
            val inUse = step == LessonStep.TRY && state.tryMergedIntoUse
            val name = stepName(step)
            val number = (step.ordinal + 1).toString()
            val total = LessonStep.entries.size.toString()
            val description =
                when {
                    step == state.step -> stringResource(Res.string.lesson_dot_current, number, total, name)
                    inUse -> stringResource(Res.string.lesson_dot_in_use, number, total, name)
                    else -> stringResource(Res.string.lesson_dot, number, total, name)
                }
            val reached = step.ordinal <= state.step.ordinal
            val dotOn = MaterialTheme.colorScheme.primary
            val dotOff = MaterialTheme.colorScheme.outlineVariant
            Column(
                Modifier
                    .sizeIn(minWidth = DOT_TARGET, minHeight = DOT_TARGET)
                    .clickable(role = Role.Button) { onGoTo(step) }
                    .clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Button
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(DOT_SIZE)
                        .background(
                            if (reached) dotOn else dotOff,
                            CircleShape,
                        ),
                )
                Text(
                    if (inUse) stringResource(Res.string.lesson_step_try_in_use) else name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (step == state.step) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
internal fun stepName(step: LessonStep): String =
    stringResource(
        when (step) {
            LessonStep.WHY -> Res.string.lesson_step_why
            LessonStep.SEE -> Res.string.lesson_step_see
            LessonStep.TRY -> Res.string.lesson_step_try
            LessonStep.USE -> Res.string.lesson_step_use
            LessonStep.RECAP -> Res.string.lesson_step_recap
        },
    )
