# MIDI byte-stream parser

`MidiParser` (in `:engine`, package `midi`) turns the raw MIDI 1.0 bytes a piano sends into typed
`MidiEvent`s from `:core:model`.

## Why stateful
MIDI messages are not self-contained. A transport may deliver a message in several chunks, and running
status lets a sender omit repeated status bytes. The parser therefore keeps the active status, the data
bytes seen so far and any SysEx being collected between `feed` calls. One instance serves one connection;
`reset()` clears everything, for example after a reconnect.

## Why lenient
Bytes come from hardware and drivers we do not control: streams start mid-message, cables glitch, devices
misbehave. The parser never throws on device bytes. Anything it cannot place (stray data bytes, a stray
0xF7, an unfinished SysEx) is dropped and parsing resumes at the next status byte. Only a bad
`offset`/`count` range, which is a programming error, throws.

## Timestamps
An event carries the timestamp of the `feed` call that delivered its last byte. That is the earliest moment
the message is known to be complete, and it is the only time the parser can observe. Bytes of one call share
one timestamp.

## Running status
A channel voice status stays active, so further complete data groups repeat it. A NoteOn with velocity 0
is reported as a NoteOff with the neutral release velocity. Real-time bytes (0xF8 and above) may appear
anywhere, even inside a SysEx, and change no state. System common messages and SysEx cancel running status,
and their data bytes are consumed or dropped. Program change, pitch bend and pressure messages are consumed
so their data bytes are not misread, but no event is produced.

## SysEx
0xF0 starts collecting, 0xF7 emits the payload without framing. Any other status byte arriving first
discards the unfinished message and is then handled normally. A payload larger than
`Tunables.SYSEX_MAX_BYTES` is discarded (no event) so a device that never sends 0xF7 cannot exhaust memory.
