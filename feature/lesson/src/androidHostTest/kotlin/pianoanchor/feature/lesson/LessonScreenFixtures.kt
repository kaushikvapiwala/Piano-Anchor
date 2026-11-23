package pianoanchor.feature.lesson

import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.Skill
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.lesson.Quiz
import pianoanchor.engine.lesson.QuizPrompt
import pianoanchor.engine.lesson.TryProgress
import pianoanchor.engine.lesson.TryRun

internal const val SDK = 36

/** A lesson at [step] for [skill], with the Try's [progress] and the [litKeys] on the strip. */
internal fun lessonState(
    step: LessonStep,
    skill: Skill = Skills.RH_C_POSITION,
    progress: TryProgress? = null,
    litKeys: Set<Int> = emptySet(),
    recap: RecapText? = null,
    countIn: Int? = null,
) = LessonState(
    skill = skill,
    card = checkNotNull(Curriculum.card(skill.id)),
    step = step,
    tryProgress = progress,
    playing = false,
    litKeys = litKeys,
    recap = recap,
    connection = LessonConnection.CONNECTED,
    countIn = countIn,
)

/** The Try of right-hand C position after the notes [played] (C D E F G A... of its sequence). */
internal fun sequenceProgress(vararg played: Int): TryProgress {
    var run = TryRun(checkNotNull(Curriculum.tryCheck(Skills.RH_C_POSITION.id)))
    played.forEach { run = run.onNoteOn(it, 64, 0) }
    return run.progress
}

/** The finished Try of middle C. */
internal fun finishedMiddleC(): TryProgress =
    TryRun(checkNotNull(Curriculum.tryCheck(Skills.MIDDLE_C.id))).onNoteOn(60, 64, 0).progress

/** The quiz screen at quiz [number] of 3 for [quiz], with the Try's [progress]. */
internal fun quizState(
    quiz: Quiz,
    number: Int = 2,
    progress: TryProgress? = TryRun(quiz.check).progress,
    litKeys: Set<Int> = emptySet(),
) = QuizState(
    number = number,
    count = 3,
    quiz = quiz,
    progress = progress,
    litKeys = litKeys,
    countIn = null,
    connection = LessonConnection.CONNECTED,
    summary = null,
    storageFailed = false,
)

/** "Play every F": F in the quiz range. */
internal val everyFQuiz =
    Quiz(Skills.WHITE_KEY_NAMES.id, TryCheck.FindAll(5, Tunables.QUIZ_RANGE), QuizPrompt.PlayEvery(5))

/** "A skip up from E". */
internal val skipQuiz =
    Quiz(Skills.STEPS_AND_SKIPS.id, TryCheck.Sequence(listOf(64, 67)), QuizPrompt.SkipUpFrom(64))

/** A note on the staff to find. */
internal val staffQuiz =
    Quiz(Skills.TREBLE_CLEF.id, TryCheck.PlayKey(67, lit = false), QuizPrompt.PlayKey(67, onStaff = true))
