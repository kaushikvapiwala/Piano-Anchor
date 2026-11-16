#!/usr/bin/env bash
# Captures one on-device test session into captures/session-<time>/ (git-ignored): the full logcat, the app's own
# lines, crashes, Android's MIDI state, device and app versions, and a screen recording. Start it, test on the phone,
# then press Enter (or Ctrl-C) to stop and collect. See docs/debugging.md.
#
# Usage: scripts/capture_session.sh [-s SERIAL] [--no-video] [--bugreport] [-o DIR]
#   -s SERIAL     the device to use when several are connected (as in `adb devices`)
#   --no-video    skip the screen recording
#   --bugreport   also take a full `adb bugreport` at the end (slow; for Bluetooth trouble or an ANR)
#   -o DIR        write into DIR instead of captures/session-<time>
# ADB=/path/to/adb overrides where adb is found.
set -u

PACKAGE="io.github.kaushikvapiwala.pianoanchor"
# Android's screenrecord stops by itself after 180 s, so the recording is taken in segments of this length.
SEGMENT_SECONDS=180
DEVICE_VIDEO_PREFIX="/sdcard/piano-anchor-capture-"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

usage() { sed -n '2,11s/^# \{0,1\}//p' "$0"; }

serial="" video=1 bugreport=0 out=""
while [ $# -gt 0 ]; do
    case "$1" in
        -s) serial="${2:?-s needs a serial}"; shift 2 ;;
        --no-video) video=0; shift ;;
        --bugreport) bugreport=1; shift ;;
        -o) out="${2:?-o needs a directory}"; shift 2 ;;
        -h | --help) usage; exit 0 ;;
        *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

find_adb() {
    if [ -n "${ADB:-}" ]; then echo "$ADB"; return; fi
    if command -v adb > /dev/null 2>&1; then command -v adb; return; fi
    for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        if [ -n "$sdk" ] && [ -x "$sdk/platform-tools/adb" ]; then echo "$sdk/platform-tools/adb"; return; fi
    done
    return 1
}

if ! adb_bin="$(find_adb)"; then
    echo "adb not found: install Android platform-tools, or set ADB or ANDROID_HOME" >&2
    exit 1
fi
adb() { if [ -n "$serial" ]; then "$adb_bin" -s "$serial" "$@"; else "$adb_bin" "$@"; fi; }

if [ "$(adb get-state 2> /dev/null)" != "device" ]; then
    echo "no device ready: connect the phone (USB debugging on, or adb connect <ip:port> over Wi-Fi)," >&2
    echo "accept the debugging prompt, and pass -s SERIAL if several are connected (adb devices)" >&2
    exit 1
fi

out="${out:-$ROOT/captures/session-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$out" || exit 1

{
    echo "captured: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "device: $(adb shell getprop ro.product.manufacturer | tr -d '\r') $(adb shell getprop ro.product.model | tr -d '\r')"
    echo "android: $(adb shell getprop ro.build.version.release | tr -d '\r') (SDK $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"
    echo "app:"
    adb shell dumpsys package "$PACKAGE" | tr -d '\r' | grep -E 'versionName|versionCode|lastUpdateTime' | sort -u
} > "$out/device.txt"
if ! grep -q versionName "$out/device.txt"; then
    echo "warning: $PACKAGE is not installed on the device" >&2
fi

adb logcat -c
adb logcat -v threadtime > "$out/logcat.txt" 2>&1 &
logcat_pid=$!

stop_flag="$out/.stop"
video_pid=""
if [ "$video" = 1 ]; then
    adb shell rm -f "${DEVICE_VIDEO_PREFIX}*.mp4" > /dev/null 2>&1
    (
        segment=1
        while [ ! -e "$stop_flag" ]; do
            adb shell screenrecord --time-limit "$SEGMENT_SECONDS" "$DEVICE_VIDEO_PREFIX$segment.mp4" > /dev/null 2>&1
            segment=$((segment + 1))
        done
    ) &
    video_pid=$!
fi

finished=0
finish() {
    [ "$finished" = 1 ] && return
    finished=1
    echo
    echo "collecting…"
    touch "$stop_flag"
    if [ -n "$video_pid" ]; then
        adb shell pkill -INT screenrecord > /dev/null 2>&1
        wait "$video_pid" 2> /dev/null
        for file in $(adb shell ls "${DEVICE_VIDEO_PREFIX}*.mp4" 2> /dev/null | tr -d '\r'); do
            adb pull "$file" "$out/" > /dev/null && adb shell rm -f "$file" > /dev/null 2>&1
        done
    fi
    kill "$logcat_pid" 2> /dev/null
    wait "$logcat_pid" 2> /dev/null
    rm -f "$stop_flag"
    grep -E ' [VDIWEF] (PianoAnchor[A-Za-z]*|AndroidRuntime) *:' "$out/logcat.txt" > "$out/app.txt"
    adb logcat -b crash -d > "$out/crash.txt" 2>&1
    adb shell dumpsys midi > "$out/midi.txt" 2>&1
    if [ "$bugreport" = 1 ]; then
        echo "taking a bug report (a few minutes)…"
        adb bugreport "$out/bugreport.zip" > /dev/null 2>&1 || echo "warning: the bug report failed" >&2
    fi
    echo "saved to $out:"
    (cd "$out" && ls -1)
    echo "app lines: $(wc -l < "$out/app.txt" | tr -d ' '), crash lines: $(wc -l < "$out/crash.txt" | tr -d ' ')"
    echo "For note trouble, export a fixture from the monitor and add it to this folder."
}
trap 'finish; exit 0' INT TERM

echo "capturing into $out"
[ "$video" = 1 ] && echo "recording the screen in ${SEGMENT_SECONDS}s segments"
echo "test on the phone now; press Enter (or Ctrl-C) to stop"
read -r _ || true
finish
