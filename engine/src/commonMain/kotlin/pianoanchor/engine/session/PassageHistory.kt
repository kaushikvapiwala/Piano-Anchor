package pianoanchor.engine.session

import pianoanchor.core.model.Passage
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.matcher.TempoRung
import kotlin.time.Instant

/**
 * What the learner has played of one passage: when it was last played, the tempos (BPM) of its CLEAN timed runs and
 * whether a wait-mode run was CLEAN ([cleanWait]). [firstCleanUnaidedWait] is when the first CLEAN wait-mode run with
 * no hint shown started, null when there is none ([cleanUnaidedWait] says whether there is one): what passes a course
 * concept.
 */
public data class PassageHistory(
    val lastPlayed: Instant?,
    val cleanTempos: Set<Int>,
    val cleanWait: Boolean = false,
    val firstCleanUnaidedWait: Instant? = null,
) {
    /** Whether a CLEAN wait-mode run was played with no hint shown. */
    val cleanUnaidedWait: Boolean get() = firstCleanUnaidedWait != null
}

/**
 * The [PassageHistory] of each of [passages], keyed by passage id: the one loader Today and Songs share.
 *
 * @throws pianoanchor.core.model.StorageUnavailableException when the store can't be read.
 */
public suspend fun AttemptRepositoryPort.passageHistories(passages: Iterable<Passage>): Map<String, PassageHistory> =
    passages.associate {
        it.id to
            PassageHistory(
                lastPlayed(it.id),
                cleanTempos(it.id),
                hasCleanWaitRun(it.id),
                firstCleanUnaidedWaitRun(it.id),
            )
    }

/** Whether [history] has a CLEAN run of [passage] at [rung] (a CLEAN wait-mode run for [TempoRung.WAIT]). */
internal fun isClean(
    passage: Passage,
    history: PassageHistory?,
    rung: TempoRung,
): Boolean {
    val bpm = rung.bpm(passage)
    return history != null && if (bpm == null) history.cleanWait else bpm in history.cleanTempos
}

/** The highest rung of the tempo ladder with a CLEAN run of [passage] in [history], null when none. */
internal fun highestClean(
    passage: Passage,
    history: PassageHistory?,
): TempoRung? = TempoRung.entries.lastOrNull { isClean(passage, history, it) }

/** The rung to play next above [highestClean]: WAIT when none is clean, null when the top rung is. */
internal fun nextRung(highestClean: TempoRung?): TempoRung? =
    if (highestClean == null) TempoRung.WAIT else TempoRung.entries.getOrNull(highestClean.ordinal + 1)
