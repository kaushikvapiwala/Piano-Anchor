package pianoanchor.feature.lesson

import pianoanchor.core.model.CardScene
import pianoanchor.core.model.ConceptCard
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Skill
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.lesson.HandTimeline
import pianoanchor.engine.lesson.NoteLabels
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
 * unpassed concept ([nextSkillId]), both null at the end of the course and when this concept is [notPassed] yet (its
 * Try or Use was skipped): it is then still the next one, as on Today's Next card.
 */
public data class RecapText(
    val learned: String,
    val nextTitle: String?,
    val nextSkillId: String? = null,
    val notPassed: Boolean = false,
)

/**
 * What the lesson screen shows: the concept's [skill] and [card], the current [step], the [tryProgress] of the Try
 * (null outside it), whether the See demo is [playing], the keys lit now ([litKeys], MIDI note numbers: sounding in
 * See, held in Try) and the [recap] (only in the Recap step). [countIn] is the numeral of the count-in (3, 2, 1) of a
 * Try with a click, null otherwise. [pedalControl] is true while a pedal tap may continue the lesson (the setting is on
 * and the concept does not use the pedal as music). A Try passes after [repsNeeded] clean checks in a row: [cleanReps]
 * is how many it has so far (a wrong key resets it to 0). In See, [seeHand] is the hand that plays the demo (null: no
 * hand) and [seeElapsedMs] the time into the demo, which decides which fingers are down (0 when not playing). A pedal
 * demo sets [pedalCue]: true while the demo has the pedal down, false while it is up, null when there is no pedal cue.
 * [check] is the concept's Try check (every course concept has one).
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
    val pedalControl: Boolean = false,
    val cleanReps: Int = 0,
    val repsNeeded: Int = 1,
    val noteLabels: NoteLabels? = null,
    val seeHand: HandTimeline? = null,
    val seeElapsedMs: Long = 0,
    val pedalCue: Boolean? = null,
    val check: TryCheck? = Curriculum.tryCheck(skill.id),
) {
    /** True when the step shows "Tap the pedal to continue": a tap acts here and the piano is connected. */
    val pedalHint: Boolean
        get() =
            pedalControl && connection == LessonConnection.CONNECTED && step != LessonStep.TRY &&
                (step != LessonStep.RECAP || recap != null)

    /** The concept card's looping animation, or null while its unit has none. */
    val scene: CardScene? get() = Curriculum.scene(skill.id)

    /** The concept's first passage, played in the Use step. */
    val usePassage: Passage? get() = usePassageOf(skill.id)

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
            val lit =
                (check as? TryCheck.PlayKey)?.lit == true || check is TryCheck.Sequence || check is TryCheck.Legato ||
                    check is TryCheck.EvenSequence
            return if (lit || progress.lastWrong != null) setOf(expected) else emptySet()
        }
}

/** The first passage of the course concept [skillId], played in its Use step; null when it has none. */
internal fun usePassageOf(skillId: String): Passage? = Curriculum.passages.firstOrNull { skillId in it.skills }

/** Where a [LessonViewModel] asks the host to go: the Use step's Practice, the next lesson, or back to Today. */
public enum class LessonExit {
    /** Open Practice on the concept's passage (the Use step). */
    PLAY_IT,

    /** Open the lesson of the next concept (Recap). */
    NEXT_LESSON,

    /** Leave for Today (Recap at the end of the course). */
    DONE,
}
