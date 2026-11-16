# 0025. Measure the link delay automatically; tap calibration becomes optional

- Status: accepted
- Date: 2026-11-15

## Context
Timed grading subtracts a per-piano latency offset (`docs/algorithms/latency-calibration.md`). Today the only way to
get it is tap calibration: the learner plays a key on 16 clicks, and a median absolute deviation above
`CALIBRATION_MAX_SPREAD_MS` (30 ms) rejects the run as "Taps were uneven". It is step 3 of the onboarding and a card
before a piano's first timed run.

The owner's hardware check (Pixel, Android 17, FRP-NUVOLA EX MIDI over Bluetooth LE) showed:
- The tap test is too hard. Several careful rounds of 12+ taps were all rejected. Ordinary tapping to a click spreads
  about 20 to 35 ms on this measure, so a 30 ms limit rejects normal people.
- It measures the wrong thing. People tap 20 to 50 ms ahead of a click (negative mean asynchrony), so a combined
  offset stores the learner's habit as if it were the piano's delay, and grading then hides the habit for good.
- The click side is already exact: the metronome places clicks by `AudioTrack.getTimestamp`, so the audio output
  delay needs no measurement.
- The link side can be measured without the learner. The piano answers the MIDI Identity Request
  (`IdentityCodec.REQUEST`, already sent by the monitor). A debug probe sent it repeatedly: round trip min 21 ms,
  median 33 ms, so the one-way link is about 10 to 16 ms. Android stamps incoming notes when the radio packet arrives,
  2 to 14 ms before the app gets them, so the stamp already removes delivery jitter.

## Decision
- **Automatic link offset.** When a timed run starts on a piano with no stored offset, the app sends the Identity
  Request `CALIBRATION_PING_COUNT` times, before the run's reader opens the input, and uses half the smallest round
  trip (send time on the `ClockPort` to the reply's receive timestamp) as the offset for that run. The smallest round
  trip is the one with the least queueing, so it is the closest to the true link delay. It is measured per run (about
  0.3 s), not stored: it follows a link that changed, and the store keeps meaning "a tap result". The attempt already
  records the offset it used (`Attempt.latencyOffsetNanos`).
- **Fallback by transport.** A piano that doesn't answer gets a default for its transport (Bluetooth LE
  `CALIBRATION_BLE_DEFAULT_MS`; USB and others 0), not "uncalibrated". `MidiDevice` gains a transport field so the
  domain doesn't parse device ids.
- **The habit is the learner's, not the piano's.** Grading no longer absorbs a learner's lead or lag. A steady lead
  shows up as the existing EARLY findings, which is honest coaching; a dedicated "you play ahead of the beat" line is a
  later choice for the dogfood to inform.
- **Calibration leaves the onboarding** (three steps instead of four), and the card before a first timed run goes away.
- **Tap calibration stays, optional,** as "Fine-tune timing" on the Pianos screen, for a piano whose own scanning delay
  is large. Its spread limit rises to 45 ms, the screen shows the measured spread, and a stored tap result is used
  instead of the automatic measurement.

Rejected:
- Only loosening the tap limit: still asks every learner for a test before their first piece and still stores their
  habit as delay.
- Assuming a fixed delay per transport for everyone: misses pianos and phones with slower links, which the ping
  measures for free.
- Measuring the piano's audio with the phone's microphone: needs a microphone permission, a quiet room and the
  piano's speakers on; too much for a first-run step.

## Consequences
- No new dependency. Onboarding gets shorter and no learner ever fails a timing test.
- The piano's key-scanning delay (a few ms on digital pianos) is not measured; tap calibration remains for that.
- A tap result still includes the learner's own lead or lag, which is why it is opt-in and not the default.
- No storage change: existing stored offsets are tap results and keep winning. `CalibrationRepositoryPort` is unchanged.
- A timed run starts about 0.3 s later on a piano without a tap result.
- Implementation is specced as a GitHub issue (label `spec`); `docs/algorithms/latency-calibration.md` is updated in
  that PR.
