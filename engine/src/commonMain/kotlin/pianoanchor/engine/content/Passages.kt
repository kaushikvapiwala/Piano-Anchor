package pianoanchor.engine.content

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Step

/** Passages built into the app. Original content (no rights record needed). */
public object Passages {
    /** C4 – D4 – E4, single notes. */
    public val CDE: Passage =
        Passage(
            id = "builtin:cde",
            title = "C-D-E",
            steps = listOf(C4, D4, E4).map { Step(setOf(MidiNote(it))) },
        )
}

private const val C4 = 60
private const val D4 = 62
private const val E4 = 64
