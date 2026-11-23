package pianoanchor.feature.ear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.EarChoice
import pianoanchor.core.model.EarExercise
import pianoanchor.feature.ear.resources.Res
import pianoanchor.feature.ear.resources.ear_answer_mark
import pianoanchor.feature.ear.resources.ear_answer_marked
import pianoanchor.feature.ear.resources.ear_back
import pianoanchor.feature.ear.resources.ear_back_glyph
import pianoanchor.feature.ear.resources.ear_choice_fifth
import pianoanchor.feature.ear.resources.ear_choice_fourth
import pianoanchor.feature.ear.resources.ear_choice_higher
import pianoanchor.feature.ear.resources.ear_choice_lower
import pianoanchor.feature.ear.resources.ear_choice_major_chord
import pianoanchor.feature.ear.resources.ear_choice_major_third
import pianoanchor.feature.ear.resources.ear_choice_minor_chord
import pianoanchor.feature.ear.resources.ear_choice_minor_third
import pianoanchor.feature.ear.resources.ear_choice_skip
import pianoanchor.feature.ear.resources.ear_choice_step
import pianoanchor.feature.ear.resources.ear_done
import pianoanchor.feature.ear.resources.ear_feedback_revealed
import pianoanchor.feature.ear.resources.ear_feedback_try_again
import pianoanchor.feature.ear.resources.ear_item_of
import pianoanchor.feature.ear.resources.ear_key_answer
import pianoanchor.feature.ear.resources.ear_key_c
import pianoanchor.feature.ear.resources.ear_key_d
import pianoanchor.feature.ear.resources.ear_key_e
import pianoanchor.feature.ear.resources.ear_key_f
import pianoanchor.feature.ear.resources.ear_key_g
import pianoanchor.feature.ear.resources.ear_listening
import pianoanchor.feature.ear.resources.ear_next
import pianoanchor.feature.ear.resources.ear_prompt_echo
import pianoanchor.feature.ear.resources.ear_prompt_identify
import pianoanchor.feature.ear.resources.ear_replay
import pianoanchor.feature.ear.resources.ear_replay_none
import pianoanchor.feature.ear.resources.ear_show_me
import pianoanchor.feature.ear.resources.ear_skill_of
import pianoanchor.feature.ear.resources.ear_summary
import pianoanchor.feature.ear.resources.ear_summary_one_skill
import pianoanchor.feature.ear.resources.ear_summary_storage
import pianoanchor.feature.ear.resources.ear_summary_title
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.pianoAnchorColors

private val AnswerButton = 48.dp
private val PillShape = RoundedCornerShape(24.dp)

/** The panes split the width about 45/55, left to right. */
private const val LEFT_PANE_WEIGHT = 0.45f
private const val RIGHT_PANE_WEIGHT = 0.55f
private val PaneGap = 24.dp

/** The keys the echo strip draws, C4 to G4: MIDI note number and name. */
private val KEYS =
    listOf(
        60 to Res.string.ear_key_c,
        62 to Res.string.ear_key_d,
        64 to Res.string.ear_key_e,
        65 to Res.string.ear_key_f,
        67 to Res.string.ear_key_g,
    )

/**
 * The ear-training screen, landscape (Living Studio, `docs/ui/living-studio.md`): back, the skill, "Item 2 of 4"
 * with a progress bar, then, on the left, Replay, the feedback line and, after a wrong answer, Show me; on the right
 * the item (the answer buttons for identify, the key strip for echo). At the end, the summary. Stateless: see
 * [EarViewModel].
 */
@Composable
public fun EarScreen(
    state: EarState,
    actions: EarActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.pianoAnchorColors.page) {
        val summary = state.summary
        val exercise = state.exercise
        if (summary != null || exercise == null) {
            Column(
                modifier =
                    Modifier
                        .safeDrawingPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Before the first item loads there is nothing to show but the header and its back button.
                if (summary != null) Summary(state, summary, actions.onDone) else Header(state, actions.onBack)
            }
        } else {
            // Landscape: the exercise (header, prompt, replay) on the left, the answer buttons or keys on the right.
            Row(
                modifier = Modifier.safeDrawingPadding().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(PaneGap),
            ) {
                Column(
                    modifier = Modifier.weight(LEFT_PANE_WEIGHT).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Header(state, actions.onBack)
                    Exercise(state, exercise, actions)
                }
                Column(
                    modifier = Modifier.weight(RIGHT_PANE_WEIGHT).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Answers(state, exercise, actions)
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: EarState,
    onBack: () -> Unit,
) {
    val itemText = stringResource(Res.string.ear_item_of, state.itemNumber.toString(), state.itemCount.toString())
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = AnswerButton)) {
            val back = stringResource(Res.string.ear_back)
            Text(
                stringResource(Res.string.ear_back_glyph),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { contentDescription = back },
            )
        }
        Column {
            Text(
                state.skillTitle,
                fontFamily = displayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.ear_skill_of, state.skillNumber.toString(), state.skillCount.toString()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Text(itemText, style = MaterialTheme.typography.titleMedium)
    LinearProgressIndicator(
        progress = { state.itemNumber.toFloat() / state.itemCount },
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
    )
}

/** The left pane under the header: the prompt, the feedback line and the replay or next controls. */
@Composable
private fun Exercise(
    state: EarState,
    exercise: EarExercise,
    actions: EarActions,
) {
    val prompt =
        when {
            state.playing -> Res.string.ear_listening
            exercise is EarExercise.Echo -> Res.string.ear_prompt_echo
            else -> Res.string.ear_prompt_identify
        }
    Text(
        stringResource(prompt),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Feedback(state.feedback)
    Controls(state, actions)
}

/** The right pane: what the learner answers with. */
@Composable
private fun Answers(
    state: EarState,
    exercise: EarExercise,
    actions: EarActions,
) {
    when (exercise) {
        is EarExercise.Identify -> Choices(exercise, state.answerShown, !state.playing, actions.onAnswer)
        is EarExercise.Echo -> KeyStrip(exercise, state.answerShown)
    }
}

@Composable
private fun Choices(
    exercise: EarExercise.Identify,
    answerShown: Boolean,
    enabled: Boolean,
    onAnswer: (EarChoice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        for (choice in exercise.choices) {
            val name = stringResource(choiceName(choice))
            val marked = answerShown && choice == exercise.answer
            val described = if (marked) stringResource(Res.string.ear_answer_marked, name) else name
            val modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = AnswerButton)
                    .semantics { contentDescription = described }
            if (marked) {
                // The answer is filled and carries a check mark: colour is never the only signal.
                Button(onClick = { onAnswer(choice) }, modifier = modifier, enabled = enabled, shape = PillShape) {
                    Text(stringResource(Res.string.ear_answer_mark) + " " + name, fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedButton(
                    onClick = { onAnswer(choice) },
                    modifier = modifier,
                    enabled = enabled,
                    shape = PillShape,
                ) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun choiceName(choice: EarChoice): StringResource =
    when (choice) {
        EarChoice.HIGHER -> Res.string.ear_choice_higher
        EarChoice.LOWER -> Res.string.ear_choice_lower
        EarChoice.STEP -> Res.string.ear_choice_step
        EarChoice.SKIP -> Res.string.ear_choice_skip
        EarChoice.MAJOR_THIRD -> Res.string.ear_choice_major_third
        EarChoice.MINOR_THIRD -> Res.string.ear_choice_minor_third
        EarChoice.FOURTH -> Res.string.ear_choice_fourth
        EarChoice.FIFTH -> Res.string.ear_choice_fifth
        EarChoice.MAJOR_CHORD -> Res.string.ear_choice_major_chord
        EarChoice.MINOR_CHORD -> Res.string.ear_choice_minor_chord
    }

/** Five keys, C4 to G4. Once the answer is shown, its keys are filled and read as part of the answer. */
@Composable
private fun KeyStrip(
    exercise: EarExercise.Echo,
    answerShown: Boolean,
) {
    val answer = exercise.sounds.flatMapTo(HashSet()) { group -> group.map { it.number } }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((number, label) in KEYS) {
            val name = stringResource(label)
            val inAnswer = answerShown && number in answer
            val colors = MaterialTheme.colorScheme
            val described = if (inAnswer) stringResource(Res.string.ear_key_answer, name) else name
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(88.dp)
                        .background(
                            if (inAnswer) colors.primaryContainer else colors.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        ).padding(bottom = 8.dp)
                        .semantics { contentDescription = described },
            ) {
                Text(
                    if (inAnswer) stringResource(Res.string.ear_answer_mark) + " " + name else name,
                    fontWeight = FontWeight.Bold,
                    color = if (inAnswer) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

/** "Not quite. Listen again." or the revealed-answer line, as a polite live region. */
@Composable
private fun Feedback(feedback: EarFeedback) {
    val text =
        when (feedback) {
            EarFeedback.NONE -> return
            EarFeedback.TRY_AGAIN -> Res.string.ear_feedback_try_again
            EarFeedback.REVEALED -> Res.string.ear_feedback_revealed
        }
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun Controls(
    state: EarState,
    actions: EarActions,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.answerShown) {
            Button(onClick = actions.onNext, modifier = Modifier.heightIn(min = AnswerButton), shape = PillShape) {
                Text(stringResource(Res.string.ear_next), fontWeight = FontWeight.SemiBold)
            }
        } else {
            OutlinedButton(
                onClick = actions.onReplay,
                enabled = state.replaysLeft > 0,
                modifier = Modifier.heightIn(min = AnswerButton),
                shape = PillShape,
            ) {
                val text =
                    if (state.replaysLeft > 0) {
                        stringResource(Res.string.ear_replay, state.replaysLeft.toString())
                    } else {
                        stringResource(Res.string.ear_replay_none)
                    }
                Text(text, fontWeight = FontWeight.SemiBold)
            }
            if (state.canShowMe) {
                TextButton(onClick = actions.onShowMe, modifier = Modifier.heightIn(min = AnswerButton)) {
                    Text(stringResource(Res.string.ear_show_me), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Summary(
    state: EarState,
    summary: EarSummary,
    onDone: () -> Unit,
) {
    Text(
        stringResource(Res.string.ear_summary_title),
        fontFamily = displayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        modifier = Modifier.semantics { heading() },
    )
    val first = summary.firstTry.toString()
    val items = summary.items.toString()
    Text(
        if (summary.skills == 1) {
            stringResource(Res.string.ear_summary_one_skill, first, items)
        } else {
            stringResource(Res.string.ear_summary, summary.skills.toString(), first, items)
        },
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (state.storageFailed) {
        Text(
            stringResource(Res.string.ear_summary_storage),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    Button(onClick = onDone, modifier = Modifier.heightIn(min = AnswerButton), shape = PillShape) {
        Text(stringResource(Res.string.ear_done), fontWeight = FontWeight.SemiBold)
    }
}
