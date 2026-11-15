# Architecture

Piano Anchor uses **hexagonal architecture** (ports and adapters) inside a Kotlin Multiplatform build.
The domain knows nothing about Android, MIDI hardware, databases or UI; those plug in through ports.
Rules in this document are enforced by `architecture-tests/` (Konsist) and the coverage gate.

```
   driving adapters                    domain (pure Kotlin)                  driven adapters
 ┌──────────────────┐        ┌───────────────────────────────────┐        ┌───────────────────┐
 │ ui/shell, feature│ calls  │ engine: matcher, analysis, learner │ calls  │ platform MIDI,    │
 │ app-android      │ ─────▶ │ model, scheduler, session          │ ─────▶ │ storage, clock,   │
 │ app-desktop      │        │ core/model · core/ports (*Port)    │  ports │ instrument drivers│
 └──────────────────┘        └───────────────────────────────────┘        └───────────────────┘
```

## Modules

| Module | Role | Notes |
|---|---|---|
| `:core:model` | Domain types (e.g. `MidiNote`, `MidiEvent`, `Attempt`) | Pure Kotlin, 100% coverage |
| `:core:ports` | Driven ports (e.g. `ClockPort`, `MidiInputPort`, `MidiOutputPort`, `MidiDiscoveryPort`, `MetronomePort`, `InstrumentRepositoryPort`, `AttemptRepositoryPort`, `CalibrationRepositoryPort`, `LearnerRepositoryPort`, `SettingsRepositoryPort`, `UpdateCheckPort`) | Interfaces only, each with KDoc |
| `:engine` | Coaching logic: the `update` version comparison (`isNewer`), the `midi` byte-stream parser, the `kit` contribute-a-piano file (`ContributionKitCodec`), the `matcher` (wait mode), `grading`, `guidance` (key-hint level and delay, spot-loop window and passage slicing), `analysis` (played notes from MIDI events, the offline performance analyser), built-in `content` (the `Curriculum` of units, skills and passages, and the `NoteText` parser); `Tunables` holds every threshold | Pure Kotlin, 100% coverage |
| `:testing` | Fakes (e.g. `FakePiano`, `FakeMetronome`), abstract port contract tests and the analyser test harness (`TakeGenerator`, `ErrorInjector`, `ClassificationScore`) | Used by test source sets only |
| `:platform:android-midi` | Driven adapters over Android's MIDI service: `AndroidMidiAdapter` (both MIDI ports and Bluetooth LE discovery), `AndroidClockAdapter` | Logic in `commonMain`, 100% coverage on the JVM; `androidMain` glue excluded (see Testing) |
| `:platform:android-audio` | Driven adapter over Android's audio output: `AndroidMetronomeAdapter` (synthesised clicks on a low-latency `AudioTrack`, placed by `AudioTrack.getTimestamp`) and `AndroidToneAdapter` (`TonePort`: sine tones from `ToneRenderer`, for the ear screen when the piano has no MIDI output) | Logic in `commonMain` behind the `AudioSink` seam, 100% coverage on the JVM; the `AudioTrackSink` glue is excluded and checked by ear on a device |
| `:platform:android-network` | Driven adapter for the opt-out update check: `GitHubUpdateCheckAdapter` (one anonymous GET of GitHub's latest release, `docs/protocols/update-check.md`); the only module allowed to use networking APIs (architecture test) | Request and JSON handling in `commonMain` behind the `HttpGetter` seam, 100% coverage on the JVM; the `HttpsURLConnection` glue is excluded and checked on a device |
| `:platform:storage` | Driven adapters over the on-device SQLite database (SQLDelight, ADR 0019): `SqlInstrumentRepositoryAdapter`, `SqlAttemptRepositoryAdapter`, `SqlSettingsRepositoryAdapter` (key-value rows), `createPianoAnchorDatabaseDriver` per platform | Adapters in `commonMain` over `SqlDriver`, tested on the JVM SQLite driver at 100% (generated code excluded); the Android driver factory is excluded |
| `:ui:shell` | Shared Compose root, `PianoAnchorTheme` (`docs/ui/living-studio.md`), the shared navigation rail `AppNavRail` and string resources | Driving adapter |
| `:feature:monitor` | Note monitor: `MonitorViewModel`, `MonitorScreen`; records JSONL fixtures (`fixtures/SCHEMA.md`) and saves the contribution kit | Driving adapter over ports and `:engine`; Roborazzi screenshots in `fastCheck` |
| `:feature:practice` | Practice and perform mode: `PracticeViewModel` (matcher, grading, saving the attempt, `RunMode`), `PracticeScreen` with the Canvas `StaffView` and the live `KeyStrip`; the run's results: `ResultsViewModel` and `ResultsScreen` | Driving adapter over ports and `:engine`; Roborazzi screenshots in `fastCheck` |
| `:feature:songs` | Songs: `SongsViewModel` (pieces from `Pieces.list` over skill states and attempt history, piano), `SongsScreen` (unit cards, locked rows) | Driving adapter over ports and `:engine`; Roborazzi screenshots in `fastCheck` |
| `:feature:today` | Today, the start screen: `TodayViewModel` (plan, budget, piano chip, practice days, the update banner), `TodayScreen` (rail, three cards, session bar, update banner); cards are composed by `SessionComposer` in `:engine` | Driving adapter over ports and `:engine`; Roborazzi screenshots in `fastCheck` |
| `:feature:onboarding` | First-launch onboarding: `OnboardingViewModel` (connect on the first note, C-E-G echo through `MidiOutputPort`, placement), `OnboardingScreen`; the calibration step is hosted by the app through a slot | Driving adapter over ports and `:engine`; Roborazzi screenshots in `fastCheck` |
| `:feature:ear` | Ear training: `EarViewModel` (items from `EarGenerator`, MIDI or speaker playback, echo answers read from `MidiInputPort`, one `recordSkill` per finished skill), `EarScreen` (identify buttons, echo key strip, summary) | Driving adapter over ports and `:engine`; Roborazzi screenshots in `fastCheck` |
| `:feature:settings` | Settings: `SettingsViewModel` over `SettingsRepositoryPort`, `SettingsScreen` (daily budget, weekly goal, click, strictness, update check); opened from Today's rail | Driving adapter over ports; Roborazzi screenshots in `fastCheck` |
| `:app-android` | Android app: composition root (Koin module, permission request, file export) and screen navigation (onboarding until `onboarded`, then Today; monitor → settings; monitor → practice → results, Try again → practice; one ViewModel store per route) | No business logic |
| `:app-desktop` | Desktop development harness | No business logic |
| `:architecture-tests` | Konsist rules enforcing this document | Runs in `fastCheck` |

Build configuration lives in `build-logic/` convention plugins: `pianoanchor.kmp-library` (pure modules,
100% coverage gate), `pianoanchor.kmp-compose` (UI), `pianoanchor.android-app`, `pianoanchor.jvm`, and
`pianoanchor.quality` (formatting, detekt, Kover, per-module `fastCheck`). Versions: `gradle/libs.versions.toml`.
Developer hooks (`.githooks/`, `.claude/hooks/`) run the same checks locally; see `docs/adr/0011-developer-hooks.md`.
`docs/generated/` is produced by `./gradlew generateDocs` and checked by `GeneratedDocsTest`; never edit it by hand.

## Dependency rules
- Domain code (`pianoanchor.core.*`, `pianoanchor.engine`) imports only Kotlin, `kotlinx.coroutines` (for `Flow`
  in ports, ADR 0013), `kotlinx.serialization` (fixture format, ADR 0016), `kotlinx.datetime` (learner days, ADR 0022)
  and other domain code: no Android,
  AndroidX, Compose, Koin or `java.*`/`javax.*`.
- `:testing` depends on `:engine` (`FakePiano` uses the real `MidiParser`). Only test code uses it: production code
  outside `pianoanchor.testing` never imports it.
- Domain code never imports driving adapters (`pianoanchor.ui`, `pianoanchor.app`, `pianoanchor.desktop`),
  driven adapters (`pianoanchor.platform`) or test support (`pianoanchor.testing`).
- Platform adapter modules (`platform/*`) depend on `:core:ports` and may use `:engine` helpers (e.g. `MidiParser`).
- Feature modules (`feature/*`, package `pianoanchor.feature`) are driving adapters: a ViewModel
  (`androidx.lifecycle` KMP) and stateless screens, depending on ports, `:engine` and `:ui:shell` (theme), never on
  driven adapters (`platform/*`) or apps.
- Apps are composition roots: they wire adapters to ports (Koin, ADR 0017) and contain no business logic.

## Ports
- A port is a top-level interface in `core/ports`, named `*Port`, documenting its contract in KDoc.
- Ports describe what the domain needs (time, MIDI input/output, storage), not how a platform does it.
- Failures cross a port as domain exceptions from `core/model`, never platform types: MIDI as
  `MidiDeviceUnavailableException`, Bluetooth as `BluetoothUnavailableException`, storage as
  `StorageUnavailableException` (disk full, corrupt or locked database).

## Storage error model
- The repository ports throw `StorageUnavailableException` when the store can't be read or written (`recent`'s flow
  fails with it). Programming errors keep their own types (a duplicate attempt id is `IllegalArgumentException`).
- `:platform:storage` translates the driver's exceptions (`android.database.SQLException`, `java.sql.SQLException`,
  via `Throwable.isStorageFailure()`) in one helper per call and passes everything else through unchanged.
- Screens recover rather than crash: the monitor shows a piano it couldn't save and keeps MIDI flowing
  (`MonitorError.STORAGE`), practice keeps a graded run it couldn't save and offers Retry or Discard, and results
  show "Couldn't load this run." with Retry. Nothing retries automatically.

## Adapters
- An adapter implements a port outside the domain and is named `*Adapter` (class or object).
- Test doubles live in `testing/`, are named `Fake*`, and obey the same contract.

## Testing
- Each port has an abstract `<Port>ContractTest` in `testing/`; for every port it implements, an implementation
  has a test class named `<Impl>…Test` that extends that contract (e.g. `FakeClockContractTest`,
  `FakePianoInputContractTest`), so fakes and real adapters behave the same.
- Every `*ViewModel` and `*UseCase` has a `*Test`; test classes are named `*Test`. ViewModel tests run on the JVM
  with `Dispatchers.setMain` and the fakes from `testing/`.
- Screens have golden screenshots (Roborazzi on Robolectric, `androidHostTest`) in light and dark, committed under
  the module's `screenshots/` and verified by `fastCheck`; `recordRoborazziAndroidHostTest` rewrites them after an
  intended change (`docs/adr/0018-screenshot-tests.md`). Controls and TalkBack labels are checked by Compose UI
  tests (`runComposeUiTest`) in the same source set (`docs/adr/0020-compose-ui-tests.md`).
- The top of the pyramid is one emulator end-to-end test, `WalkingSkeletonEndToEndTest` in
  `app-android/src/androidTest/`: the real app on a Gradle Managed Device, fed by a virtual MIDI piano
  (`VirtualPianoService`, a `MidiDeviceService` in `app-android/src/debug/`, disabled until the test enables it) and
  driven only through its UI. It runs in `.github/workflows/e2e.yml`
  on main, nightly and on PRs that change it, never in `fastCheck` (`docs/adr/0021-emulator-end-to-end-tests.md`).
- Pure modules need 100% line and branch coverage; `@ExcludeFromCoverage("reason")` marks the rare exception.
- Platform adapters split into **logic** and **glue**. The logic talks to the platform through a thin internal
  seam (e.g. `MidiSystem` in `:platform:android-midi`) and lives in `commonMain`, so it runs and is measured on the
  JVM against a fake seam (`FakeMidiSystem`), contract tests included. The glue implements the seam over the real
  platform API in `androidMain`, does no decisions of its own and is `@ExcludeFromCoverage`; it is verified on a
  device: by the emulator end-to-end test over a virtual MIDI device, by the owner's hardware check for the USB and
  Bluetooth LE specifics.
  See `docs/adr/0014-platform-adapter-modules.md`.
- An adapter that works in the background (e.g. reopening a dropped Bluetooth LE device) takes a `CoroutineScope`
  in its internal constructor; the Android factory gives it an app-lived scope and tests pass `backgroundScope`, so
  timing runs in virtual time (`docs/adr/0015-adapter-background-scope.md`).
