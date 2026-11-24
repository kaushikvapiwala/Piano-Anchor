package pianoanchor.feature.onboarding

import pianoanchor.core.model.MidiDevice
import pianoanchor.engine.Tunables

/** The steps of the first-launch onboarding, in order. */
public enum class OnboardingStep {
    /** "Press any key on your piano". */
    CONNECT,

    /** The piano plays C-E-G and the learner echoes it. */
    ECHO,

    /** "Test me" (the played placement test) or "Start from the beginning". */
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

/**
 * The piece of the placement test being played (or just played): [number] of [total] (1-based, "Piece 1 of 4"), the
 * [passageId] to host, and its [title].
 */
public data class PlacementPiece(
    val number: Int,
    val total: Int,
    val passageId: String,
    val title: String,
)

/** What the learner is told between two pieces of the placement test. */
public enum class PlacementVerdict {
    /** The piece passed and another follows: "Passed, next piece". */
    PASSED,

    /** The test is over: "That's where we'll start". */
    START_HERE,
}

/**
 * What the onboarding shows. [device] is the piano that sent the first note (null while searching); while searching,
 * [listed] is a connected piano that hasn't sent a note yet (null when none is listed), so the screen can say it is
 * connected and only waits for a key; [pianoListHint] is true once nothing has come for
 * `Tunables.ONBOARDING_CONNECT_HINT_MS`. In the echo step [echo] is where it is, [keysHighlighted] is true when the
 * piano can't play C-E-G itself (no MIDI output) so the screen shows the keys instead, and [misses] counts the attempts
 * that didn't match. [storageFailed] is true when the start point could not be saved; [finished] is true once it was
 * and the app can open Today. In the start step, [piece] is the piece of the placement test under way (null before
 * "Test me"): while [verdict] is null the app hosts a run of it, then [verdict] says how it went.
 */
public data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.CONNECT,
    val device: MidiDevice? = null,
    val listed: MidiDevice? = null,
    val pianoListHint: Boolean = false,
    val echo: EchoPhase = EchoPhase.PLAYING,
    val keysHighlighted: Boolean = false,
    val misses: Int = 0,
    val storageFailed: Boolean = false,
    val finished: Boolean = false,
    val piece: PlacementPiece? = null,
    val verdict: PlacementVerdict? = null,
) {
    /** True once the echo has been missed often enough to offer Skip. */
    val canSkipEcho: Boolean get() = misses >= Tunables.ONBOARDING_MISSES_BEFORE_SKIP
}
