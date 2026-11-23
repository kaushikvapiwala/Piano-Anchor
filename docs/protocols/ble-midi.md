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
- `connect(device)` takes a device found by a scan of the same adapter, or one whose id is `ble:<ADDRESS>`: the
  adapter reads the address from the id and opens it without a scan, as when the app reconnects the stored last
  piano at start (any other unknown id, such as `usb:`, fails with `MidiDeviceUnavailableException`; so does an
  address that does not answer). It opens the device through Android's BLE MIDI service and returns once the input and
  output listings show it. If it is not listed within `BLE_LISTED_TIMEOUT_MS` (5 s) of opening (the link dropped
  in between), the handle is closed and `connect` fails; during reconnecting such an open counts as a failed
  attempt. A device that is already connected and listed is left as it is; one that is waiting to
  be reopened is opened again at once.
- When the link drops, the adapter rides it out (next section). The adapter closes the dead handle and reopens the device after `BLE_RECONNECT_INITIAL_MS` (1 s),
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

## Riding out a brief drop
Android's link to some pianos drops for 1 to 3 s and comes back by itself, so the adapter hides short drops from
its readers. It applies only to Bluetooth devices the adapter keeps connected (`connect` was called and
`disconnect` was not); a USB device that leaves is gone at once, as is a Bluetooth device the user disconnected.
- **Listing.** When a kept device leaves the system list, `inputDevices` goes on listing it for up to
  `BLE_DROP_GRACE_MS` (5 s). If the system lists it again within that time (under the same id, with a new system
  id) the listing never changes (the hold is shared, so a reader that starts during the gap sees the device too); if not, the device is removed when the grace ends. `disconnect`, or the
  reconnecting giving up, removes it at once.
- **Events.** When a kept device's input closes, `events()` stays open and waits for the system to list the device
  again, up to `BLE_DROP_GRACE_MS` counted from the moment the system list drops it (the same moment the list's hold
  starts, so a flow never ends while its device is still listed). It ends at once if the device stops being kept
  (`disconnect`, or the reconnecting gave up). The parser starts afresh on the new link. It then opens the new input, emits `LinkResumed` and carries on with the
  messages that follow. `LinkResumed` is stamped with the clock the platform stamps use, never earlier than the
  last event, so stamps keep to the port's non-decreasing rule. If the device is not back in time, or its input can't
  be opened, the flow completes as before. Cancelling the collector during the wait closes nothing a second time:
  the old input was closed before the wait began.
- **What readers do.** Messages sent in the gap are lost. The onboarding, Pianos, Today and Songs see nothing (the
  list and the note stream carry on); the monitor shows no line; a wait-mode run carries on, as it only waits for
  the next right note (keys and held notes are released at the resume); a timed run, whose take would be missing notes, is discarded unsaved and starts over like
  Restart, saying "The piano disconnected for a moment. Starting again." until the next note. A spot loop restarts
  its current loop and keeps its clean streak.
- Assumption: a reopened device keeps its id, because ids come from the Bluetooth address.

## Still unverified (owner test T8 on the real piano)
- The scan and connect glue on a phone; whether the piano needs re-pairing after a drop; which error codes the
  scan reports in practice (every scan failure is reported as `BLUETOOTH_OFF` for now).
- Latency and jitter over BLE, and how often the link drops in a 60-minute session. The scan timeout and the three
  reconnect values are first guesses to revisit after that soak test.
