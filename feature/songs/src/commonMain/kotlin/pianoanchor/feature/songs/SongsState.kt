package pianoanchor.feature.songs

import pianoanchor.core.model.MidiDevice
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.PieceView
import pianoanchor.engine.lesson.CourseProgress
import pianoanchor.engine.matcher.TempoRung

/**
 * What the Songs screen shows. [pieces] is null until they have loaded (or when [storageFailed]); [piano] is the
 * connected piano, or null while none is. [ticked] are the ids of the course concepts passed (a clean wait-mode run)
 * and [next] the id of the suggested one, null when the course is done or not loaded.
 */
public data class SongsState(
    val pieces: List<PieceView>? = null,
    val piano: MidiDevice? = null,
    val storageFailed: Boolean = false,
    val ticked: Set<String> = emptySet(),
    val next: String? = null,
) {
    /** Whether [piece] has course concepts and every one of them is ticked: its row shows a tick. */
    public fun isTicked(piece: PieceView): Boolean {
        val course = piece.passage.skills.filter { it in courseSkillIds }
        return course.isNotEmpty() && ticked.containsAll(course)
    }

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
