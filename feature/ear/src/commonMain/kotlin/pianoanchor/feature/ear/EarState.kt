package pianoanchor.feature.ear

import pianoanchor.core.model.EarExercise
import pianoanchor.engine.Tunables

/** What the line under the item says. */
public enum class EarFeedback {
    NONE,

    /** One wrong answer: "Not quite. Listen again."; one more answer is allowed. */
    TRY_AGAIN,

    /** The item is over without a right answer and its answer is shown. */
    REVEALED,
}

/** How the whole session went: [skills] reviewed, and [firstTry] of [items] right on the first try. */
public data class EarSummary(
    val skills: Int,
    val firstTry: Int,
    val items: Int,
)

/**
 * What the ear screen shows. [exercise] is the current item (null before the first and at the summary); [playing] is
 * true while it sounds. [summary] is set when every skill is done. [storageFailed] means a skill's result couldn't be
 * saved.
 */
public data class EarState(
    val skillTitle: String = "",
    val skillNumber: Int = 0,
    val skillCount: Int = 0,
    val itemNumber: Int = 0,
    val itemCount: Int = Tunables.EAR_ITEMS_PER_REVIEW,
    val exercise: EarExercise? = null,
    val playing: Boolean = false,
    val replaysLeft: Int = Tunables.EAR_MAX_REPLAYS,
    val feedback: EarFeedback = EarFeedback.NONE,
    val summary: EarSummary? = null,
    val storageFailed: Boolean = false,
) {
    /** "Show me" is offered after the first wrong answer. */
    val canShowMe: Boolean get() = feedback == EarFeedback.TRY_AGAIN

    /** True once the answer is on show. */
    val answerShown: Boolean get() = feedback == EarFeedback.REVEALED
}
