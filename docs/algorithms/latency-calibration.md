# Latency calibration

A digital piano's notes reach the app later than the key press: the piano's own scanning, the MIDI link (Bluetooth
adds the most) and, for anything timed against a click, the phone's audio output delay. Timed grading compares played
times with the metronome, so an uncorrected delay of 40 ms reads as "late" on every note. Calibration measures that
delay once per piano and the app subtracts it afterwards.

## Method
`LatencyCalibration.clickTimes` lays out `CALIBRATION_CLICKS` clicks at `CALIBRATION_BPM` (60 BPM, one click a second), all alike (no accent). The
learner plays any key on each click. `evaluate` then:

1. pairs each NoteOn with the nearest click if it is within half a beat of it; where several taps pair with one click
   the one nearest the click counts, so a double tap is one tap;
2. drops the first `CALIBRATION_WARMUP_CLICKS` clicks, because people need a few beats to lock on;
3. needs at least `CALIBRATION_MIN_TAPS` paired taps among the rest, else `TooFewTaps`;
4. takes the median of the offsets (tap time minus click time) and the median absolute deviation around it. A deviation
   above `CALIBRATION_MAX_SPREAD_MS` is `TooUneven`: the taps were not locked to the beat, so their median says little;
5. an offset beyond `CALIBRATION_MAX_OFFSET_MS` either way is `OutOfRange`: better to store nothing than a wrong link;
6. otherwise `Accepted` with the offset and the spread.

Medians, not means, so one stray tap moves nothing. Half a beat is taken from the spacing of the clicks given, so with
the app's own pattern the window is 500 ms, wider than `CALIBRATION_MAX_OFFSET_MS`, so every lag the range check
accepts pairs with its own click and a larger one (say 450 ms) is reported as `OutOfRange` rather than mispaired.

## One combined offset
The measurement covers the click leaving the phone, the learner's own reaction and the piano's note arriving, as one
number. That is deliberate: grading compares piano time with click time, and exactly this sum is what it needs. The
learner's reaction is a roughly constant lead or lag that cancels the same way when they play with a click later.

## Use
- The offset is stored per `MidiDevice.id` behind `CalibrationRepositoryPort`.
- A timed run reads it when it starts. `TempoJudge` and `Grader.timedAttempt` subtract it from played times.
- The attempt keeps its played notes as played and stores the offset used (`Attempt.latencyOffsetNanos`, null when the
  piano was not calibrated), so Results subtract the same value later even after a recalibration.
- Wait mode has no clock and ignores it.

## Offered
Before a piano's first timed run (a card with Calibrate and Skip; Skip lasts for the app session) and on demand from
the monitor.

## Known limits
- A change of audio output (Bluetooth headphones, a speaker dock) changes the click's delay and needs a recalibration;
  the app does not detect it.
- Likewise a change of the piano's connection type (USB to Bluetooth) is a different device id, so it is calibrated on
  its own.
- Drift over time and velocity are out of scope.

Tunables: `CALIBRATION_CLICKS`, `CALIBRATION_BPM`, `CALIBRATION_WARMUP_CLICKS`, `CALIBRATION_MIN_TAPS`,
`CALIBRATION_MAX_SPREAD_MS`, `CALIBRATION_MAX_OFFSET_MS`.
