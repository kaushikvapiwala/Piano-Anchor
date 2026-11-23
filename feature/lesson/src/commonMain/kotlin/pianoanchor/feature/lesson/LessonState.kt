package pianoanchor.feature.lesson

import pianoanchor.core.model.ConceptCard
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Skill
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.lesson.TryProgress

/** The link to the piano during the lesson. */
public enum class LessonConnection {
    /** The piano's notes arrive. */
    CONNECTED,

    /** The piano's stream ended or failed; the Try can't be played until the lesson is opened again. */
    DISCONNECTED,
}

/**
 * The Recap line: what the learner can now do ([learned], the concept's title) and the [nextTitle] of the next
 * unticked concept ([nextSkillId]), both null at the end of the course.
 */
public data class RecapText(
    val learned: String,
    val nextTitle: String?,
    val nextSkillId: String? = null,
)

/**
 * What the lesson screen shows: the concept's [skill] and [card], the current [step], the [tryProgress] of the Try
 * (null outside it), whether the See demo is [playing], the keys lit now ([litKeys], MIDI note numbers: sounding in
 * See, held in Try) and the [recap] (only in the Recap step). [countIn] is the numeral of the count-in (3, 2, 1) of a
 * Try with a click, null otherwise.
 */
public data class LessonState(
    val skill: Skill,
    val card: ConceptCard,
    val step: LessonStep,
    val tryProgress: TryProgress?,
    val playing: Boolean,
    val litKeys: Set<Int>,
    val recap: RecapText?,
    val connection: LessonConnection,
    val countIn: Int? = null,
) {
    /** The concept's Try check; every course concept has one. */
    val check: TryCheck? get() = Curriculum.tryCheck(skill.id)

    /** The concept's first passage, played in the Use step. */
    val usePassage: Passage? get() = Curriculum.passages.firstOrNull { skill.id in it.skills }

    /** True when the Try is the Use step's passage: there is no separate Try step. */
    val tryMergedIntoUse: Boolean get() = check is TryCheck.Passage

    /**
     * The keys that glow in the accent: the expected key of a Try that lights it (a lit key or a sequence), and of
     * any Try after a wrong key.
     */
    val glowKeys: Set<Int>
        get() {
            val progress = tryProgress?.takeIf { step == LessonStep.TRY && !it.finished } ?: return emptySet()
            val expected = progress.expected ?: return emptySet()
            val lit = (check as? TryCheck.PlayKey)?.lit == true || check is TryCheck.Sequence
            return if (lit || progress.lastWrong != null) setOf(expected) else emptySet()
        }
}
