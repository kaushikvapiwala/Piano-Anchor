package pianoanchor.feature.lesson

import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.Skill
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
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
