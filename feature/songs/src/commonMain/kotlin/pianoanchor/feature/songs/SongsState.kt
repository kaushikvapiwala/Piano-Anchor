package pianoanchor.feature.songs

import pianoanchor.core.model.MidiDevice
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.PieceView
import pianoanchor.engine.lesson.CourseProgress
import pianoanchor.engine.matcher.TempoRung

/**
 * What the Songs screen shows. [pieces] is null until they have loaded (or when [storageFailed]); [piano] is the
 * connected piano, or null while none is. [passed] are the ids of the course concepts passed (a clean wait-mode run
 * with no hint, or placed), [learned] those of them that came back as a quiz on a later day and were passed again, and
 * [next] the id of the suggested one, null when the course is done or not loaded.
 */
public data class SongsState(
    val pieces: List<PieceView>? = null,
    val piano: MidiDevice? = null,
    val storageFailed: Boolean = false,
    val passed: Set<String> = emptySet(),
    val learned: Set<String> = emptySet(),
    val next: String? = null,
) {
    /** Whether [piece] has course concepts and every one of them is passed: its row shows a tick. */
    public fun isPassed(piece: PieceView): Boolean =
        courseSkills(piece).let { it.isNotEmpty() && passed.containsAll(it) }

    /** Whether [piece] has course concepts and every one of them is learned: its tick gets a star. */
    public fun isLearned(piece: PieceView): Boolean =
        courseSkills(piece).let {
            it.isNotEmpty() &&
                learned.containsAll(it)
        }

    private fun courseSkills(piece: PieceView) = piece.passage.skills.filter { it in courseSkillIds }

    /** Whether [piece] exercises the suggested concept: its row shows the "Next" chip. */
    public fun isNext(piece: PieceView): Boolean = next != null && next in piece.passage.skills
}

private val courseSkillIds: Set<String> = CourseProgress.skills(Curriculum).mapTo(HashSet()) { it.id }

/** Where tapping an unlocked piece leads. */
public sealed interface SongsTarget {
    /** Practice [passageId] on [device] at [rung]. */
    public data class Practice(
        val device: MidiDevice,
        val passageId: String,
        val rung: TempoRung,
    ) : SongsTarget

    /** No piano is connected: the Pianos screen. */
    public data object Pianos : SongsTarget
}
