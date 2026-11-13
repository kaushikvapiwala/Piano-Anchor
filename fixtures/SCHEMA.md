# Fixture format (schema 1)

A fixture is a recorded MIDI session: the note monitor (`:feature:monitor`) writes it, and the matcher and replay
tests read it. Code: `engine/src/commonMain/kotlin/pianoanchor/engine/fixture/FixtureCodec.kt`.

## File
JSONL (`application/x-ndjson`, extension `.jsonl`), UTF-8, every line ended by `\n`.

Line 1 is the header; every field is required:

```json
{"schema":1,"device":"FP-30X","recordedAt":"2026-11-12T10:00:00Z","clock":"monotonic-ns"}
```

| Field | Meaning |
|---|---|
| `schema` | Format version. This document describes 1; readers reject other values. |
| `device` | The piano's display name as the platform reported it. |
| `recordedAt` | When the recording was exported, ISO-8601 in UTC. |
| `clock` | Clock domain of `t`: `monotonic-ns` (the app's `ClockPort`). |

Each further line is one MIDI message:

```json
{"t":0,"bytes":"903c64"}
{"t":250000000,"bytes":"803c40"}
```

| Field | Meaning |
|---|---|
| `t` | Nanoseconds since the first message: never negative, never smaller than the line before. |
| `bytes` | The whole message as lowercase hex pairs (readers accept uppercase), starting with a status byte (80–ff). |

A reader reports the first bad line by number: missing header, wrong schema, non-hex or empty bytes, a missing
status byte, or a negative or decreasing `t`.

## Canonical bytes, not a wire capture
Messages are the events the app saw, re-encoded by `MidiEncoder`; replaying the file through `MidiParser` gives
exactly those events back. Differences from what the piano sent:
- Running status is not kept: every message has its status byte.
- A Note On with velocity 0 is written as a Note Off with release velocity 64.
- Message types the model does not parse are absent: pitch bend, program change, aftertouch and real-time bytes
  (Active Sensing, Timing Clock).

How the parser copes with real byte quirks stays covered by the parser's own tests.

## Limits
A recording stops at `MONITOR_RECORDING_MAX_MS` after its first message
(`engine/src/commonMain/kotlin/pianoanchor/engine/Tunables.kt`).

Fixtures committed here must be recordings of public-domain music or original playing (see `AGENTS.md`).
