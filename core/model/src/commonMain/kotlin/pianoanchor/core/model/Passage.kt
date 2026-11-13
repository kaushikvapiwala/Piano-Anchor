package pianoanchor.core.model

/** One moment in the music: the keys to press together (one = melody note, several = chord). Not empty. */
public data class Step(
    val notes: Set<MidiNote>,
) {
    init {
        require(notes.isNotEmpty()) { "a step must have at least one note" }
    }
}

/** A piece of music to practise. [id] is stable (stored with attempts) and not blank; [steps] is not empty. */
public data class Passage(
    val id: String,
    val title: String,
    val steps: List<Step>,
) {
    init {
        require(id.isNotBlank()) { "passage id must not be blank" }
        require(steps.isNotEmpty()) { "passage \"$id\" must have at least one step" }
    }
}
