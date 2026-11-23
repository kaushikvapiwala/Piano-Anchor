package pianoanchor.engine.content

import pianoanchor.core.model.Passage
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.PassageHistory
import pianoanchor.engine.session.highestClean
import pianoanchor.engine.session.isClean
import pianoanchor.engine.session.nextRung

/** Where a piece stands for the learner: new, in progress or mastered. Nothing is locked. */
public enum class PieceStatus { NEW, IN_PROGRESS, MASTERED }

/**
 * One piece on the Songs screen. [cleanRungs] of [totalRungs] rungs of the tempo ladder have a CLEAN run; [nextRung]
 * is the first rung above the highest clean one (WAIT when none), null when [PieceStatus.MASTERED].
 */
public data class PieceView(
    val passage: Passage,
    val unitTitle: String,
    val status: PieceStatus,
    val cleanRungs: Int,
    val totalRungs: Int,
    val nextRung: TempoRung?,
)

/** The pieces of the curriculum with the learner's progress on each (`docs/algorithms/songs.md`). Pure. */
public object Pieces {
    /**
     * Every passage marked as a piece, grouped by unit in curriculum order and in content order within a unit.
     * [history] is each passage's history by passage id (missing: never played). Skill states play no part: every
     * piece is open.
     */
    public fun list(
        curriculum: Curriculum,
        history: Map<String, PassageHistory>,
    ): List<PieceView> =
        curriculum.units.flatMap { unit ->
            unit.passages
                .filter { it.piece }
                .map { view(it, unit.title, history[it.id]) }
        }

    private fun view(
        passage: Passage,
        unitTitle: String,
        history: PassageHistory?,
    ): PieceView {
        val clean = TempoRung.entries.count { isClean(passage, history, it) }
        val highest = highestClean(passage, history)
        val status =
            when {
                history?.lastPlayed == null -> PieceStatus.NEW
                highest == TempoRung.P100 -> PieceStatus.MASTERED
                else -> PieceStatus.IN_PROGRESS
            }
        return PieceView(passage, unitTitle, status, clean, TempoRung.entries.size, nextRung(highest))
    }
}
