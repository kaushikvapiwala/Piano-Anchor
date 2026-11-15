package pianoanchor.engine.content

import pianoanchor.core.model.CurriculumUnit

/** Unit 1, "Five-finger tunes": right-hand C-position melodies, quarter and half notes in 4/4, a steady beat. */
internal val UNIT_1: CurriculumUnit =
    CurriculumUnit(
        id = "unit-1",
        title = "Five-finger tunes",
        skills =
            listOf(
                Skills.RH_STEPS.id,
                Skills.RH_SKIPS.id,
                Skills.RHYTHM_HALF.id,
                Skills.RH_TUNES.id,
                Skills.TEMPO_STEADY.id,
            ),
        passages =
            listOf(
                builtinPassage(
                    id = "step-up-step-down",
                    title = "Step up, step down",
                    notes = "C4q D4q E4q D4q | C4q D4q E4q F4q | G4q F4q E4q D4q | C4w",
                    tempoBpm = 66,
                    skills = setOf(Skills.RH_STEPS.id),
                    rights = exerciseRights("moving by step up and down the five fingers"),
                ),
                builtinPassage(
                    id = "hot-cross-buns",
                    title = "Hot Cross Buns",
                    notes = "E4q D4q C4h | E4q D4q C4h | C4q C4q C4q C4q | D4q D4q D4q D4q | E4q D4q C4h",
                    tempoBpm = 72,
                    skills = setOf(Skills.RH_STEPS.id, Skills.RHYTHM_HALF.id),
                    rights =
                        publicDomainRights(
                            origin = "Hot Cross Buns, traditional English street cry, 18th century or earlier",
                            basis =
                                "Traditional with no known author, in print by the 18th century, so any copyright " +
                                    "has long expired. The notes are our own arrangement in right-hand C position.",
                        ),
                ),
                builtinPassage(
                    id = "skipping-stones",
                    title = "Skipping stones",
                    notes = "C4q E4q G4q E4q | C4q E4q G4h | G4q E4q C4q E4q | G4q E4q C4h",
                    tempoBpm = 66,
                    skills = setOf(Skills.RH_SKIPS.id),
                    rights = exerciseRights("skipping from C to E to G"),
                ),
                builtinPassage(
                    id = "steady-skipping",
                    title = "Steady skipping",
                    notes = "C4q E4q G4q E4q | D4q F4q E4q C4q | G4q E4q F4q D4q | C4w",
                    tempoBpm = 88,
                    skills = setOf(Skills.RH_SKIPS.id, Skills.TEMPO_STEADY.id),
                    rights = exerciseRights("skips and steps at a brisker, steady pace"),
                ),
                builtinPassage(
                    id = "half-and-quarter",
                    title = "Half and quarter",
                    notes = "C4h E4h | G4q E4q C4h | D4h F4h | E4q D4q C4h",
                    tempoBpm = 66,
                    skills = setOf(Skills.RHYTHM_HALF.id),
                    rights = exerciseRights("mixing half notes and quarter notes"),
                ),
                builtinPassage(
                    id = "mary-had-a-little-lamb",
                    title = "Mary Had a Little Lamb",
                    notes =
                        "E4q D4q C4q D4q | E4q E4q E4h | D4q D4q D4h | E4q G4q G4h | " +
                            "E4q D4q C4q D4q | E4q E4q E4q E4q | D4q D4q E4q D4q | C4w",
                    tempoBpm = 80,
                    skills = setOf(Skills.RH_TUNES.id, Skills.RH_STEPS.id, Skills.RH_SKIPS.id, Skills.RHYTHM_HALF.id),
                    rights =
                        publicDomainRights(
                            origin =
                                "Mary Had a Little Lamb: words by Sarah Josepha Hale (1830), tune traditional " +
                                    "American, 19th century",
                            basis =
                                "Hale died in 1879 and the tune is traditional with no known author, so both are " +
                                    "public domain. The notes are our own arrangement in right-hand C position.",
                        ),
                ),
                builtinPassage(
                    id = "au-clair-de-la-lune",
                    title = "Au clair de la lune",
                    notes = "C4q C4q C4q D4q | E4h D4h | C4q E4q D4q D4q | C4w",
                    tempoBpm = 72,
                    skills = setOf(Skills.RH_TUNES.id, Skills.RH_STEPS.id, Skills.RHYTHM_HALF.id),
                    rights =
                        publicDomainRights(
                            origin = "Au clair de la lune, traditional French song, 18th century",
                            basis =
                                "Traditional with no known author, 18th century, so any copyright has long " +
                                    "expired. The notes are our own arrangement in right-hand C position.",
                        ),
                ),
                builtinPassage(
                    id = "lightly-row",
                    title = "Lightly Row",
                    notes =
                        "G4q E4q E4h | F4q D4q D4h | C4q D4q E4q F4q | G4q G4q G4h | " +
                            "G4q E4q E4h | F4q D4q D4h | C4q E4q G4q G4q | C4w",
                    tempoBpm = 76,
                    skills = setOf(Skills.RH_TUNES.id, Skills.RH_STEPS.id, Skills.RHYTHM_HALF.id),
                    rights =
                        publicDomainRights(
                            origin = "Lightly Row, from the traditional German song \"Hänschen klein\", 19th century",
                            basis =
                                "A traditional German children's song with no known composer, 19th century, so any " +
                                    "copyright has long expired. The notes are our own arrangement in right-hand " +
                                    "C position.",
                        ),
                ),
                builtinPassage(
                    id = "ode-to-joy",
                    title = "Ode to Joy",
                    notes =
                        "E4q E4q F4q G4q | G4q F4q E4q D4q | C4q C4q D4q E4q | E4q. D4e D4h | " +
                            "E4q E4q F4q G4q | G4q F4q E4q D4q | C4q C4q D4q E4q | D4q. C4e C4h",
                    tempoBpm = 72,
                    skills = setOf(Skills.RH_TUNES.id, Skills.RH_STEPS.id, Skills.RHYTHM_HALF.id),
                    rights = odeToJoyRights(),
                ),
            ),
    )

/** The rights record of the Ode to Joy theme; the arrangement in each passage is ours. */
internal fun odeToJoyRights() =
    publicDomainRights(
        origin = "Ode to Joy theme, Ludwig van Beethoven, Symphony No. 9 (1824)",
        basis =
            "Beethoven died in 1827, nearly 200 years ago, so the work is public domain. The notes are our own " +
                "arrangement in right-hand C position.",
    )
