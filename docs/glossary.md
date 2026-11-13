# Glossary

Domain terms used in code, tests and docs. Add a term when you introduce it.

| Term | Meaning |
|---|---|
| Adapter | Implementation of a port for a real platform or technology (e.g. Android MIDI). Named `*Adapter`. |
| Attempt | One run of an exercise or passage, from first note to stop. |
| Cold check | The first attempt of a learned item on a later day, without warm-up or hints; it measures retention. |
| Contract test | Abstract test in `testing/` that every implementation of a port must pass. |
| FakePiano | In-memory test double in `testing/` that implements both MIDI ports for one device and decodes incoming bytes with the real parser. |
| Fake | Deterministic test implementation of a port, in `testing/`. |
| Hesitation | A pause before a note that is much longer than the music asks for, without a wrong note. |
| Middle C | MIDI note 60, named C4. |
| MIDI device | A MIDI instrument the platform can see: a stable `id` plus a display `name`. Listed by the MIDI input and output ports. |
| MIDI event | A typed message from the piano (`NoteOn`, `NoteOff`, `ControlChange`, `SysEx`) with a monotonic timestamp, produced by the MIDI parser from raw bytes. |
| Mistouch | An extra note one or two semitones from a correct note, played at the same moment (brushed key). |
| Port | Interface through which the domain uses the outside world. Named `*Port`, lives in `core/ports`. |
| Running status | MIDI shorthand: after one channel status byte, further data-byte groups reuse that status without repeating it. |
| Skill node | One learnable skill (e.g. "bass clef F", "C to G chord change") in the learner's skill graph. |
| Stable device id | The `MidiDevice.id` an adapter derives from what the platform reports (e.g. `usb:<manufacturer>/<product>/<serial>`, `ble:<address>`), so the same instrument keeps its id across reconnects when the platform reports enough to tell it apart. |
| SysEx | System Exclusive: a variable-length, vendor-specific MIDI message framed by 0xF0 and 0xF7. |
| Tunable | A threshold or prior in `engine/src/commonMain/kotlin/pianoanchor/engine/Tunables.kt`, documented with why and source. |
| Wait mode | Practice mode where the music waits until the learner plays the right note(s). |
