package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Rights
import pianoanchor.core.model.Step

/**
 * The songs unit, "Songs and chords": left-hand triads in C position under a right-hand melody that stays in C4-G4.
 * Each left-hand chord shares a step with the right-hand note it starts with, so wait mode asks for them together.
 */
internal val SONGS_UNIT: CurriculumUnit =
    CurriculumUnit(
        id = "unit-3",
        title = "Songs and chords",
        skills =
            listOf(
                Skills.CHORD_TRIADS.id,
                Skills.CHORD_CHANGES.id,
                Skills.CHORD_MELODY.id,
                Skills.CHORD_INVERSIONS.id,
                Skills.CHORD_A_MINOR.id,
                Skills.CHORD_FOUR.id,
            ),
        passages =
            listOf(
                chordPassage(
                    id = "three-chords",
                    title = "Three chords",
                    left = "$C_W | $F_W | $G_W | $C_W",
                    tempoBpm = 60,
                    skills = setOf(Skills.CHORD_TRIADS.id),
                    rights = exerciseRights("the C, F and G triads in the left hand, one whole note each"),
                ),
                chordPassage(
                    id = "change-on-the-beat",
                    title = "Change on the beat",
                    left = "$C_H $G_H | $C_H $F_H | $C_H $G_H | $C_W",
                    tempoBpm = 66,
                    skills = setOf(Skills.CHORD_CHANGES.id),
                    rights = exerciseRights("changing between C, G and F in half notes"),
                ),
                chordPassage(
                    id = "hold-and-sing",
                    title = "Hold and sing",
                    right = "C4q D4q E4q D4q | E4q F4q G4h | G4q F4q E4q D4q | C4w",
                    left = "$C_W | $F_W | $G_W | $C_W",
                    tempoBpm = 60,
                    skills = setOf(Skills.CHORD_MELODY.id),
                    rights = exerciseRights("a five-note tune over whole-note chords"),
                ),
                chordPassage(
                    id = "close-moves",
                    title = "Close moves",
                    left = "$C_H $F1_H | $G1_H $C_H | $C_H $F1_H | $G1_H $C_H",
                    tempoBpm = 66,
                    skills = setOf(Skills.CHORD_INVERSIONS.id),
                    rights = exerciseRights("moving between C, F and G with first inversions, the hand barely moving"),
                ),
                chordPassage(
                    id = "minor-colour",
                    title = "Minor colour",
                    left = "$C_H $AM_H | $C_W | $AM_H $C_H | $C_W",
                    tempoBpm = 66,
                    skills = setOf(Skills.CHORD_A_MINOR.id),
                    rights = exerciseRights("the A minor triad between C chords"),
                ),
                chordPassage(
                    id = "lightly-row-with-chords",
                    title = "Lightly Row with chords",
                    right =
                        "G4q E4q E4h | F4q D4q D4h | C4q D4q E4q F4q | G4q G4q G4h | " +
                            "G4q E4q E4h | F4q D4q D4h | C4q E4q G4q G4q | C4w",
                    left = "$C_W | $G_W | $C_W | $C_W | $C_W | $G_W | $C_H $G_H | $C_W",
                    tempoBpm = 72,
                    skills = setOf(Skills.CHORD_CHANGES.id, Skills.CHORD_MELODY.id),
                    rights =
                        publicDomainRights(
                            origin = "Lightly Row, from the traditional German song \"Hänschen klein\", 19th century",
                            basis =
                                "A traditional German children's song with no known composer, 19th century, so any " +
                                    "copyright has long expired. The melody and the left-hand chords are our own " +
                                    "arrangement.",
                        ),
                    piece = true,
                ),
                chordPassage(
                    id = "ode-to-joy-with-chords",
                    title = "Ode to Joy with chords",
                    right =
                        "E4q E4q F4q G4q | G4q F4q E4q D4q | C4q C4q D4q E4q | E4q. D4e D4h | " +
                            "E4q E4q F4q G4q | G4q F4q E4q D4q | C4q C4q D4q E4q | D4q. C4e C4h | C4w",
                    left = "$C_W | $G1_W | $C_W | $C_H $G1_H | $C_W | $G1_W | $C_W | $G1_H $C_H | $C_W",
                    tempoBpm = 66,
                    skills = setOf(Skills.CHORD_MELODY.id, Skills.CHORD_INVERSIONS.id),
                    rights = odeToJoyRights(),
                    piece = true,
                ),
                chordPassage(
                    id = "when-the-saints-go-marching-in",
                    title = "When the Saints Go Marching In",
                    right =
                        "C4q E4q F4q G4q | G4w | C4q E4q F4q G4q | G4w | C4q E4q F4q G4q | " +
                            "E4q C4q E4q D4q | E4q E4q D4q C4q | F4q E4q D4q C4q | C4w",
                    left = "$C_W | $C_W | $C_W | $C_W | $C_W | $C_H $G1_H | $G1_H $C_H | $F_H $C_H | $C_W",
                    tempoBpm = 76,
                    skills = setOf(Skills.CHORD_MELODY.id, Skills.CHORD_INVERSIONS.id),
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
                ),
                chordPassage(
                    id = "four-chord-walk",
                    title = "Four-chord walk",
                    right =
                        "E4q G4q G4q E4q | D4q G4q F4q D4q | E4q E4q D4q C4q | F4q E4q D4q C4q | " +
                            "G4q E4q C4q E4q | D4q F4q G4q F4q | E4q D4q C4q E4q | D4q E4q F4q D4q | C4w",
                    left = "$C_W | $G_W | $AM_W | $F_W | $C_W | $G_W | $AM_W | $F_W | $C_W",
                    tempoBpm = 72,
                    skills = setOf(Skills.CHORD_FOUR.id),
                    rights = originalPieceRights("A walk through the chords C, G, Am and F under a melody"),
                    piece = true,
                ),
            ),
    )

private const val C_TRIAD = "C3 E3 G3"
private const val F_TRIAD = "F2 A2 C3"
private const val F_FIRST_INVERSION = "A2 C3 F3"
private const val G_TRIAD = "G2 B2 D3"
private const val G_FIRST_INVERSION = "B2 D3 G3"
private const val A_MINOR_TRIAD = "A2 C3 E3"

private const val C_W = "[$C_TRIAD]w"
private const val F_W = "[$F_TRIAD]w"
private const val G_W = "[$G_TRIAD]w"
private const val G1_W = "[$G_FIRST_INVERSION]w"
private const val AM_W = "[$A_MINOR_TRIAD]w"
private const val C_H = "[$C_TRIAD]h"
private const val F_H = "[$F_TRIAD]h"
private const val G_H = "[$G_TRIAD]h"
private const val F1_H = "[$F_FIRST_INVERSION]h"
private const val G1_H = "[$G_FIRST_INVERSION]h"
private const val AM_H = "[$A_MINOR_TRIAD]h"

/**
 * A 4/4 passage with left-hand chords ([left], note text) and, when given, a right-hand melody ([right], note text of
 * the same length). Steps that start together merge, so a chord and the melody note it starts with are one step; each
 * note keeps its own length, so a chord can be held while the melody moves.
 */
private fun chordPassage(
    id: String,
    title: String,
    left: String,
    tempoBpm: Int,
    skills: Set<String>,
    rights: Rights,
    right: String? = null,
    piece: Boolean = false,
): Passage {
    val chords = builtinPassage(id, title, left, tempoBpm, skills, rights, piece = piece)
    val melody = right?.let { NoteText.parse(it, FOUR_FOUR) }.orEmpty()
    val steps =
        (chords.steps + melody)
            .groupBy { it.onsetTicks }
            .entries
            .sortedBy { it.key }
            .map { (onset, same) -> Step(onset, same.flatMapTo(LinkedHashSet()) { it.notes }) }
    return chords.copy(steps = steps)
}
