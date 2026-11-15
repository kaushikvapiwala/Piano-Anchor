package pianoanchor.engine.content

import kotlinx.datetime.LocalDate
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Rights
import pianoanchor.core.model.RightsStatus
import pianoanchor.core.model.TimeSignature

/** Passages built into the app; [Curriculum] is the full list. */
public object Passages {
    /**
     * C4 – D4 – E4 in 4/4 at 80 BPM: two quarter notes, then a half note that closes the phrase. A placeholder in
     * unit 0 until the real units land (M3.2).
     */
    public val CDE: Passage =
        Passage(
            id = "builtin:cde",
            title = "C-D-E",
            steps = NoteText.parse("C4q D4q E4h"),
            timeSignature = TimeSignature(beats = 4, beatUnit = 4),
            tempoBpm = CDE_TEMPO_BPM,
            rights =
                Rights(
                    status = RightsStatus.ORIGINAL,
                    origin = "Piano Anchor, 2026",
                    basis = "Three notes of a rising scale, written for Piano Anchor.",
                    checkedOn = LocalDate(2026, 11, 15),
                ),
            skills = setOf(Skills.RH_C_POSITION.id),
        )
}

/** A gentle beginner pace for the first passage. */
private const val CDE_TEMPO_BPM = 80
