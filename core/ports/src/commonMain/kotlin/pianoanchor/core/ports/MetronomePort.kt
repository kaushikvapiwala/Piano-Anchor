package pianoanchor.core.ports

/**
 * Driven port: an audible beat. Times are in the [ClockPort] domain (monotonic nanoseconds).
 * Contract: `pianoanchor.testing.MetronomePortContractTest`.
 */
public interface MetronomePort {
    /**
     * Clicks at [firstBeatNanos] and every [beatIntervalNanos] after it until [stop], accenting every [beatsPerBar]th
     * beat starting with the first. Replaces a running pattern. Beats already in the past are skipped, keeping the
     * phase. Throws IllegalArgumentException when [beatIntervalNanos] <= 0 or [beatsPerBar] < 1.
     *
     * Audio is queued a little ahead, so a beat within a few tens of milliseconds of the call may be skipped or still
     * follow the replaced pattern: start a pattern well ahead of its first beat (a count-in leaves room for that).
     */
    public suspend fun start(
        firstBeatNanos: Long,
        beatIntervalNanos: Long,
        beatsPerBar: Int,
    )

    /** Stops clicking; a no-op when idle. */
    public suspend fun stop()
}
