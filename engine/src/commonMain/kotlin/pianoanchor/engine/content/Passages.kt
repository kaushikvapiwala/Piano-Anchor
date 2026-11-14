package pianoanchor.engine.content

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature

/** Passages built into the app. Original content (no rights record needed). */
public object Passages {
    /** C4 – D4 – E4 in 4/4 at 80 BPM: two quarter notes, then a half note that closes the phrase. */
    public val CDE: Passage =
        Passage(
            id = "builtin:cde",
            title = "C-D-E",
            steps =
                listOf(
                    note(onset = 0, C4, QUARTER),
                    note(onset = QUARTER, D4, QUARTER),
                    note(onset = 2 * QUARTER, E4, 2 * QUARTER),
                ),
            timeSignature = TimeSignature(beats = 4, beatUnit = 4),
            tempoBpm = CDE_TEMPO_BPM,
        )

    /** The built-in passage with [id], or null if there is none (e.g. an attempt of a passage since removed). */
    public fun byId(id: String): Passage? = listOf(CDE).firstOrNull { it.id == id }
}

private fun note(
    onset: Int,
    pitch: Int,
    length: Int,
) = Step(onset, setOf(ScoreNote(MidiNote(pitch), length)))

private const val QUARTER = TICKS_PER_QUARTER
private const val C4 = 60
private const val D4 = 62
private const val E4 = 64

/** A gentle beginner pace for the first passage. */
private const val CDE_TEMPO_BPM = 80
