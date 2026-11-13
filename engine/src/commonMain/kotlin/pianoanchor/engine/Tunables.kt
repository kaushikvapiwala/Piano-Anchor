package pianoanchor.engine

/**
 * Every threshold the engine uses lives here, each with "Why:" and "Source:" in its KDoc.
 * Values are priors: tune them from the learner's own data.
 */
public object Tunables {
    /**
     * Notes whose onsets fall within this many milliseconds of a cluster's first note form one chord.
     *
     * Why: a chord is one physical gesture, so the window is absolute (not tempo-scaled, not widened
     * for beginners).
     *
     * Source: practice-mode scorers group chord tones within 50–90 ms; score-alignment research uses a
     * tighter 35 ms simultaneity threshold (Nakamura et al., ISMIR 2017). 90 ms suits beginners.
     */
    public const val CHORD_CLUSTER_MS: Int = 90

    /**
     * Largest System Exclusive payload (bytes between 0xF0 and 0xF7) the MIDI parser keeps; longer ones are discarded.
     *
     * Why: bounds memory if a device never sends 0xF7.
     *
     * Source: MIDI 1.0 sets no SysEx length limit; 64 KiB is far above any message the app reads (identity
     * replies and parameter reads are tens of bytes).
     */
    public const val SYSEX_MAX_BYTES: Int = 65_536

    /**
     * How long a Bluetooth LE MIDI scan runs, in milliseconds, before it stops by itself.
     *
     * Why: a piano that is on and free advertises often, so a short scan finds it; stopping early saves battery.
     *
     * Source: a surveyed open-source BLE MIDI app scans for 12 s; revisit after the T8 soak test.
     */
    public const val BLE_SCAN_TIMEOUT_MS: Long = 12_000

    /**
     * Wait before the first attempt to reopen a dropped Bluetooth LE MIDI device, in milliseconds; each further
     * attempt waits twice as long, up to [BLE_RECONNECT_MAX_MS].
     *
     * Why: a brief radio drop should recover quickly, without retrying in a tight loop.
     *
     * Source: initial guess; revisit after the T8 60-minute soak test.
     */
    public const val BLE_RECONNECT_INITIAL_MS: Long = 1_000

    /**
     * Longest wait between attempts to reopen a dropped Bluetooth LE MIDI device, in milliseconds.
     *
     * Why: caps the backoff so a piano that comes back is found soon, without hammering the Bluetooth stack.
     *
     * Source: initial guess; revisit after the T8 soak test.
     */
    public const val BLE_RECONNECT_MAX_MS: Long = 30_000

    /**
     * Total backoff waiting, in milliseconds, after which the adapter stops trying to reopen a dropped Bluetooth LE
     * MIDI device: no attempt starts once the waits since the drop would add up to more than this (the time the
     * attempts themselves take is not counted).
     *
     * Why: covers briefly walking away from the piano, then stops draining the battery.
     *
     * Source: initial guess; revisit after the T8 soak test.
     */
    public const val BLE_RECONNECT_GIVE_UP_MS: Long = 300_000

    /**
     * How long, in milliseconds, a Bluetooth LE MIDI device that Android reports as opened may take to show up in the
     * device list; if it doesn't, the open counts as failed and the handle is closed.
     *
     * Why: Android lists the device before it hands back the opened device, so this only bounds the rare case of a
     * link that drops in between; without it `connect` could wait forever.
     *
     * Source: initial guess, far above the expected delay of one main-looper callback; revisit after the T8 test.
     */
    public const val BLE_LISTED_TIMEOUT_MS: Long = 5_000

    /**
     * How many lines the note monitor keeps in its live log (newest first); older lines are dropped.
     *
     * Why: enough to read back the last phrase played, while bounding the work of redrawing the log.
     *
     * Source: UI choice agreed in the note-monitor grill (2026-11-11).
     */
    public const val MONITOR_LOG_LINES: Int = 50

    /**
     * Longest note-monitor recording, in milliseconds after its first event; recording stops there with a notice.
     *
     * Why: bounds memory (a few MB of messages at most) while a fixture session lasts minutes.
     *
     * Source: agreed in the note-monitor grill (2026-11-11).
     */
    public const val MONITOR_RECORDING_MAX_MS: Long = 1_800_000
}
