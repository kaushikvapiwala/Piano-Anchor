# 0020. Compose UI tests on Robolectric

- Status: accepted
- Date: 2026-11-13

## Context
ADR 0007 puts Compose UI tests in the pyramid, and screenshots (ADR 0018) show how a screen looks but not that its
controls act or what TalkBack reads. The results screen (slice 1.10) is the first to need such a test.

## Decision
- Compose Multiplatform's **`org.jetbrains.compose.ui:ui-test`** (Apache-2.0, same version as Compose
  Multiplatform) with `androidx.compose.ui.test.v2.runComposeUiTest`, in the feature module's `androidHostTest`
  on Robolectric, next to the screenshot tests. **`androidx.compose.ui:ui-test-manifest`** (Apache-2.0, the AndroidX
  Compose version Compose Multiplatform resolves to) registers the activity the test hosts content in.
- Test only: no production module depends on them.

## Consequences
UI tests run in `fastCheck` on the JVM without an emulator. Bump `ui-test-manifest` with Compose Multiplatform
(its AndroidX Compose version shows in the module's dependency report).
