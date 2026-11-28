package pianoanchor.feature.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.jetbrains.compose.resources.stringResource
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.guidance.wrongKeyHint
import pianoanchor.engine.lesson.TryProgress
import pianoanchor.engine.lesson.TryTiming
import pianoanchor.engine.lesson.rhythmCount
import pianoanchor.feature.lesson.resources.Res
import pianoanchor.feature.lesson.resources.lesson_card_how
import pianoanchor.feature.lesson.resources.lesson_card_why
import pianoanchor.feature.lesson.resources.lesson_count_in
import pianoanchor.feature.lesson.resources.lesson_disconnected
import pianoanchor.feature.lesson.resources.lesson_done
import pianoanchor.feature.lesson.resources.lesson_next
import pianoanchor.feature.lesson.resources.lesson_next_lesson
import pianoanchor.feature.lesson.resources.lesson_play_again
import pianoanchor.feature.lesson.resources.lesson_recap
import pianoanchor.feature.lesson.resources.lesson_recap_done
import pianoanchor.feature.lesson.resources.lesson_recap_not_passed
import pianoanchor.feature.lesson.resources.lesson_see_count
import pianoanchor.feature.lesson.resources.lesson_see_listen
import pianoanchor.feature.lesson.resources.lesson_see_watch_hand
import pianoanchor.feature.lesson.resources.lesson_skip
import pianoanchor.feature.lesson.resources.lesson_try_clean
import pianoanchor.feature.lesson.resources.lesson_try_done
import pianoanchor.feature.lesson.resources.lesson_try_early
import pianoanchor.feature.lesson.resources.lesson_try_gap
import pianoanchor.feature.lesson.resources.lesson_try_late
import pianoanchor.feature.lesson.resources.lesson_try_overlap
import pianoanchor.feature.lesson.resources.lesson_try_pedal_early
import pianoanchor.feature.lesson.resources.lesson_try_pedal_hold_early
import pianoanchor.feature.lesson.resources.lesson_try_pedal_late
import pianoanchor.feature.lesson.resources.lesson_try_pedal_start
import pianoanchor.feature.lesson.resources.lesson_try_progress
import pianoanchor.feature.lesson.resources.lesson_try_wrong_any
import pianoanchor.feature.lesson.resources.lesson_use_play
import pianoanchor.feature.lesson.resources.lesson_use_play_it
import pianoanchor.ui.shell.PedalHintLine
import pianoanchor.ui.shell.displayFontFamily
import pianoanchor.ui.shell.withPedalHint
import pianoanchor.ui.shell.wrongKeyHintText

private val COUNT_IN_SIZE = 56.sp
private val BUTTON_HEIGHT = 44.dp
private val WHY_ANIMATION_MAX_WIDTH = 280.dp

/** The right pane: the content of the current step. */
@Composable
internal fun StepPane(
    state: LessonState,
    actions: LessonActions,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stepName(state.step),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        when (state.step) {
            LessonStep.WHY -> {
                state.scene?.let { CardAnimation(it, Modifier.widthIn(max = WHY_ANIMATION_MAX_WIDTH)) }
                Body(state.card.why)
                Buttons { PrimaryButton(stringResource(Res.string.lesson_next), actions.onNext, state.pedalHint) }
            }

            LessonStep.SEE -> {
                SeePane(state, actions)
            }

            LessonStep.TRY -> {
                TryPane(state, actions)
            }

            LessonStep.USE -> {
                UsePane(state, actions)
            }

            LessonStep.RECAP -> {
                RecapPane(state, actions)
            }
        }
    }
}

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
private fun Buttons(content: @Composable () -> Unit) =
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }

/** The step's main button; with [pedalHint] a small line beside it says a pedal tap does the same (TalkBack too). */
@Composable
internal fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    pedalHint: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onClick,
            modifier = Modifier.heightIn(min = BUTTON_HEIGHT).withPedalHint(label, pedalHint),
        ) { Text(label) }
        PedalHintLine(pedalHint)
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) = OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = BUTTON_HEIGHT)) {
    Text(label)
}

@Composable
private fun SeePane(
    state: LessonState,
    actions: LessonActions,
) {
    if (state.check == TryCheck.AnyKey) {
        Body(stringResource(Res.string.lesson_see_watch_hand))
        Buttons { PrimaryButton(stringResource(Res.string.lesson_next), actions.onNext, state.pedalHint) }
    } else {
        Body(stringResource(Res.string.lesson_see_listen))
        (state.check as? TryCheck.Rhythm)?.let {
            Body(
                stringResource(Res.string.lesson_see_count, rhythmCount(it.lengths)),
            )
        }
        Buttons {
            SecondaryButton(stringResource(Res.string.lesson_play_again), actions.onPlayAgain, enabled = !state.playing)
            PrimaryButton(stringResource(Res.string.lesson_skip), actions.onSkip, state.pedalHint)
        }
    }
}

@Composable
private fun TryPane(
    state: LessonState,
    actions: LessonActions,
) {
    val progress = state.tryProgress
    Body(instruction(state.check))
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
        if (state.repsNeeded > 1) {
            Text(
                stringResource(Res.string.lesson_try_clean, state.cleanReps.toString(), state.repsNeeded.toString()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        (wrongKeyLine(progress) ?: timingLine(progress, state.check))?.let {
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
    Buttons { TextButton(onClick = actions.onSkip) { Text(stringResource(Res.string.lesson_skip)) } }
}

/** The line after a wrong key: what it was and what the app waits for. */
@Composable
private fun wrongKeyLine(progress: TryProgress): String? {
    val wrong = progress.lastWrong ?: return null
    val expected = progress.expected
    return if (expected == null) {
        stringResource(Res.string.lesson_try_wrong_any, MidiNote(wrong).name)
    } else {
        wrongKeyHintText(wrongKeyHint(wrong, expected))
    }
}

/**
 * The line after an attempt that went wrong in time, else null: a rhythm tap too early or late, a legato gap or
 * overlap, a pedal lifted or changed at the wrong moment (the words follow [check]).
 */
@Composable
internal fun timingLine(
    progress: TryProgress,
    check: TryCheck?,
): String? =
    when (progress.timing) {
        TryTiming.EARLY -> {
            stringResource(
                when (check) {
                    is TryCheck.PedalChange -> Res.string.lesson_try_pedal_early
                    is TryCheck.PedalHold -> Res.string.lesson_try_pedal_hold_early
                    else -> Res.string.lesson_try_early
                },
            )
        }

        TryTiming.LATE -> {
            stringResource(
                if (check is TryCheck.PedalChange) Res.string.lesson_try_pedal_late else Res.string.lesson_try_late,
            )
        }

        TryTiming.GAP -> {
            stringResource(Res.string.lesson_try_gap)
        }

        TryTiming.OVERLAP -> {
            stringResource(Res.string.lesson_try_overlap)
        }

        null -> {
            null
        }
    }

@Composable
private fun UsePane(
    state: LessonState,
    actions: LessonActions,
) {
    val passage = state.usePassage
    if (passage != null) Body(stringResource(Res.string.lesson_use_play, passage.title))
    Buttons {
        PrimaryButton(stringResource(Res.string.lesson_use_play_it), actions.onPlayIt, state.pedalHint)
        TextButton(onClick = actions.onNext) { Text(stringResource(Res.string.lesson_next)) }
    }
}

@Composable
private fun RecapPane(
    state: LessonState,
    actions: LessonActions,
) {
    val recap = state.recap
    if (recap != null) {
        Body(
            if (recap.notPassed) {
                stringResource(Res.string.lesson_recap_not_passed, recap.learned)
            } else if (recap.nextTitle == null) {
                stringResource(Res.string.lesson_recap_done)
            } else {
                stringResource(
                    Res.string.lesson_recap,
                    recap.learned,
                    recap.nextTitle,
                )
            },
        )
    }
    CardField(Res.string.lesson_card_why, state.card.why)
    CardField(Res.string.lesson_card_how, state.card.how)
    Buttons {
        if (recap?.nextSkillId != null) {
            PrimaryButton(stringResource(Res.string.lesson_next_lesson), actions.onNextLesson, state.pedalHint)
            SecondaryButton(stringResource(Res.string.lesson_done), actions.onDone)
        } else {
            PrimaryButton(stringResource(Res.string.lesson_done), actions.onDone, state.pedalHint)
        }
    }
}
