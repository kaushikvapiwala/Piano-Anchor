package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit
import pianoanchor.core.model.PedalSpan

/** The C chord in the left hand, a dotted half note (one 3/4 bar). */
internal const val C_DH = "[$C_TRIAD]h."

/**
 * The C chord in second inversion (G C E) in the left hand, a dotted half note: under the right hand's G3 it keeps
 * the same key out of one step twice (a step cannot hold a pitch twice).
 */
internal const val C2_DH = "[G2 C3 E3]h."

/** The F chord in second inversion (A C F) in the left hand, a dotted half note. */
internal const val F1_DH = "[$F_FIRST_INVERSION]h."

/** The G chord in first inversion (B D G) in the left hand, a dotted half note. */
internal const val G1_DH = "[$G_FIRST_INVERSION]h."

private const val F1_W = "[$F_FIRST_INVERSION]w"
private const val FOUR_BARS = 4
private const val AMAZING_GRACE_BARS = 15

/** One pedal span per bar for the bars [first] until [end] (0-based), each [barTicks] long. */
private fun barSpans(
    first: Int,
    end: Int,
    barTicks: Int,
): List<PedalSpan> = (first until end).map { PedalSpan(it * barTicks, (it + 1) * barTicks) }

private val FOUR_FOUR_BAR_TICKS = FOUR_FOUR.barTicks

/**
 * Unit 6, "Legato and the pedal": joined notes, the sustain pedal held and changed with the chord, ending with Amazing
 * Grace in 3/4, a pedal change every bar.
 */
internal val UNIT_6: CurriculumUnit =
    CurriculumUnit(
        id = "unit-6",
        title = "Legato and the pedal",
        skills =
            listOf(
                Skills.LEGATO_TOUCH.id,
                Skills.SUSTAIN_PEDAL.id,
                Skills.PEDAL_CHANGES.id,
                Skills.PEDAL_SONG.id,
            ),
        passages =
            listOf(
                builtinPassage(
                    id = "joined-steps",
                    title = "Joined steps",
                    notes = "C4h D4h | E4h F4h | G4h F4h | E4h D4h | C4w",
                    tempoBpm = 66,
                    skills = setOf(Skills.LEGATO_TOUCH.id),
                    rights = exerciseRights("joining each note to the next, with no gap between them"),
                ),
                chordPassage(
                    id = "ringing-chords",
                    title = "Ringing chords",
                    right = "E4q G4q C5h | rw | G4q E4q C4h | rw",
                    left = "$C_H rh | rw | $C_H rh | rw",
                    tempoBpm = 60,
                    skills = setOf(Skills.SUSTAIN_PEDAL.id),
                    rights = exerciseRights("the sustain pedal, which keeps the sound ringing through the rest bars"),
                    // A pedal mark may not pass the last bar with a note, so the second span stops at bar 3's end.
                    pedal =
                        listOf(
                            PedalSpan(0, 2 * FOUR_FOUR_BAR_TICKS),
                            PedalSpan(2 * FOUR_FOUR_BAR_TICKS, 3 * FOUR_FOUR_BAR_TICKS),
                        ),
                ),
                chordPassage(
                    id = "pedal-changes",
                    title = "Pedal changes",
                    right = "E4h G4h | F4h E4h | D4h F4h | E4w",
                    left = "$C_W | $F1_W | $G1_W | $C_W",
                    tempoBpm = 60,
                    skills = setOf(Skills.PEDAL_CHANGES.id),
                    rights = exerciseRights("changing the pedal once in each bar, with the chord"),
                    pedal = barSpans(0, FOUR_BARS, FOUR_FOUR_BAR_TICKS),
                ),
                chordPassage(
                    id = "amazing-grace",
                    title = "Amazing Grace",
                    right =
                        "rh G3q | C4h E4e C4e | E4h D4q | C4h A3q | G3h G3q | C4h E4e C4e | E4h D4q | G4h E4q | " +
                            "G4h E4e C4e | E4h D4q | C4h A3q | G3h G3q | C4h E4e C4e | E4h D4q | C4h.",
                    left =
                        "rh. | $C_DH | $C_DH | $F1_DH | $C2_DH | $C_DH | $C_DH | $G1_DH | $C_DH | $C_DH | " +
                            "$F1_DH | $C2_DH | $C_DH | $G1_DH | $C_DH",
                    tempoBpm = 72,
                    skills = setOf(Skills.PEDAL_SONG.id, Skills.PEDAL_CHANGES.id, Skills.LEGATO_TOUCH.id),
                    rights =
                        publicDomainRights(
                            origin =
                                "Amazing Grace, the hymn tune \"New Britain\", printed in 1829; the words are by " +
                                    "John Newton, 1779",
                            basis =
                                "A hymn tune printed in 1829 with words from 1779, so it is in the public domain. " +
                                    "This simplified melody and the left-hand chords are our own arrangement.",
                        ),
                    piece = true,
                    timeSignature = THREE_FOUR,
                    pedal = barSpans(1, AMAZING_GRACE_BARS, THREE_FOUR.barTicks),
                ),
            ),
    )
