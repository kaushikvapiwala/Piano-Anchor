# Bluetooth LE MIDI

How `:platform:android-midi` finds, connects and keeps Bluetooth LE MIDI pianos (slice 1.4b), behind
`MidiDiscoveryPort`. Code: `AndroidMidiAdapter.kt` and `ReconnectPolicy.kt` (logic, `commonMain`) and
`MidiManagerSystem.kt` (glue, `androidMain`). Listing, opening and timestamps work as for any device; see
`docs/protocols/android-midi.md`.

## Background
- BLE MIDI devices advertise the service UUID `03B80E5A-EDE8-4B33-A751-6CE34EC4C700`. The scan filters on it only.
- MIDI has to be connected from an app. Pairing in Android's Bluetooth settings pairs only Bluetooth audio: a piano
  can show up there as "… Audio" while its MIDI side is a separate BLE device ("… MIDI").
- `MidiManager.openBluetoothDevice` hands the link to Android's own BLE MIDI service, which asks for a high
  connection priority and a large MTU by itself. It never reconnects: when the link drops it closes the device and
  removes it from the device list, and the app has to open it again.
- Most pianos accept one BLE central at a time. While another app or computer is connected, the piano does not
  advertise and a scan does not find it.
- On API 31+ (our minSdk) scanning needs `BLUETOOTH_SCAN`, declared with `neverForLocation`, and connecting needs
  `BLUETOOTH_CONNECT`. The library manifest declares both; asking the user for them is the app's job (slice 1.4m).

## Scan
- `scan()` emits an empty list first, then the list of devices found so far after each new one, de-duplicated by
  address, and completes after `Tunables.BLE_SCAN_TIMEOUT_MS` (12 s) or when the collector cancels. Scan mode is
  low latency.
- A found device gets the id `ble:<ADDRESS>` (upper case), the same id it is listed under once connected, and the
  name from its advertisement, else the placeholder "MIDI device".
- Devices already listed when the scan starts (connected) are left out.

## Connect, drop, reconnect
- `connect(device)` takes a device found by a scan of the same adapter (others are unknown and fail with
  `MidiDeviceUnavailableException`), opens it through Android's BLE MIDI service and returns once the input and
  output listings show it. If it is not listed within `BLE_LISTED_TIMEOUT_MS` (5 s) of opening (the link dropped
  in between), the handle is closed and `connect` fails; during reconnecting such an open counts as a failed
  attempt. A device that is already connected and listed is left as it is; one that is waiting to
  be reopened is opened again at once.
- When the link drops, the device leaves the listings and running `events()` flows complete, as the MIDI port
  contracts say. The adapter closes the dead handle and reopens the device after `BLE_RECONNECT_INITIAL_MS` (1 s),
  doubling the wait up to `BLE_RECONNECT_MAX_MS` (30 s). No attempt starts later than `BLE_RECONNECT_GIVE_UP_MS`
  (5 min, counted over the waits) after the drop; then the adapter forgets the device. While Bluetooth is off or a
  permission is missing, an attempt is skipped but its wait still counts. After a successful reopen the device is
  listed under the same id; callers collect `events()` again. A later drop starts the backoff over.
- `disconnect(device)` stops the retries and closes the device. Connected devices are remembered in memory only:
  remembering the last piano across app restarts needs storage and comes later.
- `connect` opens the device while holding the adapter's connection lock, so connecting a second piano waits for
  the first open to finish. The app connects one piano at a time; per-device locking can come with multi-piano use.
- Reconnecting runs in the scope the adapter was created with (an app-lived scope on Android; see ADR 0015).
- Errors: a missing permission gives `BluetoothUnavailableException(PERMISSION_DENIED)`; Bluetooth off, no Bluetooth
  LE, or a scan the platform refuses gives `BLUETOOTH_OFF`.

## Still unverified (owner test T8 on the real piano)
- The scan and connect glue on a phone; whether the piano needs re-pairing after a drop; which error codes the
  scan reports in practice (every scan failure is reported as `BLUETOOTH_OFF` for now).
- Latency and jitter over BLE, and how often the link drops in a 60-minute session. The scan timeout and the three
  reconnect values are first guesses to revisit after that soak test.
