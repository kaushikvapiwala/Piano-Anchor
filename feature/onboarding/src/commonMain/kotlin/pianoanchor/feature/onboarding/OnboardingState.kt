package pianoanchor.feature.onboarding

import pianoanchor.core.model.MidiDevice

/** Misses after which the echo offers Skip. */
public const val ECHO_MISSES_BEFORE_SKIP: Int = 3

/** The steps of the first-launch onboarding, in order. */
public enum class OnboardingStep {
    /** "Press any key on your piano". */
    CONNECT,

    /** The piano plays C-E-G and the learner echoes it. */
    ECHO,

    /** The tap calibration (the screen embeds `CalibrationScreen`). */
    CALIBRATE,

    /** Unit 0 or "I can already play". */
    START_POINT,
}

/** Where the echo step is. */
public enum class EchoPhase {
    /** The piano is playing C-E-G. */
    PLAYING,

    /** Waiting for the learner's notes (the piano has played, or has no MIDI output). */
    LISTENING,

    /** The last attempt didn't match. */
    MISSED,

    /** The learner echoed C-E-G. */
    PASSED,
}

/** Where the learner starts: the beginning, or after the first [unitsPlaced] units, whose skills count as learned. */
public enum class StartPoint(
    public val unitsPlaced: Int,
) {
    /** Unit 0, nothing placed. */
    BEGINNING(0),

    /** Unit 1: Unit 0's skills are placed. */
    UNIT_1(1),

    /** Unit 2: the skills of Units 0 and 1 are placed. */
    UNIT_2(2),
}

/**
 * What the onboarding shows. [device] is the piano that sent the first note (null while searching);
 * [pianoListHint] is true once nothing has come for `Tunables.ONBOARDING_CONNECT_HINT_MS`. In the echo step [echo] is
 * where it is, [keysHighlighted] is true when the piano can't play C-E-G itself (no MIDI output) so the screen shows
 * the keys instead, and [misses] counts the attempts that didn't match. [storageFailed] is true when the start point
 * could not be saved; [finished] is true once it was and the app can open Today.
 */
public data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.CONNECT,
    val device: MidiDevice? = null,
    val pianoListHint: Boolean = false,
    val echo: EchoPhase = EchoPhase.PLAYING,
    val keysHighlighted: Boolean = false,
    val misses: Int = 0,
    val storageFailed: Boolean = false,
    val finished: Boolean = false,
) {
    /** True once the echo has been missed often enough to offer Skip. */
    val canSkipEcho: Boolean get() = misses >= ECHO_MISSES_BEFORE_SKIP
}
