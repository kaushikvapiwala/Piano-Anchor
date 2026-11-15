package pianoanchor.engine.content

import pianoanchor.core.model.Passage
import pianoanchor.core.model.Skill
import pianoanchor.core.model.SkillGraph
import pianoanchor.core.model.SkillState
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.PassageHistory

/** Where a piece stands for the learner: [LOCKED] until its skills are reachable, then new, in progress or mastered. */
public enum class PieceStatus { LOCKED, NEW, IN_PROGRESS, MASTERED }

/**
 * One piece on the Songs screen. [cleanRungs] of [totalRungs] rungs of the tempo ladder have a CLEAN run; [nextRung]
 * is the first rung above the highest clean one (WAIT when none), null when [PieceStatus.MASTERED]. [waitingFor] is
 * the first skill (graph order) of the piece whose prerequisites are not all learned, set when LOCKED.
 */
public data class PieceView(
    val passage: Passage,
    val unitTitle: String,
    val status: PieceStatus,
    val cleanRungs: Int,
    val totalRungs: Int,
    val nextRung: TempoRung?,
    val waitingFor: Skill?,
)

/** The pieces of the curriculum with the learner's progress on each (`docs/algorithms/songs.md`). Pure. */
public object Pieces {
    /**
     * Every passage marked as a piece, grouped by unit in curriculum order and in content order within a unit.
     * [states] are the skill states by skill id, [history] each passage's history by passage id (missing: never
     * played).
     */
    public fun list(
        curriculum: Curriculum,
        graph: SkillGraph,
        states: Map<String, SkillState>,
        history: Map<String, PassageHistory>,
    ): List<PieceView> =
        curriculum.units.flatMap { unit ->
            unit.passages
                .filter { it.piece }
                .map { view(it, unit.title, graph, states, history[it.id]) }
        }

    private fun view(
        passage: Passage,
        unitTitle: String,
        graph: SkillGraph,
        states: Map<String, SkillState>,
        history: PassageHistory?,
    ): PieceView {
        val waitingFor =
            graph.skills.firstOrNull { skill ->
                skill.id in passage.skills && !skill.prerequisites.all { states[it]?.learned == true }
            }
        val clean =
            TempoRung.entries.filter { rung ->
                val bpm = rung.bpm(passage)
                history != null && if (bpm == null) history.cleanWait else bpm in history.cleanTempos
            }
        val highest = clean.lastOrNull()
        val status =
            when {
                waitingFor != null -> PieceStatus.LOCKED
                history?.lastPlayed == null -> PieceStatus.NEW
                highest == TempoRung.P100 -> PieceStatus.MASTERED
                else -> PieceStatus.IN_PROGRESS
            }
        val next = if (highest == null) TempoRung.WAIT else TempoRung.entries.getOrNull(highest.ordinal + 1)
        return PieceView(passage, unitTitle, status, clean.size, TempoRung.entries.size, next, waitingFor)
    }
}
