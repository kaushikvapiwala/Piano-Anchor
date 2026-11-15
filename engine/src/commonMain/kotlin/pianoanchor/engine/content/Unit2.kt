package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Rights
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.Step

/** Unit 2, "Both hands": left hand in C position, hands taking turns, whole notes, 3/4. */
internal val UNIT_2: CurriculumUnit =
    CurriculumUnit(
        id = "unit-2",
        title = "Both hands",
        skills =
            listOf(
                Skills.LH_C_POSITION.id,
                Skills.LH_STEPS.id,
                Skills.RHYTHM_WHOLE.id,
                Skills.HANDS_TURNS.id,
                Skills.TIME_THREE_FOUR.id,
                Skills.HANDS_HELD_BASS.id,
            ),
        passages =
            listOf(
                builtinPassage(
                    id = "left-hand-walk",
                    title = "Left hand walk",
                    notes = "C3q D3q E3q F3q | G3q F3q E3q D3q | C3w",
                    tempoBpm = 66,
                    skills = setOf(Skills.LH_C_POSITION.id),
                    rights = exerciseRights("walking C to G and back with the left hand"),
                ),
                builtinPassage(
                    id = "left-hand-steps",
                    title = "Left hand steps",
                    notes = "C3q D3q E3q D3q | E3q F3q G3q F3q | E3q D3q C3h",
                    tempoBpm = 66,
                    skills = setOf(Skills.LH_STEPS.id),
                    rights = exerciseRights("left-hand steps up and down"),
                ),
                builtinPassage(
                    id = "long-notes",
                    title = "Long notes",
                    notes = "C4w | E4h D4h | C4w | G4h F4h | E4w",
                    tempoBpm = 60,
                    skills = setOf(Skills.RHYTHM_WHOLE.id),
                    rights = exerciseRights("whole notes and half notes in the right hand"),
                ),
                builtinPassage(
                    id = "low-whole-notes",
                    title = "Low whole notes",
                    notes = "C3w | G3w | E3w | C3w",
                    tempoBpm = 60,
                    skills = setOf(Skills.RHYTHM_WHOLE.id, Skills.LH_C_POSITION.id),
                    rights = exerciseRights("whole notes in the left hand"),
                ),
                builtinPassage(
                    id = "turn-taking",
                    title = "Turn-taking",
                    notes = "C4q D4q E4q F4q | G3q F3q E3q D3q | E4q F4q G4q F4q | E3q D3q C3h",
                    tempoBpm = 66,
                    skills = setOf(Skills.HANDS_TURNS.id),
                    rights = exerciseRights("the right hand and the left hand taking turns"),
                ),
                builtinPassage(
                    id = "echo",
                    title = "Echo",
                    notes = "C4h E4h | C3h E3h | G4h E4h | G3h C3h",
                    tempoBpm = 60,
                    skills = setOf(Skills.HANDS_TURNS.id, Skills.RHYTHM_HALF.id),
                    rights = exerciseRights("the left hand answering the right, an octave lower"),
                ),
                builtinPassage(
                    id = "waltz-in-c",
                    title = "Waltz in C",
                    notes =
                        "C4q E4q G4q | G4q E4q C4q | D4q F4q E4q | D4h. | " +
                            "C4q E4q G4q | E4q C4q E4q | D4q E4q D4q | C4h.",
                    tempoBpm = 72,
                    skills = setOf(Skills.TIME_THREE_FOUR.id),
                    rights = exerciseRights("counting three beats to the bar"),
                    timeSignature = THREE_FOUR,
                ),
                builtinPassage(
                    id = "three-beat-steps",
                    title = "Three-beat steps",
                    notes = "C4q D4q E4q | E4h. | F4q E4q D4q | C4h.",
                    tempoBpm = 66,
                    skills = setOf(Skills.TIME_THREE_FOUR.id, Skills.RHYTHM_HALF.id),
                    rights = exerciseRights("steps and a dotted half note in 3/4"),
                    timeSignature = THREE_FOUR,
                ),
                heldBassPassage(
                    id = "held-bass",
                    title = "Hold and play",
                    tune = "C4q E4q G4q E4q | D4q F4q G4q F4q | E4q D4q C4q D4q | C4w",
                    bass = listOf(C3, G3, G3, C3),
                    tempoBpm = 60,
                    rights = exerciseRights("a left-hand whole note held under a right-hand line"),
                ),
                heldBassPassage(
                    id = "ode-to-joy-held-bass",
                    title = "Ode to Joy with a held bass",
                    tune =
                        "E4q E4q F4q G4q | G4q F4q E4q D4q | C4q C4q D4q E4q | E4q. D4e D4h | " +
                            "E4q E4q F4q G4q | G4q F4q E4q D4q | C4q C4q D4q E4q | D4q. C4e C4h",
                    bass = listOf(C3, G3, C3, G3, C3, G3, C3, C3),
                    tempoBpm = 66,
                    rights = odeToJoyRights(),
                    piece = true,
                ),
            ),
    )

private const val C3 = 48
private const val G3 = 55

/**
 * A 4/4 right-hand [tune] over a left-hand whole note per bar ([bass], MIDI pitches). Each whole note joins the step
 * at the start of its bar as a chord with the right-hand note; later steps stay right hand only.
 */
private fun heldBassPassage(
    id: String,
    title: String,
    tune: String,
    bass: List<Int>,
    tempoBpm: Int,
    rights: Rights,
    piece: Boolean = false,
): Passage {
    val skills = setOf(Skills.HANDS_HELD_BASS.id, Skills.RHYTHM_WHOLE.id)
    val tuneOnly = builtinPassage(id, title, tune, tempoBpm, skills, rights, piece = piece)
    val barTicks = tuneOnly.timeSignature.barTicks
    val steps =
        tuneOnly.steps.map { step ->
            val bar = step.onsetTicks / barTicks
            if (step.onsetTicks % barTicks == 0) {
                Step(step.onsetTicks, step.notes + ScoreNote(MidiNote(bass[bar]), barTicks))
            } else {
                step
            }
        }
    return tuneOnly.copy(steps = steps)
}
