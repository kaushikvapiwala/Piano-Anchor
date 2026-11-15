package pianoanchor.engine.content

import pianoanchor.core.model.Skill
import pianoanchor.core.model.Track

/** The ear-training skills. Each needs the one before it; the echoes also need a playing skill. */
public object EarSkills {
    /** Playing back one note heard, on the exact key. */
    public val ECHO_NOTE: Skill =
        Skill("ear-echo-note", "Echo one note", setOf(Skills.KB_MIDDLE_C.id), Track.EAR)

    /** Telling whether the second of two notes is higher or lower. */
    public val HIGHER_LOWER: Skill =
        Skill("ear-higher-lower", "Higher or lower", setOf(ECHO_NOTE.id), Track.EAR)

    /** Telling a step from a skip. */
    public val STEP_SKIP: Skill = Skill("ear-step-skip", "Step or skip", setOf(HIGHER_LOWER.id), Track.EAR)

    /** Playing back three notes heard, in any octave. */
    public val ECHO_THREE: Skill =
        Skill("ear-echo-three", "Echo three notes", setOf(STEP_SKIP.id, Skills.RH_C_POSITION.id), Track.EAR)

    /** Telling a major third from a minor third. */
    public val THIRDS: Skill = Skill("ear-thirds", "Major or minor third", setOf(ECHO_THREE.id), Track.EAR)

    /** Telling a fourth from a fifth. */
    public val FOURTH_FIFTH: Skill = Skill("ear-fourth-fifth", "Fourth or fifth", setOf(THIRDS.id), Track.EAR)

    /** Telling a major chord from a minor one. */
    public val CHORD_QUALITY: Skill =
        Skill("ear-chord-quality", "Major or minor chord", setOf(FOURTH_FIFTH.id), Track.EAR)

    /** Playing back five notes heard, in any octave. */
    public val ECHO_FIVE: Skill =
        Skill("ear-echo-five", "Echo five notes", setOf(CHORD_QUALITY.id, Skills.RH_SKIPS.id), Track.EAR)

    /** All eight, in teaching order. */
    public val all: List<Skill> =
        listOf(ECHO_NOTE, HIGHER_LOWER, STEP_SKIP, ECHO_THREE, THIRDS, FOURTH_FIFTH, CHORD_QUALITY, ECHO_FIVE)
}
