# Android MIDI service (`android.media.midi`)

How `:platform:android-midi` uses Android's MIDI service. Code:
`platform/android-midi/src/commonMain/kotlin/pianoanchor/platform/androidmidi/AndroidMidiAdapter.kt` (logic) and
`platform/android-midi/src/androidMain/kotlin/pianoanchor/platform/androidmidi/MidiManagerSystem.kt` (glue).

## What the adapter sees
- Devices come from `MidiManager`, limited to byte-stream (MIDI 1.0) devices. On Android 13+ the adapter asks for
  that transport explicitly; on older versions it uses the plain device list, which holds only byte-stream devices.
- USB MIDI devices appear when plugged in. Bluetooth LE MIDI devices appear only after something has opened them
  (pairing and opening is slice 1.4b); this adapter does no Bluetooth scanning and needs no Bluetooth permission.
- A device without the MIDI system feature lists no devices.
- One device callback, registered for the life of the adapter (it is app-scoped), re-reads the whole list on every
  add or remove, so looking up a device before opening or sending costs nothing.

## Port naming
Android names ports from the device's point of view:
- A device's **output port** carries what the instrument plays. `MidiInputPort.inputDevices` lists devices with at
  least one output port, and `events` opens **output port 0**.
- A device's **input port** receives what we send. `MidiOutputPort.outputDevices` lists devices with at least one
  input port, and `send` writes to **input port 0**.
- Devices with several ports of one kind use port 0 only for now (multi-port devices are a follow-up).

## Opening and closing
- `events(device)` opens the device and its output port when collection starts and closes both when the collector
  is cancelled. If the device is removed while open (or while it is being opened), the flow completes normally.
  Only one collector per device at a time; a second one fails with `IllegalStateException`. A received block whose
  range is invalid fails the flow instead of crashing Android's callback thread.
- `send` opens input port 0 on first use and keeps it open. When the device has left the list, the open port is
  closed at the next `send` (the only moment the adapter looks at it), which then fails as usual. If Android
  rejects the bytes because the port has closed before the list caught up, `send` drops that port and fails with
  `MidiDeviceUnavailableException`; a retry opens the port again. The adapter has no `close()`: it is meant to live
  as long as the app, and Android releases its ports when the app process ends.

## Timestamps
- Android passes a timestamp with every received block of bytes (`MidiReceiver.onSend`), in the
  `System.nanoTime()` clock domain. `AndroidClockAdapter` reads the same clock, so the two can be compared.
- For Bluetooth LE MIDI, Android's own Bluetooth MIDI service turns the timestamps the instrument puts in each BLE
  packet into that clock domain and keeps them from lying in the future. The adapter does not re-interpret them.
- All events parsed from one callback share its timestamp. Per open device the adapter keeps stamps
  non-decreasing (`stamp = max(previous stamp, platform timestamp)`), as the input port contract requires.
- Bytes are parsed with the shared `MidiParser` (see `docs/algorithms/midi-parser.md`), one parser per open device,
  so running status and SysEx split across callbacks are handled. Callbacks arrive on binder threads; parsing is
  serialised per device and events are queued without blocking the callback thread.

## Stable device ids
`MidiDevice.id` must stay the same for the same instrument and be the same in input and output listings:

| Transport | Id | Needs |
|---|---|---|
| USB | `usb:<manufacturer>/<product>/<serial>` | all three USB properties present and not blank |
| Bluetooth | `ble:<ADDRESS>` (upper case) | the Bluetooth device in the device properties |
| anything else, or missing properties | `sys:<id>` | Android's per-connection device id (changes on reconnect) |

If two listed devices compute the same id (e.g. two identical USB devices without distinct serials), the one with
the higher Android device id (the later connection) gets `#<Android device id>` appended, repeatedly if the result
is still taken, so ids stay unique and do not swap between list refreshes. The display name is the device's name
property, else its product name, else a generic "MIDI device".

## Verified so far
- The adapter logic (listing, ids, opening rules, parsing, timestamps, sending) runs on the JVM against a fake of
  the platform seam, including the MIDI port contract tests.
- The `MidiManager` glue is **not yet verified on hardware**. Its first check is the slice 1.4m owner test, over
  Bluetooth LE on a phone; USB is checked once a cable is available.
