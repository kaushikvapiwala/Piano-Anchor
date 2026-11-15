# Releasing Piano Anchor

A release is a `vX.Y.Z` tag. Pushing it runs `.github/workflows/release.yml`, which builds a signed, minified APK,
smoke-tests it and publishes a GitHub Release. The in-app update check (`UpdateCheckPort`) reads that Release's
`tag_name` and `html_url`, so the tag is the version users see.

## 1. One-time setup (owner)

1. Create a keystore and keep it somewhere safe and backed up. **Losing it means no update can ever install over an
   existing install.**

   ```
   keytool -genkeypair -v -keystore piano-anchor-release.jks -alias piano-anchor \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. In the repository settings (Secrets and variables, Actions), add four secrets:
   - `ANDROID_KEYSTORE_BASE64`: `base64 -w0 piano-anchor-release.jks`
   - `ANDROID_KEYSTORE_PASSWORD`: the store password
   - `ANDROID_KEY_ALIAS`: `piano-anchor` (or the alias you chose)
   - `ANDROID_KEY_PASSWORD`: the key password
3. Create the issue labels `dogfood` and `blocker` (Issues, Labels). The `dogfood` issue template uses the first.

If a secret is missing, the `build` job fails at once and names it. The workflow decodes the keystore to a temporary
file, hands only its path and the passwords to Gradle through environment variables, and deletes the file at the end.
Nothing secret is written into the repository.

## 2. Cut a release

1. Make sure `main` is green and the checklist below is done.
2. Tag the commit and push the tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.
3. Watch the Release workflow. It runs three jobs:
   - `build`: `ciBuild`, then `:app-android:assembleRelease`, then `apksigner verify`, then the SHA-256.
   - `smoke`: installs the release APK on an emulator and checks that the first screen renders.
   - `publish` (only after both pass, and the only job with write access): creates the Release.
4. To run it again for an existing tag (for example after a flaky smoke test), start the workflow by hand
   (Actions, Release, Run workflow) and enter the tag. The Release of that tag is replaced; the tag stays. A broken
   release is fixed with a new patch version, never by moving a tag.

What the Release holds:
- Title `Piano Anchor X.Y.Z`, asset `piano-anchor-X.Y.Z.apk` and its `.sha256` file.
- Notes drawn from the Conventional Commit subjects since the previous tag, grouped into features, fixes and other,
  then the SHA-256 and the install steps (allow installs from this source). There is no changelog file.
- A pre-release flag while the major version is 0.

## 3. Versions

- versionName is the tag without the `v`. versionCode is `X*10000 + Y*100 + Z`, so `0.1.0` is 100 and `1.2.3` is
  10203. Minor and patch must each be at most 99; the build fails otherwise
  (`build-logic/convention/src/main/kotlin/pianoanchor/ReleaseVersion.kt`).
- The workflow passes the version to Gradle as `-PpianoanchorVersion=X.Y.Z`. Builds without it are `0.0.0-dev`,
  versionCode 1. The update check compares `X.Y.Z` numbers only, so a dev build never offers an update.
- A local build of the release type without the signing variables is unsigned, which is enough to check the R8 rules:
  `./gradlew :app-android:assembleRelease -PpianoanchorVersion=0.1.0`.

## 4. The smoke test

The emulator end-to-end test (`app-android/src/androidTest`) feeds notes from a virtual piano that exists only in
debug builds, so it cannot run against a minified release APK. The `smoke` job instead boots an Automated Test
Device emulator (the same image family as `atdPixel`), installs the release APK, launches it and checks that
Onboarding ("Press any key on your piano") or Today ("Practised N of the last M days") renders. A missing Koin
definition, serializer or SQLite class that R8 removed shows up as a crash or a blank screen there.

## 5. Checklist before tagging

- `main` is green (CI and the nightly emulator end-to-end run).
- The keep rules in `app-android/proguard-rules.pro` still cover any new reflection-based library.
- Real-piano checks on the debug build or the last release candidate: the pending items under "Owner device checks"
  in `docs/ROADMAP.md`, and the rule that no piano setting is written unless it is documented and verified
  (`AGENTS.md`, "Never").
- After the release: install the APK from the Release page on the phone and check that the app opens.

## 6. Dogfood: the M3 exit check

After v0.1.0 the owner uses the released APK for practice. Today's practice-day line shows how many days count.
Report each bug or annoyance as an issue from the "Dogfood finding" template (label `dogfood`); the owner adds
`blocker` to anything that stops practice or loses data. M3 is done when there are 14 practice days within 16 and no
open `dogfood` issue is labelled `blocker`.
