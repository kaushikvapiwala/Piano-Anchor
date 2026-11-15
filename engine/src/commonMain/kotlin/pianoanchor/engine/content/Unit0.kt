package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit

/** Unit 0, "Find your way": keyboard geography and the right hand in C position. */
internal val UNIT_0: CurriculumUnit =
    CurriculumUnit(
        id = "unit-0",
        title = "Find your way",
        skills =
            listOf(
                Skills.KB_BLACK_GROUPS.id,
                Skills.KB_FIND_C.id,
                Skills.KB_MIDDLE_C.id,
                Skills.RH_C_POSITION.id,
                Skills.RHYTHM_QUARTER.id,
            ),
        passages =
            listOf(
                builtinPassage(
                    id = "twos-and-threes",
                    title = "Twos and threes",
                    notes = "C#4q D#4q F#4q G#4q | A#4q C#5q D#5q F#5q | G#5q A#5q rh",
                    tempoBpm = 50,
                    skills = setOf(Skills.KB_BLACK_GROUPS.id),
                    rights = exerciseRights("every black key in the groups of two and threes over two octaves"),
                ),
                builtinPassage(
                    id = "twos-and-threes-down",
                    title = "Twos and threes, coming down",
                    notes = "A#5q G#5q F#5q D#5q | C#5q A#4q G#4q F#4q | D#4q C#4q rh",
                    tempoBpm = 56,
                    skills = setOf(Skills.KB_BLACK_GROUPS.id),
                    rights = exerciseRights("the black key groups, played from the top down"),
                ),
                builtinPassage(
                    id = "all-the-cs",
                    title = "All the Cs",
                    notes = "C3w | C4w | C5w",
                    tempoBpm = 50,
                    skills = setOf(Skills.KB_FIND_C.id),
                    rights = exerciseRights("finding C3, C4 and C5, one whole note each"),
                ),
                builtinPassage(
                    id = "cs-up-and-down",
                    title = "Cs up and down",
                    notes = "C3q C4q C5q C4q | C3h rh",
                    tempoBpm = 56,
                    skills = setOf(Skills.KB_FIND_C.id),
                    rights = exerciseRights("jumping between the three Cs"),
                ),
                builtinPassage(
                    id = "middle-c-march",
                    title = "Middle C march",
                    notes = "C4q C4q C4q C4q | C4q C4q C4h",
                    tempoBpm = 60,
                    skills = setOf(Skills.KB_MIDDLE_C.id),
                    rights = exerciseRights("playing middle C again and again"),
                ),
                builtinPassage(
                    id = "which-c-is-middle",
                    title = "Which C is middle?",
                    notes = "C5q C4q C3q C4q | C4h C4h",
                    tempoBpm = 60,
                    skills = setOf(Skills.KB_MIDDLE_C.id, Skills.KB_FIND_C.id),
                    rights = exerciseRights("picking middle C out from the other Cs"),
                ),
                Passages.CDE,
                builtinPassage(
                    id = "five-finger-walk",
                    title = "Five-finger walk",
                    notes = "C4q D4q E4q F4q | G4q F4q E4q D4q | C4w",
                    tempoBpm = 66,
                    skills = setOf(Skills.RH_C_POSITION.id, Skills.RHYTHM_QUARTER.id),
                    rights = exerciseRights("walking C to G and back with the right hand"),
                ),
                builtinPassage(
                    id = "steady-quarters",
                    title = "Steady quarters",
                    notes = "C4q C4q G4q G4q | E4q E4q C4h",
                    tempoBpm = 72,
                    skills = setOf(Skills.RHYTHM_QUARTER.id),
                    rights = exerciseRights("a steady quarter-note beat"),
                ),
            ),
    )
