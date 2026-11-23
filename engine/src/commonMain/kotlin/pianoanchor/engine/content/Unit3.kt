package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Rights
import pianoanchor.core.model.Step

/**
 * Unit 3, "Chords and songs", the course's goal: left-hand triads in C position under a right-hand melody that stays
 * in C4-G4. Each left-hand chord shares a step with the right-hand note it starts with, so wait mode asks for them
 * together.
 */
internal val UNIT_3: CurriculumUnit =
    CurriculumUnit(
        id = "unit-3",
        title = "Chords and songs",
        skills =
            listOf(
                Skills.C_CHORD.id,
                Skills.G_AND_F_CHORDS.id,
                Skills.MELODY_AND_CHORDS.id,
                Skills.SHARPS_AND_FLATS.id,
            ),
        passages =
            listOf(
                chordPassage(
                    id = "c-chord-hold",
                    title = "C chord, held then on the beat",
                    left = "$C_W | $C_Q $C_Q $C_Q $C_Q",
                    tempoBpm = 60,
                    skills = setOf(Skills.C_CHORD.id),
                    rights = exerciseRights("a left-hand C triad held for a bar, then played on each beat"),
                ),
                chordPassage(
                    id = "three-chords",
                    title = "Three chords",
                    left = "$C_W | $F_W | $G_W | $C_W",
                    tempoBpm = 60,
                    skills = setOf(Skills.G_AND_F_CHORDS.id),
                    rights = exerciseRights("the C, F and G triads in the left hand, one whole note each"),
                ),
                chordPassage(
                    id = "change-on-the-beat",
                    title = "Change on the beat",
                    left = "$C_H $G_H | $C_H $F_H | $C_H $G_H | $C_W",
                    tempoBpm = 66,
                    skills = setOf(Skills.G_AND_F_CHORDS.id),
                    rights = exerciseRights("changing between C, G and F in half notes"),
                ),
                chordPassage(
                    id = "hold-and-sing",
                    title = "Hold and sing",
                    right = "C4q D4q E4q D4q | E4q F4q G4h | G4q F4q E4q D4q | C4w",
                    left = "$C_W | $F_W | $G_W | $C_W",
                    tempoBpm = 60,
                    skills = setOf(Skills.MELODY_AND_CHORDS.id),
                    rights = exerciseRights("a five-note tune over whole-note chords"),
                ),
                chordPassage(
                    id = "lightly-row-with-chords",
                    title = "Lightly Row with chords",
                    right =
                        "G4q E4q E4h | F4q D4q D4h | C4q D4q E4q F4q | G4q G4q G4h | " +
                            "G4q E4q E4h | F4q D4q D4h | C4q E4q G4q G4q | C4w",
                    left = "$C_W | $G_W | $C_W | $C_W | $C_W | $G_W | $C_H $G_H | $C_W",
                    tempoBpm = 72,
                    skills = setOf(Skills.MELODY_AND_CHORDS.id, Skills.G_AND_F_CHORDS.id),
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
                    skills = setOf(Skills.MELODY_AND_CHORDS.id, Skills.G_AND_F_CHORDS.id),
                    rights = odeToJoyRights(),
                    piece = true,
                ),
                chordPassage(
                    id = "sharp-phrase",
                    title = "Sharp phrase",
                    right = "C4q C#4q D4q E4q | F#4q G4q F#4q E4q | D4q C#4q C4h",
                    left = "$C_W | $C_W | $C_W",
                    tempoBpm = 66,
                    skills = setOf(Skills.SHARPS_AND_FLATS.id),
                    rights = exerciseRights("a five-finger phrase with C sharp and F sharp"),
                ),
            ),
    )

internal const val C_TRIAD = "C3 E3 G3"
internal const val F_TRIAD = "F2 A2 C3"
internal const val F_FIRST_INVERSION = "A2 C3 F3"
internal const val G_TRIAD = "G2 B2 D3"
internal const val G_FIRST_INVERSION = "B2 D3 G3"
internal const val A_MINOR_TRIAD = "A2 C3 E3"

internal const val C_W = "[$C_TRIAD]w"
internal const val F_W = "[$F_TRIAD]w"
internal const val G_W = "[$G_TRIAD]w"
internal const val G1_W = "[$G_FIRST_INVERSION]w"
internal const val AM_W = "[$A_MINOR_TRIAD]w"
internal const val C_Q = "[$C_TRIAD]q"
internal const val C_H = "[$C_TRIAD]h"
internal const val F_H = "[$F_TRIAD]h"
internal const val G_H = "[$G_TRIAD]h"
internal const val F1_H = "[$F_FIRST_INVERSION]h"
internal const val G1_H = "[$G_FIRST_INVERSION]h"
internal const val AM_H = "[$A_MINOR_TRIAD]h"

/**
 * A 4/4 passage with left-hand chords ([left], note text) and, when given, a right-hand melody ([right], note text of
 * the same length). Steps that start together merge, so a chord and the melody note it starts with are one step; each
 * note keeps its own length, so a chord can be held while the melody moves.
 */
internal fun chordPassage(
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
