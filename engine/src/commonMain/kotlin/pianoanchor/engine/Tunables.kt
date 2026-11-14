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
     * How long, in milliseconds after the selected device drops, the note monitor waits for it to be open again before
     * it stops reconnecting and reports the device as unavailable.
     *
     * Why: the monitor cannot see when the adapter stops retrying. The adapter's [BLE_RECONNECT_GIVE_UP_MS] counts only
     * its waits, not its attempts (about 13 with this backoff, each up to [BLE_LISTED_TIMEOUT_MS] plus Android's open
     * time), so the monitor waits twice as long. Giving up late only shows "Reconnecting…" longer; giving up early
     * would hide a piano that came back. A USB piano unplugged that long is reported too, instead of waiting forever.
     *
     * Source: derived from [BLE_RECONNECT_GIVE_UP_MS]; revisit once the T8 soak test measures how long attempts take.
     */
    public const val MONITOR_RECONNECT_GIVE_UP_MS: Long = 2 * BLE_RECONNECT_GIVE_UP_MS

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

    /**
     * How long, in milliseconds after sending the MIDI Identity Request, the app waits for the Identity Reply before
     * it stores the piano without an identity.
     *
     * Why: a piano that answers does so within tens of milliseconds, and a short wait keeps connecting snappy; many
     * Bluetooth pianos never answer, so the wait is paid often.
     *
     * Source: MIDI 1.0 sets no deadline; initial guess (slice 1.5 spec), revisit after the hardware check.
     */
    public const val IDENTITY_REPLY_TIMEOUT_MS: Long = 1_000

    /**
     * Lowest accuracy (0.0–1.0) of a wait-mode attempt graded `Band.CLEAN`.
     *
     * Why: "clean" should allow at most a rare slip on longer passages.
     *
     * Source: matches the M1 ≥ 95% classification bar; initial guess, tune from the learner's data.
     */
    public const val BAND_CLEAN_MIN_ACCURACY: Double = 0.95

    /**
     * Lowest accuracy (0.0–1.0) of a wait-mode attempt graded `Band.GOOD`; below it the band is `KEEP_PRACTISING`.
     *
     * Why: about one slip per five notes is still a run worth building on.
     *
     * Source: initial guess (slice 1.7 spec); tune from the learner's data.
     */
    public const val BAND_GOOD_MIN_ACCURACY: Double = 0.80

    /**
     * How long, in milliseconds, the practice screen's key strip flashes a pressed key as correct or wrong.
     *
     * Why: long enough to see which key was hit, short enough not to linger into the next note.
     *
     * Source: the Living Studio mock (slice 1.9 spec).
     */
    public const val KEY_FLASH_MS: Long = 260

    /**
     * Floor of the early/late band, in milliseconds: an onset further than max(this, [TIMING_TOLERANCE_BEAT_FRACTION]
     * of a beat) from where the tempo puts it is `EARLY` or `LATE`.
     *
     * Why: at fast tempos a fraction of a beat gets too tight for a beginner's hands; 60 ms stays lenient.
     *
     * Source: starting value, owner-agreed in the M1 grill; calibrated in M7.
     */
    public const val TIMING_TOLERANCE_MS: Long = 60

    /**
     * The early/late band as a fraction of a beat (a quarter note); the band is max([TIMING_TOLERANCE_MS], this).
     *
     * Why: at slow tempos the band grows with the beat; an eighth of a beat is clearly off, less is expressive.
     *
     * Source: starting value, owner-agreed in the M1 grill; calibrated in M7.
     */
    public const val TIMING_TOLERANCE_BEAT_FRACTION: Double = 0.125

    /**
     * A step whose gap from the previous step is at least this many times the expected gap (and at least
     * [HESITATION_MIN_MS]) is a `HESITATION`, not a late note.
     *
     * Why: a gap twice the expected one reads as a stop, not as a rushed or dragged note.
     *
     * Source: starting value, owner-agreed in the M1 grill; calibrated in M7.
     */
    public const val HESITATION_GAP_RATIO: Double = 2.0

    /**
     * Shortest gap before a step, in milliseconds, that can be a `HESITATION` (with [HESITATION_GAP_RATIO]).
     *
     * Why: at fast tempos a doubled gap is still short, and short gaps aren't stops.
     *
     * Source: starting value, owner-agreed in the M1 grill; calibrated in M7.
     */
    public const val HESITATION_MIN_MS: Long = 400

    /**
     * A softer key one or two semitones from a louder correct note, starting within this many milliseconds of it, is a
     * `MISTOUCH` (a brushed key), not an extra note.
     *
     * Why: a brushed neighbour key sounds almost together with the intended one.
     *
     * Source: starting value, owner-agreed in the M1 grill; calibrated in M7.
     */
    public const val MISTOUCH_WINDOW_MS: Long = 40

    /**
     * A note held (key down to key up) for less than this fraction of its written length is `TOO_SHORT`.
     *
     * Why: under half the written length is clearly clipped, not a phrasing choice.
     *
     * Source: starting value, owner-agreed in the M1 grill; calibrated in M7.
     */
    public const val NOTE_SHORT_RATIO: Double = 0.5

    /**
     * A note held (key down to key up) for more than this fraction of its written length is `TOO_LONG`.
     *
     * Why: over half again the written length is clearly held too long.
     *
     * Source: starting value, owner-agreed in the M1 grill; calibrated in M7.
     */
    public const val NOTE_LONG_RATIO: Double = 1.5

    /**
     * Length of one metronome click, in milliseconds: a sine burst that decays to near silence.
     *
     * Why: short enough to mark an instant, long enough to hear on phone speakers.
     *
     * Source: starting value; checked by ear on device.
     */
    public const val METRONOME_CLICK_MS: Long = 15

    /**
     * Pitch of the accented click on the first beat of each bar, in hertz.
     *
     * Why: higher than the other beats, so the bar can be heard.
     *
     * Source: starting value; checked by ear on device.
     */
    public const val METRONOME_ACCENT_HZ: Int = 1_500

    /**
     * Pitch of the click on every beat but the first of a bar, in hertz.
     *
     * Why: clear on small speakers.
     *
     * Source: starting value; checked by ear on device.
     */
    public const val METRONOME_BEAT_HZ: Int = 1_000

    /**
     * Length of each block of audio the metronome writes, in milliseconds.
     *
     * Why: a new pattern takes effect from the next block, so small blocks keep `start` and `stop` responsive.
     *
     * Source: starting value; checked by ear on device.
     */
    public const val METRONOME_CHUNK_MS: Long = 10
}
