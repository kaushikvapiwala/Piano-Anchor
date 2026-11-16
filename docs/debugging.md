# Debugging on a phone

How to install a build on an Android phone, read its logs and report what went wrong. The owner device checks in
`docs/ROADMAP.md` and the dogfood in `docs/RELEASING.md` use this.

## Install
1. On the phone: Settings, About phone, tap "Build number" seven times; then Developer options, turn on USB debugging
   (and Wireless debugging, see below).
2. Get the debug APK: the `app-android-debug-<sha>` artifact of the latest CI run on main
   (`gh run download <run-id> -n app-android-debug-<sha>`), or build it with `./gradlew :app-android:assembleDebug`
   (it lands in `app-android/build/outputs/apk/debug/`).
3. `adb install -r <apk>`. On macOS with Android Studio, `adb` is in `~/Library/Android/sdk/platform-tools/`.

A piano on the phone's USB port takes the only cable, so connect `adb` over Wi-Fi (Android 11 or later): Developer
options, Wireless debugging, "Pair device with pairing code", then `adb pair <ip:port>` with the code and
`adb connect <ip:port>` with the address the Wireless debugging screen shows. Bluetooth LE pianos work over either.

## Logs
The platform glue logs to logcat (`docs/adr/0024-glue-logging.md`), one tag per module:

| Tag | What it logs |
|---|---|
| `PianoAnchorMidi` | MIDI devices added and removed, input and output ports opened and closed, Bluetooth LE scans and results, each Bluetooth open (reconnects included), Bluetooth unavailable |
| `PianoAnchorNet` | The update check's request: HTTP status or failure |
| `PianoAnchorDb` | Database failures (disk full, corrupt or locked file) |
| `PianoAnchorAudio` | Metronome writer failures |

Debug builds log every level; release builds keep warnings and errors only (R8 removes the rest). MIDI note bytes are
never logged: record them with the monitor's fixture export instead.

### One command per session
`scripts/capture_session.sh` captures a whole test session: start it, test on the phone, press Enter (or Ctrl-C) to
stop (it also stops cleanly when the terminal closes). It writes `captures/session-<time>/` (git-ignored) with:

| File | Contents |
|---|---|
| `device.txt` | Phone model, Android version, the app's version and install time |
| `logcat.txt` | The full log since the start (the buffer is cleared first) |
| `app.txt` | Every line of the app's own processes (its crashes included) and every `PianoAnchor*` line, picked out of `logcat.txt` |
| `crash.txt` | The crash buffer: crashes of any app, native crashes included |
| `midi.txt` | `dumpsys midi`: Android's view of MIDI devices and open ports at the end |
| `piano-anchor-capture-<n>.mp4` | The screen recording, in 3-minute segments (`--no-video` skips it) |
| `bugreport.zip` | With `--bugreport` only: a full bug report (slow; for Bluetooth trouble or an ANR) |

`-s SERIAL` picks a phone when several are connected; `-h` lists the options. The script finds `adb` on `PATH`, in
`ANDROID_HOME` or in Android Studio's default SDK folder (`ADB=/path/to/adb` overrides it). For note trouble, also copy
the monitor's exported fixture into the session folder.

### By hand
```sh
adb logcat -c                                          # clear old lines first
adb logcat -v threadtime > session.log                 # everything, to a file
adb logcat -v color -s 'PianoAnchorMidi:*' 'PianoAnchorNet:*' 'PianoAnchorDb:*' 'PianoAnchorAudio:*' \
  AndroidRuntime:E                                     # the app's lines and crashes, live
adb logcat -b crash -d                                 # crashes since the last clear
adb shell dumpsys midi                                 # Android's view of MIDI devices and open ports
adb shell screenrecord /sdcard/run.mp4                 # a video of the repro (stop with Ctrl-C), then adb pull
adb bugreport bugreport.zip                            # everything, for Bluetooth trouble or an ANR
```

Android's own Bluetooth and MIDI lines (`BluetoothGatt`, `MidiService`, `BluetoothMidiDevice`) are in the full log, not
the app-only filter.

## Report
File an issue from the "Bug report" template (the "Dogfood finding" template while dogfooding) with the steps,
`app.txt` (or the full log when Bluetooth or MIDI misbehaves), the screen recording and, for anything about notes, the exported fixture JSONL. Logs can hold Bluetooth
addresses and device serials: trim them before attaching if that matters to you.
