# Android audio output (`android.media.AudioTrack`)

How `:platform:android-audio` plays the metronome (slice M1.4a) behind `MetronomePort`. Code:
`platform/android-audio/src/commonMain/kotlin/pianoanchor/platform/androidaudio/AndroidMetronomeAdapter.kt` (logic) and
`platform/android-audio/src/androidMain/kotlin/pianoanchor/platform/androidaudio/AudioTrackSink.kt` (glue).

## The track
- One streaming `AudioTrack` for the life of the app: mono 16-bit PCM at the device's native output rate
  (`AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE`, 48 kHz when unreported), `PERFORMANCE_MODE_LOW_LATENCY`, `USAGE_GAME`
  and a buffer of at least four chunks (or the platform minimum, if larger), created on the first write. The
  platform `AudioTrack` is enough for a click: Oboe would add an NDK build, and `SoundPool` cannot play at a given
  time.
- Clicks are synthesised, not samples: a `METRONOME_CLICK_MS` sine burst with an exponential decay, at
  `METRONOME_ACCENT_HZ` for a bar's first beat and `METRONOME_BEAT_HZ` otherwise. No assets, so no rights records.
- While a pattern runs, the adapter writes `METRONOME_CHUNK_MS` chunks, mostly silence, from a coroutine in its
  app-lived scope. Writes are non-blocking with short suspending retries, so the queue stays about one buffer ahead of
  playback and `stop` never waits for the track.
- `stop` ends the writer, then pauses and flushes the track, which drops queued audio and resets its frame position
  to 0. The track stays open. A track whose write fails (for example after the audio route changed) is released and
  replaced; frame positions continue from where the failed one would have been. Until the new track has a timestamp
  the old mapping stays in use, so clicks can be off by up to one buffer for a moment. Any other failure of the writer
  is logged, never crashes the app, and the next `start` begins a fresh stream.

## Placing a click: the clock mapping
Beat times are monotonic nanoseconds from `ClockPort` (`System.nanoTime()`, `CLOCK_MONOTONIC`), the clock
`AudioTimestamp.nanoTime` also uses. Before writing each chunk the adapter reads the latest timestamp, a frame
position and the time it was played, and places each beat at

`frame = timestamp.framePosition + (beatNanos − timestamp.nanoTime) × sampleRate / 10⁹` (rounded to the nearest frame).

- Once playback has a timestamp, the glue re-reads it once a second: the `AudioTrack` documentation advises against
  frequent polling, and the mapping drifts slowly.
- Before playback has a timestamp (just after `play()`), playback is assumed to start when the first chunk is
  written: frame 0 at that moment. A timestamp from before the latest `play()` is ignored. Once a timestamp has been
  read, the latest one is kept until a newer one arrives.
- A beat whose frame is already written is skipped; later beats keep their phase (beat numbers count from the
  pattern's first beat, so accents stay on the bar).
- `start` while running replaces the pattern from the next chunk on. Audio already queued plays out, so a beat within
  about one buffer and one chunk of the call may still follow the old pattern; callers start patterns ahead of their
  first beat (M1.4c counts in one bar).

## Known limits
- Bluetooth output adds a delay the timestamp does not fully show and that differs per headset. M1.5 latency
  calibration measures it together with the piano's MIDI delay as one offset.
- The clock mapping and the flush behaviour (frame position back to 0) follow the `AudioTrack` documentation and are
  checked by ear on a device (owner check), not by an automated test; the logic is tested on the JVM against a fake
  sink.
