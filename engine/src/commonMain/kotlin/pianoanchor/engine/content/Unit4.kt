package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit

/**
 * Unit 4, "Eighth notes and the dotted quarter": the first notes shorter than a beat, in the right-hand C position
 * (Unit 3's left-hand chords under the two-handed passages), ending with the Jingle Bells chorus.
 */
internal val UNIT_4: CurriculumUnit =
    CurriculumUnit(
        id = "unit-4",
        title = "Eighth notes and the dotted quarter",
        skills =
            listOf(
                Skills.EIGHTH_NOTES.id,
                Skills.EIGHTH_PAIRS.id,
                Skills.DOTTED_QUARTER.id,
                Skills.RHYTHM_SONG.id,
            ),
        passages =
            listOf(
                builtinPassage(
                    id = "running-steps",
                    title = "Running steps",
                    notes = "C4q D4q E4e F4e G4q | G4e F4e E4q D4h | E4q F4q G4e F4e E4q | D4e E4e D4q C4h",
                    tempoBpm = 72,
                    skills = setOf(Skills.EIGHTH_NOTES.id),
                    rights = exerciseRights("steps in quarters and pairs of eighths"),
                ),
                chordPassage(
                    id = "skipping-eighths",
                    title = "Skipping eighths",
                    right = "E4q G4q E4e D4e C4q | D4e E4e F4e D4e E4h | G4q E4q F4e E4e D4q | E4e D4e C4q C4h",
                    left = "$C_W | $G1_W | $C_W | $G1_H $C_H",
                    tempoBpm = 72,
                    skills = setOf(Skills.EIGHTH_PAIRS.id),
                    rights = exerciseRights("beamed eighth pairs mixed with quarters, over left-hand chords"),
                ),
                builtinPassage(
                    id = "long-short",
                    title = "Long, short",
                    notes = "C4q. D4e E4h | E4q. F4e G4h | G4q. F4e E4q D4q | E4q. D4e C4h",
                    tempoBpm = 66,
                    skills = setOf(Skills.DOTTED_QUARTER.id),
                    rights = exerciseRights("a dotted quarter followed by an eighth"),
                ),
                chordPassage(
                    id = "jingle-bells",
                    title = "Jingle Bells",
                    right =
                        "E4q E4q E4h | E4q E4q E4h | E4q G4q C4q. D4e | E4w | " +
                            "F4q F4q F4q. F4e | F4q E4q E4e E4e E4q | E4q D4q D4q E4q | D4h G4h | " +
                            "E4q E4q E4h | E4q E4q E4h | E4q G4q C4q. D4e | E4w | " +
                            "F4q F4q F4q. F4e | F4q E4q E4e E4e E4q | G4q G4q F4q D4q | C4w",
                    left =
                        "$C_W | $C_W | $C_W | $C_W | $F_W | $C_W | $G1_W | $G1_W | " +
                            "$C_W | $C_W | $C_W | $C_W | $F_W | $C_W | $G1_W | $C_W",
                    tempoBpm = 80,
                    skills = setOf(Skills.RHYTHM_SONG.id, Skills.EIGHTH_NOTES.id, Skills.DOTTED_QUARTER.id),
                    rights =
                        publicDomainRights(
                            origin = "Jingle Bells, by James Lord Pierpont, published 1857",
                            basis =
                                "Written by James Lord Pierpont and published in 1857, so its copyright has long " +
                                    "expired. Only the chorus is used; the left-hand chords are our own arrangement.",
                        ),
                    piece = true,
                ),
            ),
    )
