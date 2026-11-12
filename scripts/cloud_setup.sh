#!/usr/bin/env bash
# SessionStart hook for Claude Code cloud sessions: installs the Android SDK so `./gradlew fastCheck` can run.
# Does nothing outside the cloud (CLAUDE_CODE_REMOTE != true). Never blocks the session: failures print a
# message and exit 0, and fastCheck then fails with its own clear error.
set -euo pipefail

[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="$HOME/android-sdk"
BASE="${ANDROID_REPO_BASE:-https://dl.google.com/android/repository}"
# Pinned fallback, used only when repository2-1.xml cannot be parsed.
FALLBACK_ZIP="commandlinetools-linux-11076708_latest.zip"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

say() { echo "cloud_setup: $*"; }
giveup() { say "$* (continuing; fastCheck will fail until the Android SDK is installed)"; exit 0; }

# `java -version` may print "Picked up JAVA_TOOL_OPTIONS: ..." first, so parse the line with `version "`.
jdk_major="$(java -version 2>&1 | sed -n '/version "/{s/.*version "\([0-9]*\)[."].*/\1/p;q;}')" || jdk_major=""
if [ "${jdk_major:-0}" -lt 17 ] 2>/dev/null; then
  say "warning: no JDK >= 17 found to launch Gradle"
fi

# Maven Central sometimes answers 429 on the cloud's shared egress proxy; let Gradle retry with backoff.
mkdir -p "$HOME/.gradle"
for prop in "systemProp.org.gradle.internal.repository.max.retries=6" \
  "systemProp.org.gradle.internal.repository.initial.backoff=2000"; do
  grep -qs "^${prop%%=*}=" "$HOME/.gradle/gradle.properties" || echo "$prop" >>"$HOME/.gradle/gradle.properties"
done

# Maven Central answers 429 on the cloud's shared egress IP, so route it to Google's public mirror (outside the repo).
mkdir -p "$HOME/.gradle/init.d"
cat >"$HOME/.gradle/init.d/cloud-maven-mirror.init.gradle.kts" <<'KTS'
// Written by scripts/cloud_setup.sh in Claude Code cloud sessions only: Maven Central 429s on the shared egress IP.
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
val central = setOf("repo.maven.apache.org", "repo1.maven.org")

fun RepositoryHandler.useMirror() =
    withType<MavenArtifactRepository>().configureEach { if (url.host in central) setUrl(mirror) }

gradle.beforeSettings {
    pluginManagement.repositories.useMirror()
    dependencyResolutionManagement.repositories.useMirror()
}
gradle.allprojects {
    repositories.useMirror()
    buildscript.repositories.useMirror()
}
KTS

SDK_API="$(sed -n 's/^android-compileSdk *= *"\([0-9]*\)".*/\1/p' "$ROOT/gradle/libs.versions.toml")"
[ -n "$SDK_API" ] || giveup "cannot read android-compileSdk from gradle/libs.versions.toml"

install_cmdline_tools() {
  local tmp zip_name sha1 meta
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  zip_name="$FALLBACK_ZIP"
  sha1=""
  if curl -fsSL --max-time 60 "$BASE/repository2-1.xml" -o "$tmp/repo.xml" 2>/dev/null &&
    meta="$(python3 - "$tmp/repo.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
for pkg in ET.parse(sys.argv[1]).getroot().iter("remotePackage"):
    if pkg.get("path") != "cmdline-tools;latest":
        continue
    for archive in pkg.iter("archive"):
        if archive.findtext("host-os") == "linux":
            c = archive.find("complete")
            print(c.findtext("url"), c.findtext("checksum") or "")
            sys.exit(0)
sys.exit(1)
PY
    )"; then
    zip_name="${meta%% *}"
    [ "$meta" = "$zip_name" ] || sha1="${meta#* }"
  else
    say "could not read $BASE/repository2-1.xml; trying pinned $FALLBACK_ZIP"
  fi
  curl -fsSL --max-time 600 "$BASE/$zip_name" -o "$tmp/tools.zip" || giveup "download of $BASE/$zip_name failed"
  if [ -n "$sha1" ] && [ "$(sha1sum "$tmp/tools.zip" | cut -d' ' -f1)" != "$sha1" ]; then
    giveup "SHA-1 mismatch for $zip_name"
  fi
  unzip -q "$tmp/tools.zip" -d "$tmp/x" || giveup "cannot unpack $zip_name"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/x/cmdline-tools" "$SDK/cmdline-tools/latest"
}

[ -x "$SDKMANAGER" ] || install_cmdline_tools

export ANDROID_HOME="$SDK"
platform_dir() { compgen -G "$SDK/platforms/android-$SDK_API" >/dev/null || compgen -G "$SDK/platforms/android-$SDK_API.*" >/dev/null; }
if ! platform_dir || [ ! -d "$SDK/platform-tools" ]; then
  # `yes` gets SIGPIPE when sdkmanager stops reading; pipefail would call that a failure.
  { yes 2>/dev/null || true; } | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
  # Google names platforms with a minor version (android-37.0) and sdkmanager exits 0 for unknown ids.
  available="$("$SDKMANAGER" --list 2>/dev/null | awk '{print $1}' | grep -E "^platforms;android-$SDK_API(\.[0-9]+)?$" || true)"
  platform_id=""
  for candidate in "platforms;android-$SDK_API.0" "platforms;android-$SDK_API"; do
    if grep -qx "$candidate" <<<"$available"; then platform_id="$candidate"; break; fi
  done
  [ -n "$platform_id" ] || platform_id="$(sort -V <<<"$available" | tail -n1)"
  { yes 2>/dev/null || true; } | "$SDKMANAGER" "platform-tools" ${platform_id:+"$platform_id"} >/dev/null 2>&1 ||
    giveup "sdkmanager could not install platform-tools and ${platform_id:-android-$SDK_API}"
  platform_dir || say "platform android-$SDK_API not installed (no matching package found); AGP can still auto-install it during the build"
fi

if ! grep -qs '^sdk.dir' "$ROOT/local.properties"; then
  echo "sdk.dir=$SDK" >>"$ROOT/local.properties"
fi
if [ -n "${CLAUDE_ENV_FILE:-}" ] && ! grep -qs "ANDROID_HOME=$SDK" "$CLAUDE_ENV_FILE"; then
  echo "export ANDROID_HOME=$SDK" >>"$CLAUDE_ENV_FILE"
fi
# Warm the Gradle cache (plugins, toolchain resolver) so the first fastCheck does not hit a transient 429.
warmed=0
for delay in 0 20; do
  sleep "$delay"
  if (cd "$ROOT" && ./gradlew help --no-daemon -q >/dev/null 2>&1); then warmed=1; break; fi
done
[ "$warmed" -eq 1 ] || say "Gradle warm-up failed (likely Maven Central 429), fastCheck may fail; retry later"
if platform_dir; then
  say "Android SDK ready at $SDK (platform android-$SDK_API)"
else
  say "Android SDK tools ready at $SDK (platform android-$SDK_API missing)"
fi
