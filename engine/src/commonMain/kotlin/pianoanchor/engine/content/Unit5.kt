package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit
import pianoanchor.core.model.KeySignature

/** The G triad (G B D) in the left-hand G position, one whole note. */
internal const val G_POS_G_W = "[G2 B2 D3]w"

/** The D7 shortcut (F sharp, C, D) in the left hand, one whole note. */
internal const val D7_W = "[F#2 C3 D3]w"

/**
 * Unit 5, "G position and the key of G": both hands on G A B C D, the key signature with one sharp, the G and D7
 * chords, ending with Go Tell Aunt Rhody. Every passage of the unit is in the key of G.
 */
internal val UNIT_5: CurriculumUnit =
    CurriculumUnit(
        id = "unit-5",
        title = "G position and the key of G",
        skills =
            listOf(
                Skills.RH_G_POSITION.id,
                Skills.LH_G_POSITION.id,
                Skills.KEY_OF_G.id,
                Skills.G_AND_D7_CHORDS.id,
                Skills.MELODY_CHORDS_IN_G.id,
            ),
        passages =
            listOf(
                builtinPassage(
                    id = "g-position-walk",
                    title = "G position walk",
                    notes = "G4q A4q B4q C5q | D5h C5q B4q | A4q B4q C5q A4q | G4w",
                    tempoBpm = 72,
                    skills = setOf(Skills.RH_G_POSITION.id),
                    rights = exerciseRights("the right-hand G position, up and down the five keys"),
                    keySignature = KeySignature.G,
                ),
                builtinPassage(
                    id = "left-hand-in-g",
                    title = "Left hand in G",
                    notes = "G2q A2q B2q C3q | D3h C3q B2q | A2q B2q A2q B2q | G2w",
                    tempoBpm = 72,
                    skills = setOf(Skills.LH_G_POSITION.id),
                    rights = exerciseRights("the left-hand G position, up and down the five keys"),
                    keySignature = KeySignature.G,
                ),
                builtinPassage(
                    id = "f-sharp-in-g",
                    title = "F sharp in G",
                    notes = "G4q A4q B4q G4q | A4q G4q F#4h | G4q B4q A4q F#4q | G4w",
                    tempoBpm = 72,
                    skills = setOf(Skills.KEY_OF_G.id),
                    rights = exerciseRights("the F sharp of the key of G, reached by finger 1 stretching down one key"),
                    keySignature = KeySignature.G,
                ),
                chordPassage(
                    id = "g-and-d7",
                    title = "G and D7",
                    right = "B4h D5h | B4w | A4h C5h | B4w",
                    left = "$G_POS_G_W | $G_POS_G_W | $D7_W | $G_POS_G_W",
                    tempoBpm = 66,
                    skills = setOf(Skills.G_AND_D7_CHORDS.id),
                    rights = exerciseRights("the G chord and the D7 shortcut in the left hand under a simple tune"),
                    keySignature = KeySignature.G,
                ),
                chordPassage(
                    id = "go-tell-aunt-rhody",
                    title = "Go Tell Aunt Rhody",
                    right =
                        "B4q B4q A4q G4q | G4w | A4q A4q C5q B4q | A4h G4h | " +
                            "D5q D5q C5q B4q | B4w | A4q G4q A4q B4q | G4w",
                    left =
                        "$G_POS_G_W | $G_POS_G_W | $D7_W | $G_POS_G_W | " +
                            "$G_POS_G_W | $G_POS_G_W | $D7_W | $G_POS_G_W",
                    tempoBpm = 88,
                    skills =
                        setOf(
                            Skills.MELODY_CHORDS_IN_G.id,
                            Skills.G_AND_D7_CHORDS.id,
                            Skills.RH_G_POSITION.id,
                        ),
                    rights =
                        publicDomainRights(
                            origin =
                                "Go Tell Aunt Rhody, an American folk song of the 19th century; the tune goes back " +
                                    "to a melody in Rousseau's opera Le Devin du village, 1752",
                            basis =
                                "A folk song of the 19th century whose tune is older still (Rousseau, 1752), so it " +
                                    "is in the public domain. This simplified melody and the left-hand chords are " +
                                    "our own arrangement.",
                        ),
                    piece = true,
                    keySignature = KeySignature.G,
                ),
            ),
    )
