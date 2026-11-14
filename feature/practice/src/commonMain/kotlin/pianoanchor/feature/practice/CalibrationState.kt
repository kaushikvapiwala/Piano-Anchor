package pianoanchor.feature.practice

import pianoanchor.engine.Tunables
import pianoanchor.engine.calibration.CalibrationResult

/**
 * What the calibration screen shows. [phase] is where the run is; [click] is how many clicks have sounded (0 before
 * the first) and [pulse] is true for half a beat after each; [countedTaps] are the taps answering the clicks that
 * count (after the warm-up), out of [COUNTED_CLICKS]. [result] is what the finished run found; [saveFailed] is true
 * when an accepted offset could not be stored; [disconnected] is true when the piano went away and the run was dropped.
 */
public data class CalibrationState(
    val phase: CalibrationPhase = CalibrationPhase.READY,
    val click: Int = 0,
    val pulse: Boolean = false,
    val countedTaps: Int = 0,
    val result: CalibrationResult? = null,
    val saveFailed: Boolean = false,
    val disconnected: Boolean = false,
) {
    /** True while the clicks sounded so far are all warm-up (shown as "get ready"). */
    val warmingUp: Boolean get() = click <= Tunables.CALIBRATION_WARMUP_CLICKS

    public companion object {
        /** The clicks whose taps count: all but the warm-up. */
        public const val COUNTED_CLICKS: Int = Tunables.CALIBRATION_CLICKS - Tunables.CALIBRATION_WARMUP_CLICKS
    }
}

/** Where a calibration is. */
public enum class CalibrationPhase {
    /** Waiting for Start (also after a dropped piano). */
    READY,

    /** The clicks are playing. */
    RUNNING,

    /** The run is over: [CalibrationState.result] says how it went. */
    DONE,
}
