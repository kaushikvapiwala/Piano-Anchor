# MIDI Identity Request and Reply

How the app asks a connected piano who it is. Code:
`engine/src/commonMain/kotlin/pianoanchor/engine/identity/IdentityCodec.kt` (bytes) and
`feature/monitor/src/commonMain/kotlin/pianoanchor/feature/monitor/MonitorViewModel.kt` (when it is sent).
Both messages are part of the MIDI 1.0 standard (Universal Non-Real-Time System Exclusive, General Information),
so every vendor that implements them uses the same layout. No vendor-specific SysEx is sent.

## Request
Six bytes: `F0 7E 7F 06 01 F7`.
- `7E` marks a Universal Non-Real-Time message.
- `7F` is the device id "all call": every device on the port should answer, whatever its own device id.
- `06 01` is General Information, Identity Request.

## Reply
`F0 7E <device id> 06 02 <manufacturer> <family LSB> <family MSB> <model LSB> <model MSB> <v1> <v2> <v3> <v4> F7`
- `<device id>` is the piano's own device id; any value is accepted.
- `<manufacturer>` is one byte, or three bytes when the first is `00` (extended ids). A one-byte id is never `00`.
- Family and model are 14-bit numbers, each sent as two 7-bit bytes, least significant first:
  `value = LSB + 128 × MSB` (e.g. `7F 01` is 255).
- The four version bytes are vendor-defined; the app shows them joined by dots.
- Bytes after the version are ignored. A reply too short to hold every field counts as no reply.

The parser gets the SysEx payload (the bytes between `F0` and `F7`) from the MIDI parser and checks
payload[0] = `7E`, payload[2] = `06`, payload[3] = `02`; anything else is some other SysEx.

## When the app asks
- On every connect of the selected piano (first connect and every reconnect), if the platform lists an output with
  the same device id, the monitor sends the request once and waits up to `Tunables.IDENTITY_REPLY_TIMEOUT_MS` for
  the first reply. Other messages keep flowing to the log meanwhile, and the reply itself is logged too.
- The wait is armed before sending, so a reply that comes back at once still counts.
- Then the instrument is stored by its stable device id (`InstrumentRepositoryPort.upsert`): the platform name, the
  identity, and the current wall-clock time as last seen; the first-seen time already stored is kept.

## Fallback
Many Bluetooth pianos never answer. No output device, a send that fails because the device is gone, or no reply
before the timeout all store the instrument **without an identity**. None of these is an error for the learner.
A piano already stored with an identity keeps it: storing without one updates the name and last-seen time only, so a
reply lost on one reconnect doesn't erase the model. A reply with a different identity (after a firmware update, say)
replaces the stored one. This relies on stable device ids: a `sys:<id>` fallback id (`docs/protocols/android-midi.md`)
can name another piano after a reconnect, which would then show the earlier piano's model.
A piano that drops before the reply or the timeout is asked again when it reconnects.

Out of scope for now: retries, a manufacturer-name table, feature gating by model, and recognising the same piano
seen over USB and Bluetooth.
