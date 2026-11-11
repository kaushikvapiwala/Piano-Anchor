package pianoanchor.core.ports

/**
 * Driven port: a monotonic time source in nanoseconds.
 *
 * All engine timing (matching, grading, scheduling within a session) uses one clock domain.
 * Adapters must never go backwards. Wall-clock time is not provided here.
 * Contract: `pianoanchor.testing.ClockPortContractTest`.
 */
public interface ClockPort {
    /** Current monotonic time in nanoseconds; only differences between readings are meaningful. */
    public fun nowNanos(): Long
}
