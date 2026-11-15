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
     * Longest wait, in milliseconds after the NoteOn that completes a wait-mode passage, for the last notes to stop
     * sounding before the run is graded and saved; notes still sounding then end at this cap.
     *
     * Why: people usually lift within a second, so Results open at the natural moment; the cap only guards against a
     * stuck key, a lost NoteOff or a pedal left down.
     *
     * Source: product choice in the owner grill (no outside source).
     */
    public const val WAIT_MODE_RELEASE_TIMEOUT_MS: Long = 3_000

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

    /**
     * How many times per beat a tempo run moves the practice cursor and checks the clock (4 = every quarter beat).
     *
     * Why: fine enough that the cursor never looks late against a click, coarse enough to stay a few redraws per second
     * (the screen has no animation, so screenshots stay still).
     *
     * Source: starting value from the M1.4c spec; revisit after the device check.
     */
    public const val TEMPO_CURSOR_SUBDIVISIONS: Int = 4

    /**
     * How long, in milliseconds, after the key that starts a tempo run the first count-in click sounds.
     *
     * Why: the metronome queues audio a little ahead, so a click right at the key press could be skipped; this leaves
     * room without making the learner wait long.
     *
     * Source: starting value from the M1.4c spec; the metronome port documents a few tens of milliseconds of lead.
     */
    public const val TEMPO_COUNT_IN_LEAD_MS: Long = 200

    /**
     * How many metronome clicks the latency calibration plays, warm-up included.
     *
     * Why: enough taps for a stable median in about 16 seconds at the calibration tempo.
     *
     * Source: starting value, owner-agreed in the M1 grill; calibrated in M7.
     */
    public const val CALIBRATION_CLICKS: Int = 16

    /**
     * The tempo of the calibration clicks, in beats per minute.
     *
     * Why: half a beat (500 ms) is wider than `CALIBRATION_MAX_OFFSET_MS`, so every accepted lag pairs with its own
     * click; still a comfortable tapping pace.
     *
     * Source: owner decision 2026-11-15 (was 90 BPM in the M1 grill); calibrated in M7.
     */
    public const val CALIBRATION_BPM: Int = 60

    /**
     * How many of the first calibration clicks are warm-up: their taps are ignored.
     *
     * Why: people lock onto a beat after a few clicks.
     *
     * Source: starting value, owner-agreed in the M1 grill; calibrated in M7.
     */
    public const val CALIBRATION_WARMUP_CLICKS: Int = 4

    /**
     * The fewest paired taps among the counted clicks for a calibration to be judged at all.
     *
     * Why: at least two thirds of the 12 counted clicks must have a tap (the spec author's choice).
     *
     * Source: starting value, owner-agreed in the M1 grill; calibrated in M7.
     */
    public const val CALIBRATION_MIN_TAPS: Int = 8

    /**
     * The largest median absolute deviation of the paired taps, in milliseconds, that a calibration accepts.
     *
     * Why: above this the taps were not locked to the beat, so their median says little about the piano.
     *
     * Source: starting value, owner-agreed in the M1 grill; calibrated in M7.
     */
    public const val CALIBRATION_MAX_SPREAD_MS: Long = 30

    /**
     * The largest latency offset, in milliseconds either way, that a calibration stores.
     *
     * Why: beyond this the link or the setup is wrong; better not to store it.
     *
     * Source: starting value, owner-agreed in the M1 grill; calibrated in M7.
     */
    public const val CALIBRATION_MAX_OFFSET_MS: Long = 400

    /**
     * How long calibration keeps reading after the last click's half beat, so a tap timestamped in time but
     * delivered late still counts.
     *
     * Why: Bluetooth MIDI devices send events at connection intervals of 7.5-30 ms and may buffer a few of them;
     * 200 ms covers that with margin and keeps the result immediate.
     *
     * Source: the BLE MIDI specification's connection-interval range, plus an owner decision in the grill.
     */
    public const val CALIBRATION_LAST_TAP_GRACE_MS: Long = 200

    /**
     * Recall probability the scheduler aims for when it picks the next review: an item is due when its
     * retrievability falls to this.
     *
     * Why: the standard FSRS balance between review workload and how much is remembered.
     *
     * Source: owner-agreed in the M2 grill; the FSRS default (py-fsrs `desired_retention` 0.9).
     */
    public const val DESIRED_RETENTION: Double = 0.90

    /**
     * Longest interval the scheduler hands out, in days (100 years).
     *
     * Why: no practical cap, so a very stable item is never forced back early; it only bounds the arithmetic.
     *
     * Source: py-fsrs default `maximum_interval` (36500), the reference the vectors come from.
     */
    public const val MAX_INTERVAL_DAYS: Int = 36_500

    /**
     * The learner day starts at this local hour (0-23): a run before it belongs to the previous day.
     *
     * Why: late-night practice still belongs to the evening's day.
     *
     * Source: starting value, owner-agreed in the M2 grill; calibrated in M7.
     */
    public const val LEARNER_DAY_START_HOUR: Int = 4

    /**
     * The fewest CLEAN cold checks, on distinct days, before a skill counts as learned.
     *
     * Why: one fully clean run is still required.
     *
     * Source: owner decision 2026-11-15 after the #73 simulation (was 2); calibrated in M7.
     */
    public const val LEARNED_MIN_CLEAN_COLD_CHECKS: Int = 1

    /**
     * The fewest recalled (Hard or Good) cold checks, on distinct days, before a skill counts as learned.
     *
     * Why: two separate recalls show it wasn't luck, while small slips no longer block a skill's dependants for
     * months.
     *
     * Source: owner decision 2026-11-15 after the #73 simulation; calibrated in M7.
     */
    public const val LEARNED_MIN_RECALLED_COLD_CHECKS: Int = 2

    /**
     * The least FSRS stability, in days, a skill needs to count as learned.
     *
     * Why: FSRS expects it to last days, not hours.
     *
     * Source: starting value, owner-agreed in the M2 grill; calibrated in M7.
     */
    public const val LEARNED_MIN_STABILITY_DAYS: Double = 3.0

    /**
     * The FSRS stability, in days, at which recalled cold checks alone (at least
     * [LEARNED_MIN_RECALLED_COLD_CHECKS], none of them clean) make a skill learned.
     *
     * Why: a skill recalled every time with small slips, whose memory already lasts weeks, is known well enough to
     * unlock its dependants; without this, one unlucky skill locked a third of the #73 simulation's graph.
     *
     * Source: Anki's "mature" card threshold (an interval of 21 days or more); owner request 2026-11-15 after the
     * #73 simulation; calibrated in M7.
     */
    public const val LEARNED_MATURE_STABILITY_DAYS: Double = 21.0

    /**
     * The most new skills the plan introduces on one learner day, counting those already introduced that day.
     *
     * Why: new material stays digestible in a 15-20 minute session.
     *
     * Source: starting value, owner-agreed in the M2 grill; calibrated in M7.
     */
    public const val MAX_NEW_PER_DAY: Int = 2

    /**
     * The most due skills the plan offers on one learner day; the overflow waits for later days.
     *
     * Why: reviews fit the same 15-20 minute session.
     *
     * Source: starting value, owner-agreed in the M2 grill; calibrated in M7.
     */
    public const val MAX_DUE_PER_DAY: Int = 12

    /**
     * The share of the daily budget Today gives to the review (cold open) segment.
     *
     * Why: review first and short: memory fades fastest overnight.
     *
     * Source: starting value, owner, M4 grill: the mock's 30-minute plan without Free; calibrated in M7.
     */
    public const val SESSION_SHARE_REVIEW: Double = 0.20

    /**
     * The share of the daily budget Today gives to the new lesson segment.
     *
     * Why: new material needs the most focused time.
     *
     * Source: starting value, owner, M4 grill: the mock's 30-minute plan without Free; calibrated in M7.
     */
    public const val SESSION_SHARE_LESSON: Double = 0.35

    /**
     * The share of the daily budget Today gives to the song segment.
     *
     * Why: motivation and tempo work.
     *
     * Source: starting value, owner, M4 grill: the mock's 30-minute plan without Free; calibrated in M7.
     */
    public const val SESSION_SHARE_SONG: Double = 0.30

    /**
     * The share of the daily budget Today gives to the ear segment.
     *
     * Why: short daily ear practice beats a long weekly one.
     *
     * Source: owner, M4 grill: the mock's 30-minute plan without Free; calibrated in M7.
     */
    public const val SESSION_SHARE_EAR: Double = 0.15

    /**
     * The minutes one ear skill takes in the ear segment; it sets how many ear skills the ear card lists.
     *
     * Why: one ear review is a set of 4 short items.
     *
     * Source: owner, M4 grill: a set of 4 per review.
     */
    public const val EAR_MINUTES_PER_SKILL: Int = 1

    /**
     * How many items one ear review draws.
     *
     * Why: one draw is too noisy (a 50% guess rate on 2 choices).
     *
     * Source: owner, M4 grill.
     */
    public const val EAR_ITEMS_PER_REVIEW: Int = 4

    /**
     * How many times the learner may replay one ear item.
     *
     * Why: hear it again without turning it into a memory test.
     *
     * Source: owner, M4 grill.
     */
    public const val EAR_MAX_REPLAYS: Int = 2

    /**
     * How many answers one ear item allows in total.
     *
     * Why: one second chance, then the answer is shown.
     *
     * Source: owner, M4 grill.
     */
    public const val EAR_MAX_ANSWERS: Int = 2

    /**
     * The fewest items of a set that must end right (first try or recovered) for the band GOOD.
     *
     * Why: 3 of 4 right in the end is "recalled with slips".
     *
     * Source: owner, M4 grill.
     */
    public const val EAR_GOOD_MIN_RIGHT: Int = 3

    /**
     * How long a chord of an ear item is held, in milliseconds.
     *
     * Why: a chord needs longer than a single note to judge its quality.
     *
     * Source: starting value.
     */
    public const val EAR_CHORD_LENGTH_MS: Long = 800

    /**
     * The gap between the starts of the notes the app plays (onboarding's C, E and G; an ear item), in milliseconds.
     *
     * Why: 500 ms gives a clear, unhurried arpeggio on any piano.
     *
     * Source: starting value; the mock's pacing.
     */
    public const val PLAYBACK_NOTE_GAP_MS: Long = 500

    /**
     * How long each played note is held before its NoteOff, in milliseconds.
     *
     * Why: shorter than [PLAYBACK_NOTE_GAP_MS], so the three notes sound apart, yet long enough to ring.
     *
     * Source: starting value; the mock's pacing.
     */
    public const val PLAYBACK_NOTE_LENGTH_MS: Long = 400

    /**
     * The velocity of the notes the app plays to the piano.
     *
     * Why: a medium-soft touch (80 of 127) is clearly audible without startling the learner on any piano.
     *
     * Source: starting value; the mock's pacing.
     */
    public const val PLAYBACK_VELOCITY: Int = 80

    /**
     * How long the onboarding waits for a first note, in milliseconds, before it offers the piano list.
     *
     * Why: a piano that is on and connected sends a note within seconds of a key press; a long silence means it
     * isn't connected, and the Pianos screen is where to find out why.
     *
     * Source: starting value; the mock's pacing.
     */
    public const val ONBOARDING_CONNECT_HINT_MS: Long = 30_000

    /**
     * How many missed echo attempts the onboarding allows before it offers Skip.
     *
     * Why: three tries are fair for a first chord, and a learner who can't get it (or whose piano doesn't answer
     * as expected) must not be stuck on the second step.
     *
     * Source: spec #80; starting value, the mock's pacing.
     */
    public const val ONBOARDING_MISSES_BEFORE_SKIP: Int = 3

    /**
     * How many stray notes before the C the onboarding's echo check forgives.
     *
     * Why: a first key press on an unfamiliar piano is often a wrong one; forgiving one keeps the first success
     * easy without accepting any random run of notes.
     *
     * Source: spec #80; starting value, the mock's pacing.
     */
    public const val ONBOARDING_ECHO_MAX_STRAY_NOTES: Int = 1
}
