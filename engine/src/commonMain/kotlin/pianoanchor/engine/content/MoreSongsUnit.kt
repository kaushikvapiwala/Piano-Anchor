package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit

/** The optional "More songs" unit after the course: first inversions, A minor and a four-chord progression. */
internal val MORE_SONGS_UNIT: CurriculumUnit =
    CurriculumUnit(
        id = "unit-more-songs",
        title = "More songs",
        skills =
            listOf(
                Skills.MORE_INVERSIONS.id,
                Skills.MORE_A_MINOR.id,
                Skills.MORE_FOUR_CHORDS.id,
            ),
        passages =
            listOf(
                chordPassage(
                    id = "close-moves",
                    title = "Close moves",
                    left = "$C_H $F1_H | $G1_H $C_H | $C_H $F1_H | $G1_H $C_H",
                    tempoBpm = 66,
                    skills = setOf(Skills.MORE_INVERSIONS.id),
                    rights = exerciseRights("moving between C, F and G with first inversions, the hand barely moving"),
                ),
                chordPassage(
                    id = "minor-colour",
                    title = "Minor colour",
                    left = "$C_H $AM_H | $C_W | $AM_H $C_H | $C_W",
                    tempoBpm = 66,
                    skills = setOf(Skills.MORE_A_MINOR.id),
                    rights = exerciseRights("the A minor triad between C chords"),
                ),
                chordPassage(
                    id = "when-the-saints-go-marching-in",
                    title = "When the Saints Go Marching In",
                    right =
                        "C4q E4q F4q G4q | G4w | C4q E4q F4q G4q | G4w | C4q E4q F4q G4q | " +
                            "E4q C4q E4q D4q | E4q E4q D4q C4q | F4q E4q D4q C4q | C4w",
                    left = "$C_W | $C_W | $C_W | $C_W | $C_W | $C_H $G1_H | $G1_H $C_H | $F_H $C_H | $C_W",
                    tempoBpm = 76,
                    skills = setOf(Skills.MORE_INVERSIONS.id, Skills.MELODY_AND_CHORDS.id),
                    rights =
                        publicDomainRights(
                            origin =
                                "When the Saints Go Marching In, traditional American spiritual, " +
                                    "19th century or earlier",
                            basis =
                                "A traditional spiritual with no known author, 19th century or earlier, so any " +
                                    "copyright has long expired. The melody and the left-hand chords are our own " +
                                    "arrangement, not taken from any printed edition.",
                        ),
                    piece = true,
                    chords = "C F | C | C F | C | C F | C | C G | F G | C",
                ),
                chordPassage(
                    id = "four-chord-walk",
                    title = "Four-chord walk",
                    right =
                        "E4q G4q G4q E4q | D4q G4q F4q D4q | E4q E4q D4q C4q | F4q E4q D4q C4q | " +
                            "G4q E4q C4q E4q | D4q F4q G4q F4q | E4q D4q C4q E4q | D4q E4q F4q D4q | C4w",
                    left = "$C_W | $G_W | $AM_W | $F_W | $C_W | $G_W | $AM_W | $F_W | $C_W",
                    tempoBpm = 72,
                    skills = setOf(Skills.MORE_FOUR_CHORDS.id),
                    rights = originalPieceRights("A walk through the chords C, G, Am and F under a melody"),
                    piece = true,
                    chords = "C | G | Am | F | C | G | Am | F | C",
                ),
            ),
    )
