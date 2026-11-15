# Update check (GitHub Releases API)

How `:platform:android-network` tells the learner that a newer release exists (slice M3.6), behind `UpdateCheckPort`.
This is the app's only network call (ADR 0004, amendment). Code:
`platform/android-network/src/commonMain/kotlin/pianoanchor/platform/androidnetwork/GitHubUpdateCheckAdapter.kt`
(request and response handling) and
`platform/android-network/src/androidMain/kotlin/pianoanchor/platform/androidnetwork/HttpsUrlConnectionGetter.kt`
(glue).

## The request
- `GET https://api.github.com/repos/<owner>/<name>/releases/latest`, where `<owner>/<name>` is the build constant
  `UPDATE_REPOSITORY` in `app-android/build.gradle.kts` (`kaushikvapiwala/Piano-Anchor`); a fork changes it there.
- Two headers: `Accept: application/vnd.github+json` and `User-Agent: PianoAnchor/<installed version>`. No
  authorization, no cookies, no identifiers, no body, no query string. The platform's HTTPS stack adds its own
  transport headers (`Host`, `Connection`, `Accept-Encoding`); the app adds nothing else.
- Platform `HttpsURLConnection`, 10 seconds to connect and 10 seconds to read. No HTTP library: one GET doesn't justify
  OkHttp or Ktor.
- What GitHub learns: the phone's IP address and the app version, as with any web request.

## The response
Only a `200` answer counts. Of its JSON the adapter reads two fields and ignores the rest:
- `tag_name`, such as `v0.2.0`: the version, without a leading `v`.
- `html_url`: the release page. It must start with `https://github.com/<owner>/<name>/releases/`, otherwise the whole
  answer is ignored, so a changed answer can't send the learner to another site.

Every other outcome (offline, timeout, `404` when no release exists, `403` or `429` when the rate limit is hit, an
unreadable body) is `null`: the check is silent, with no banner and no error card.

## When it runs
- When Today opens, if the Settings switch "update check" is on, and at most once per learner day (the day that starts
  at `LEARNER_DAY_START_HOUR`). The day is stored before the request, so a failed request is not retried until the next
  day.
- Three settings keys hold its state: `last_update_check` (the day), `latest_release` (version and page of the newest
  release seen) and `dismissed_update` (the version whose banner was dismissed).
- With the switch off no request is made at all, and no banner shows.

## The banner
Today shows "Version X is available" when `isNewer(latest, installed)` is true (`X.Y.Z` compared number by number, an
optional leading `v`; malformed means no banner) and the version is newer than the dismissed one. "See release" opens
the page in the browser; "Not now" hides the banner until a still newer version appears. The app never downloads or
installs an APK.

## Turning it off
Settings, "update check" switch. The app also needs the `INTERNET` permission for this and nothing else; an architecture
test (`ArchitectureTest`) fails when any other module imports `java.net`, `javax.net`, `okhttp3` or `io.ktor.client`.
