# Link latency and tap calibration

A digital piano's notes reach the app later than the key press: the piano's own scanning and the MIDI link (Bluetooth
adds the most). Timed grading compares played times with the metronome, so an uncorrected link delay of 15 ms reads
as "late" on every note. The click side needs no correction: the metronome places clicks by the audio output
timestamp, so only the link side is measured.

The app finds the link delay by itself (below) and uses it for every timed run. A learner never has to take a test.
A tap calibration (further down) stays as an optional "Fine-tune timing" for a piano whose own scanning delay is
large.

## Automatic link offset
`measureLinkLatencyNanos(device, input, output, clock)` (`engine/src/commonMain/kotlin/pianoanchor/engine/midi/LinkLatency.kt`) times the MIDI Identity
Request round trip (`docs/protocols/midi-identity.md`):

1. It opens a reader on the piano's input, then sends one warm-up `IdentityCodec.REQUEST` and waits up to
   `CALIBRATION_PING_TIMEOUT_MS` for a reply. The input may still be opening (the adapter's `events` suspends while the
   port opens), so the first reply can arrive before anyone listens and be lost. The warm-up's reply, or its absence,
   is not counted and does not end the measurement.
   Then it sends `CALIBRATION_PING_COUNT` timed requests, one after another. Before each request (the warm-up too) it
   discards replies still queued, and it notes the `ClockPort` time right after the send returns.
2. Each timed request waits up to `CALIBRATION_PING_TIMEOUT_MS` for a SysEx that `IdentityCodec.parseReply` accepts, and
   takes the reply's timestamp minus the send time as the round trip. Android stamps incoming MIDI when it arrives,
   so the stamp is already free of delivery jitter. Any other message is ignored, and so is a round trip of zero or
   less (a late reply to an earlier request).
3. The result is half the smallest round trip. The smallest is the one with the least queueing, so it is the closest
   to the true one-way delay; half assumes the two directions take equally long.
4. No reply to a timed request ends the measurement with null: a piano that is silent will not answer later, so the
   wait is paid twice at most (the warm-up and the first timed request, 600 ms). A piano with no MIDI output, or a
   send that fails because the piano went away (warm-up included), gives null at once.
5. The reader is closed before the function returns. An input has one reader at a time, so the run's own reader opens
   only after the measurement has finished.

`defaultLinkLatencyNanos(transport)` is the fallback for a piano that does not answer: `CALIBRATION_BLE_DEFAULT_MS`
for Bluetooth LE, 0 for USB and everything else. `MidiDevice.transport` carries the transport, so the domain does not
parse device ids.

The owner's probe on a Bluetooth LE piano gave a smallest round trip of 21 ms (median 33 ms): a one-way delay of about
10 to 16 ms.

## Which offset a run uses
`PracticeViewModel`, for a timed run only (wait mode has no clock and measures nothing):

1. the offset stored by a tap calibration for this piano, if any; else
2. the measured link delay, found once per piano per ViewModel (a second take reuses it); else
3. the default of the piano's transport.

`TempoJudge` and `Grader.timedAttempt` subtract it from played times. The attempt keeps its played notes as played and
stores the offset used (`Attempt.latencyOffsetNanos`), so Results subtract the same value later. The learner sees no
message about which of the three applied. The measurement is not stored: it follows a link that changed, and the
store keeps meaning "a tap result". A timed run on a piano without a tap result arms about 0.3 s later on a piano that answers, up to 0.6 s on a silent one.

The measurement now comes first: a timed run arms itself, measures the link delay, waits
`Tunables.TEMPO_AUTO_START_DELAY_MS` and only then counts in. No starting key exists, so no key can fall into the
measurement and be dropped; keys pressed while arming or counting in sound and light neutrally but are not judged.

## Tap calibration ("Fine-tune timing")
`LatencyCalibration.clickTimes` lays out `CALIBRATION_CLICKS` clicks at `CALIBRATION_BPM` (60 BPM, one click a
second), all alike (no accent). The learner plays any key on each click. `evaluate` then:

1. pairs each NoteOn with the nearest click if it is within half a beat of it; where several taps pair with one click
   the one nearest the click counts, so a double tap is one tap;
2. drops the first `CALIBRATION_WARMUP_CLICKS` clicks, because people need a few beats to lock on;
3. needs at least `CALIBRATION_MIN_TAPS` paired taps among the rest, else `TooFewTaps`;
4. takes the median of the offsets (tap time minus click time) and the median absolute deviation around it (the
   spread). A spread above `CALIBRATION_MAX_SPREAD_MS` (45 ms) is `TooUneven`: the taps were not locked to the beat, so
   their median says little. Careful learners tapping to a click spread 20 to 35 ms, so the limit leaves room;
5. an offset beyond `CALIBRATION_MAX_OFFSET_MS` either way is `OutOfRange`: better to store nothing than a wrong link;
6. otherwise `Accepted` with the offset and the spread.

Medians, not means, so one stray tap moves nothing. Half a beat is taken from the spacing of the clicks given, so with
the app's own pattern the window is 500 ms, wider than `CALIBRATION_MAX_OFFSET_MS`, so every lag the range check
accepts pairs with its own click and a larger one (say 450 ms) is reported as `OutOfRange` rather than mispaired.
The screen shows the spread ("Spread ± 12 ms") and, when the taps were uneven, the limit.

The accepted offset is stored per `MidiDevice.id` behind `CalibrationRepositoryPort` and wins over the measurement.
Offsets stored before the automatic measurement existed are tap results too and keep winning; running Fine-tune
timing again replaces one. It is reached from the Pianos screen's Fine-tune timing button.

### One combined offset
A tap result covers the click leaving the phone, the learner's own reaction and the piano's note arriving, as one
number, so it also stores the learner's habit. People tap 20 to 50 ms ahead of a click, so a stored tap offset hides
that habit from grading for good. That is why tap calibration is opt-in and not the default: the automatic offset is
the piano's link only, and a steady lead shows up as the existing EARLY findings.

### Reading past the last click
The run does not evaluate at the last click's half beat but reads for `CALIBRATION_LAST_TAP_GRACE_MS` (200 ms) more.
Bluetooth MIDI delivers events at connection intervals of 7.5-30 ms and may buffer a few, so the last tap can be
timestamped inside its click's window yet arrive after the window closed. The grace only lets such late deliveries in:
a tap still counts only if `countedOffsets` accepts its timestamp, so the window is not widened. A drop during the
grace is handled like a drop during the run: no result, nothing saved.

## Known limits
- The piano's key-scanning delay (a few ms on digital pianos) is not measured; tap calibration covers it.
- A piano that never answers the Identity Request gets the transport default, which is a guess.
- A change of the piano's connection type (USB to Bluetooth) is a different device id, so a stored tap result does not
  carry over; the automatic measurement follows the link anyway.
- Drift over time and velocity are out of scope.

Tunables: `CALIBRATION_PING_COUNT`, `CALIBRATION_PING_TIMEOUT_MS`, `CALIBRATION_BLE_DEFAULT_MS`, `CALIBRATION_CLICKS`,
`CALIBRATION_BPM`, `CALIBRATION_WARMUP_CLICKS`, `CALIBRATION_MIN_TAPS`, `CALIBRATION_MAX_SPREAD_MS`,
`CALIBRATION_MAX_OFFSET_MS`, `CALIBRATION_LAST_TAP_GRACE_MS`.
