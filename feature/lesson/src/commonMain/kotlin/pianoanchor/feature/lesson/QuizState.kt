package pianoanchor.feature.lesson

import pianoanchor.engine.lesson.Quiz
import pianoanchor.engine.lesson.TryProgress

/** How a finished set of quizzes went: [clean] of [count] had no wrong key and weren't skipped. */
public data class QuizSummary(
    val count: Int,
    val clean: Int,
)

/**
 * What the quiz screen shows: quiz [number] of [count] ([quiz] is null once the [summary] shows), the [progress] of its
 * Try, the keys held now ([litKeys]), the [countIn] numeral of a quiz with a click and the piano's [connection].
 * [storageFailed] is true when a result could not be saved. [pedalControl] is true while a pedal tap may press Done.
 */
public data class QuizState(
    val number: Int,
    val count: Int,
    val quiz: Quiz?,
    val progress: TryProgress?,
    val litKeys: Set<Int>,
    val countIn: Int?,
    val connection: LessonConnection,
    val summary: QuizSummary?,
    val storageFailed: Boolean,
    val pedalControl: Boolean = false,
) {
    /** True when the summary shows "Tap the pedal to continue": a pedal tap acts as Done and the piano is connected. */
    val pedalHint: Boolean get() = pedalControl && summary != null && connection == LessonConnection.CONNECTED

    /** The keys the strip shows. */
    val keyRange: IntRange get() = keyRange(quiz?.check)

    /** The expected key, glowing in the accent after a wrong key (as in the lesson's Try). */
    val glowKeys: Set<Int>
        get() {
            val current = progress?.takeIf { !it.finished && it.lastWrong != null } ?: return emptySet()
            return setOfNotNull(current.expected)
        }
}

/** What the user can do on the [QuizScreen]; the composition root wires these to the ViewModel and navigation. */
public data class QuizActions(
    val onBack: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onDone: () -> Unit = {},
)
