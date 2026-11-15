package pianoanchor.engine.ear

import pianoanchor.core.model.EarExercise
import pianoanchor.core.model.NoteOn

private const val SEMITONES_PER_OCTAVE = 12

/** Judges an echo answer. */
public object EarEcho {
    /**
     * True when the first notes of [played] (NoteOns in order) are the target notes in order: the exact keys when
     * the exercise asks for the exact octave, else the same pitch classes in any octave. Fewer played notes than
     * targets never match; extra ones are ignored. A chord group counts as its notes from the lowest up.
     */
    public fun matches(
        exercise: EarExercise.Echo,
        played: List<NoteOn>,
    ): Boolean {
        val target = exercise.sounds.flatMap { group -> group.map { it.number }.sorted() }
        if (played.size < target.size) return false
        val heard = played.take(target.size).map { it.note.number }
        return if (exercise.exactOctave) {
            heard == target
        } else {
            heard.map { it % SEMITONES_PER_OCTAVE } == target.map { it % SEMITONES_PER_OCTAVE }
        }
    }
}
