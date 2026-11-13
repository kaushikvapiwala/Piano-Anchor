# 0017. Koin for dependency injection, androidx.lifecycle ViewModels

- Status: accepted
- Date: 2026-11-13

## Context
The first screen (note monitor) needs a ViewModel that survives configuration changes and a composition root that
hands it the MIDI adapters. Desktop (1.3) will share the same ViewModels.

## Decision
- **ViewModels:** `androidx.lifecycle:lifecycle-viewmodel` in its KMP build (`org.jetbrains.androidx.lifecycle`,
  Apache-2.0); `viewModelScope` replaces hand-made scope and lifecycle handling. Feature modules use it in
  `commonMain`; tests set `Dispatchers.setMain` to a test dispatcher.
- **DI:** Koin (Apache-2.0, KMP) in composition roots only: `app-android` uses `koin-android` and
  `koin-androidx-compose` (`koinViewModel()`). Feature, engine and adapter code take plain constructor parameters
  and never import Koin (the domain rule forbids it there).
- Rejected: Dagger/Hilt (Android-only, code generation) and manual wiring in the Activity (no ViewModel factory
  story, grows with every screen).

## Consequences
Wiring for a new screen is one `viewModel { … }` line in the app's module. The lifecycle Compose artifact was not
added: screens are stateless and the app collects state with `collectAsState`.
