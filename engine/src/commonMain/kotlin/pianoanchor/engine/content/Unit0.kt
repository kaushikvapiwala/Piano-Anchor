package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit

/** Unit 0, "Meet the piano": hand shape, the keyboard's landmarks, a steady beat and note lengths; no staff. */
internal val UNIT_0: CurriculumUnit =
    CurriculumUnit(
        id = "unit-0",
        title = "Meet the piano",
        skills =
            listOf(
                Skills.HAND_SHAPE.id,
                Skills.MIDDLE_C.id,
                Skills.BLACK_KEY_GROUPS.id,
                Skills.HIGH_AND_LOW.id,
                Skills.STEADY_BEAT.id,
                Skills.QUARTER_AND_HALF.id,
                Skills.WHITE_KEY_NAMES.id,
            ),
        passages =
            listOf(
                builtinPassage(
                    id = "five-fingers",
                    title = "Five fingers",
                    notes = "C4h D4h | E4h F4h | G4w",
                    tempoBpm = 50,
                    skills = setOf(Skills.HAND_SHAPE.id),
                    rights = exerciseRights("fingers 1 to 5 on C to G, one key each, played slowly"),
                ),
                builtinPassage(
                    id = "middle-c-march",
                    title = "Middle C march",
                    notes = "C4q C4q C4q C4q | C4q C4q C4h",
                    tempoBpm = 60,
                    skills = setOf(Skills.MIDDLE_C.id),
                    rights = exerciseRights("playing middle C again and again"),
                ),
                builtinPassage(
                    id = "twos-and-threes",
                    title = "Twos and threes",
                    notes = "C#4q D#4q F#4q G#4q | A#4q C#5q D#5q F#5q | G#5q A#5q rh",
                    tempoBpm = 50,
                    skills = setOf(Skills.BLACK_KEY_GROUPS.id),
                    rights = exerciseRights("every black key in the groups of two and three over two octaves"),
                ),
                builtinPassage(
                    id = "twos-and-threes-down",
                    title = "Twos and threes, coming down",
                    notes = "A#5q G#5q F#5q D#5q | C#5q A#4q G#4q F#4q | D#4q C#4q rh",
                    tempoBpm = 56,
                    skills = setOf(Skills.BLACK_KEY_GROUPS.id),
                    rights = exerciseRights("the black key groups, played from the top down"),
                ),
                builtinPassage(
                    id = "which-c-is-middle",
                    title = "Which C is middle?",
                    notes = "C5q C4q C3q C4q | C4h C4h",
                    tempoBpm = 60,
                    skills = setOf(Skills.BLACK_KEY_GROUPS.id),
                    rights = exerciseRights("picking middle C out from the other Cs"),
                ),
                builtinPassage(
                    id = "all-the-cs",
                    title = "All the Cs",
                    notes = "C3w | C4w | C5w",
                    tempoBpm = 50,
                    skills = setOf(Skills.HIGH_AND_LOW.id),
                    rights = exerciseRights("finding C3, C4 and C5, one whole note each"),
                ),
                builtinPassage(
                    id = "cs-up-and-down",
                    title = "Cs up and down",
                    notes = "C3q C4q C5q C4q | C3h rh",
                    tempoBpm = 56,
                    skills = setOf(Skills.HIGH_AND_LOW.id),
                    rights = exerciseRights("jumping between the three Cs"),
                ),
                builtinPassage(
                    id = "steady-quarters",
                    title = "Steady quarters",
                    notes = "C4q C4q G4q G4q | E4q E4q C4h",
                    tempoBpm = 72,
                    skills = setOf(Skills.STEADY_BEAT.id),
                    rights = exerciseRights("a steady quarter-note beat"),
                ),
                builtinPassage(
                    id = "half-and-quarter",
                    title = "Half and quarter",
                    notes = "C4h E4h | G4q E4q C4h | D4h F4h | E4q D4q C4h",
                    tempoBpm = 66,
                    skills = setOf(Skills.QUARTER_AND_HALF.id),
                    rights = exerciseRights("mixing half notes and quarter notes"),
                ),
                builtinPassage(
                    id = "every-f-every-b",
                    title = "Every F, every B",
                    notes = "F3q F4q F5q F4q | B3q B4q B5q B4q",
                    tempoBpm = 56,
                    skills = setOf(Skills.WHITE_KEY_NAMES.id),
                    rights = exerciseRights("finding every F and then every B across the keyboard"),
                ),
            ),
    )
