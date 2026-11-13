# Glossary

Domain terms used in code, tests and docs. Add a term when you introduce it.

| Term | Meaning |
|---|---|
| Accuracy | How cleanly a completed attempt was played by pitch: steps / (steps + wrong notes + incomplete chords), from 0 to 1 (`docs/algorithms/grading.md`). |
| Adapter | Implementation of a port for a real platform or technology (e.g. Android MIDI). Named `*Adapter`. |
| Attempt | One completed run of a passage, from the first note to `PassageComplete` (`Attempt` in `:core:model`): its counts, accuracy, band and match events, stored by `AttemptRepositoryPort`. Abandoned runs are not saved. |
| Band | The result of an attempt named for the learner: `CLEAN`, `GOOD` or `KEEP_PRACTISING`, from its accuracy and the `BAND_*_MIN_ACCURACY` tunables. |
| BLE MIDI | MIDI over Bluetooth Low Energy. A BLE MIDI piano is found by a scan and must be connected from the app (`MidiDiscoveryPort`); pairing in the system settings pairs only Bluetooth audio. |
| Chord window | The `CHORD_CLUSTER_MS` span from a step's first pressed note in which its other notes must follow; the matcher reports an incomplete chord when it runs out. |
| Cold check | The first attempt of a learned item on a later day, without warm-up or hints; it measures retention. |
| Contract test | Abstract test in `testing/` that every implementation of a port must pass. |
| FakePiano | In-memory test double in `testing/` that implements both MIDI ports for one wired device, MIDI discovery for the devices it advertises, and decodes incoming bytes with the real parser. |
| Fixture | A recorded MIDI session as a JSONL file (`fixtures/SCHEMA.md`): a header, then each message's canonical bytes and its time since the first message. Feeds the matcher and replay tests. |
| Fake | Deterministic test implementation of a port, in `testing/`. |
| Hesitation | A pause before a note that is much longer than the music asks for, without a wrong note. |
| Incomplete chord | `ChordIncomplete`: the chord window of a step ran out before all its notes were pressed; the step must be played again. |
| Instrument | A piano the app has seen, stored by its stable device id (`InstrumentRepositoryPort`): display name, instrument identity if it answered, and when it was first and last seen. Per-piano data (latency, velocity curve) hangs off it. |
| Instrument identity | What a piano reports in a MIDI Identity Reply: manufacturer id, model family, model number and four version bytes (`docs/protocols/midi-identity.md`). Null when the piano did not answer. |
| Middle C | MIDI note 60, named C4. |
| MIDI device | A MIDI instrument the platform can see: a stable `id` plus a display `name`. Listed by the MIDI input and output ports. |
| MIDI event | A typed message from the piano (`NoteOn`, `NoteOff`, `ControlChange`, `SysEx`) with a monotonic timestamp, produced by the MIDI parser from raw bytes. |
| Mistouch | An extra note one or two semitones from a correct note, played at the same moment (brushed key). |
| MIDI discovery | Finding and connecting MIDI devices that the platform does not list until connected, such as BLE MIDI pianos: `MidiDiscoveryPort.scan`, `connect` and `disconnect`. |
| Note monitor | The first screen (`:feature:monitor`): pick a piano, watch its live notes, record a session and export it as a fixture. |
| Passage | A piece of music to practise (`Passage` in `:core:model`): a stable id, a title and its steps. |
| Port | Interface through which the domain uses the outside world. Named `*Port`, lives in `core/ports`. |
| Reconnect backoff | How the adapter reopens a dropped BLE MIDI device: waits that double from `BLE_RECONNECT_INITIAL_MS` up to `BLE_RECONNECT_MAX_MS`, giving up after `BLE_RECONNECT_GIVE_UP_MS`. |
| Running status | MIDI shorthand: after one channel status byte, further data-byte groups reuse that status without repeating it. |
| Skill node | One learnable skill (e.g. "bass clef F", "C to G chord change") in the learner's skill graph. |
| Stable device id | The `MidiDevice.id` an adapter derives from what the platform reports (e.g. `usb:<manufacturer>/<product>/<serial>`, `ble:<address>`), so the same instrument keeps its id across reconnects when the platform reports enough to tell it apart. |
| Step | One moment in a passage: the set of keys to press together (one note, or a chord). |
| SysEx | System Exclusive: a variable-length, vendor-specific MIDI message framed by 0xF0 and 0xF7. |
| Tunable | A threshold or prior in `engine/src/commonMain/kotlin/pianoanchor/engine/Tunables.kt`, documented with why and source. |
| Wait mode | Practice mode where the music waits until the learner plays the right note(s). |
| Wrong note | `WrongNote`: a key pressed that isn't in the current step; progress is unchanged. |
