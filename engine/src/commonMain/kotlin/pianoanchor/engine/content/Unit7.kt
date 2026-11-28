package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit
import pianoanchor.core.model.KeySignature

/** The C chord in the left hand, a half note: it shares bar 3 of Joy to the World with the G chord. */
private const val C_HALF_NOTE = "[$C_TRIAD]h"

/** The G chord in first inversion in the left hand, a half note. */
private const val G1_HALF_NOTE = "[$G_FIRST_INVERSION]h"

/** The F chord in first inversion in the left hand, a whole note. */
private const val F1_WHOLE_NOTE = "[$F_FIRST_INVERSION]w"

private const val SCALE_TEMPO_BPM = 66
private const val JOY_TEMPO_BPM = 80

/**
 * Unit 7, "Scales with thumb-under": the thumb passing under the hand, the C and G major scales in the right hand,
 * the C major scale in the left, and Joy to the World, which runs down a scale. It closes course 2.
 */
internal val UNIT_7: CurriculumUnit =
    CurriculumUnit(
        id = "unit-7",
        title = "Scales with thumb-under",
        skills =
            listOf(
                Skills.THUMB_UNDER.id,
                Skills.C_MAJOR_SCALE.id,
                Skills.G_MAJOR_SCALE.id,
                Skills.LH_C_SCALE.id,
                Skills.SCALE_SONG.id,
            ),
        passages =
            listOf(
                builtinPassage(
                    id = "thumb-under-walk",
                    title = "Thumb under walk",
                    notes = "C4q D4q E4q F4q | G4q A4q G4q F4q | E4q D4q E4q F4q | G4w",
                    tempoBpm = SCALE_TEMPO_BPM,
                    skills = setOf(Skills.THUMB_UNDER.id),
                    rights = exerciseRights("passing the thumb under the hand on the way up the keys"),
                ),
                builtinPassage(
                    id = "c-scale",
                    title = "C major scale",
                    notes = "C4q D4q E4q F4q | G4q A4q B4q C5q | B4q A4q G4q F4q | E4q D4q C4h",
                    tempoBpm = SCALE_TEMPO_BPM,
                    skills = setOf(Skills.C_MAJOR_SCALE.id),
                    rights = exerciseRights("the C major scale, one octave up and down"),
                ),
                builtinPassage(
                    id = "g-scale",
                    title = "G major scale",
                    notes = "G4q A4q B4q C5q | D5q E5q F#5q G5q | F#5q E5q D5q C5q | B4q A4q G4h",
                    tempoBpm = SCALE_TEMPO_BPM,
                    skills = setOf(Skills.G_MAJOR_SCALE.id),
                    rights = exerciseRights("the G major scale, one octave up and down"),
                    keySignature = KeySignature.G,
                ),
                builtinPassage(
                    id = "left-hand-c-scale",
                    title = "Left-hand C scale",
                    notes = "C3q D3q E3q F3q | G3q A3q B3q C4q | B3q A3q G3q F3q | E3q D3q C3h",
                    tempoBpm = SCALE_TEMPO_BPM,
                    skills = setOf(Skills.LH_C_SCALE.id),
                    rights = exerciseRights("the C major scale in the left hand, one octave up and down"),
                ),
                chordPassage(
                    id = "joy-to-the-world",
                    title = "Joy to the World",
                    right = "C5h B4q. A4e | G4h. F4q | E4h D4h | C4h. G4q | A4h. A4q | B4h. B4q | C5w",
                    left = "$C_W | $G1_W | $C_HALF_NOTE $G1_HALF_NOTE | $C_W | $F1_WHOLE_NOTE | $G1_W | $C_W",
                    tempoBpm = JOY_TEMPO_BPM,
                    skills = setOf(Skills.SCALE_SONG.id, Skills.C_MAJOR_SCALE.id),
                    rights =
                        publicDomainRights(
                            origin =
                                "Joy to the World, the tune \"Antioch\" arranged by Lowell Mason, 1839; the words " +
                                    "are by Isaac Watts, 1719",
                            basis =
                                "A tune arranged in 1839 with words from 1719, so it is in the public domain. " +
                                    "This simplified melody and the left-hand chords are our own arrangement.",
                        ),
                    piece = true,
                ),
            ),
    )
