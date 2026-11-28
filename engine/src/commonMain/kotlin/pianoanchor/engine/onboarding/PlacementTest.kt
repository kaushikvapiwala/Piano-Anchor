package pianoanchor.engine.onboarding

import pianoanchor.core.model.Band

/**
 * The played placement test of the onboarding (`docs/algorithms/learner-model.md`, "Placement"): one passage per unit
 * of the beginner course, played in order at first sight. The test stops at the first passage not passed and the
 * learner starts at that passage's unit. Pure: the same bands always give the same start unit.
 */
public object PlacementTest {
    /** The units the test covers: one passage each, Units 0 to 5. */
    private const val UNITS: Int = 6

    /** The test passages' ids, one per unit 0..5, in order. Each must exist in Curriculum (content test). */
    public val passageIds: List<String> =
        listOf(
            "builtin:all-the-cs",
            "builtin:mary-had-a-little-lamb",
            "builtin:held-bass",
            "builtin:ode-to-joy-with-chords",
            "builtin:jingle-bells",
            "builtin:go-tell-aunt-rhody",
        )

    /** True for GOOD and CLEAN. */
    public fun passes(band: Band): Boolean = band != Band.KEEP_PRACTISING

    /** After [bands] (in test order): the next passage index to play, or null when the test is over. */
    public fun next(bands: List<Band>): Int? = bands.size.takeIf { bands.all(::passes) && it < UNITS }

    /** The unit the learner starts at (0..6; 6 = course done) once [next] is null. */
    public fun startUnit(bands: List<Band>): Int = bands.indexOfFirst { !passes(it) }.takeIf { it >= 0 } ?: bands.size
}
