package pianoanchor.platform.androidnetwork

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pianoanchor.core.model.Release
import pianoanchor.core.ports.UpdateCheckPort

private const val HTTP_OK = 200
private const val ACCEPT = "application/vnd.github+json"

private val json = Json { ignoreUnknownKeys = true }

/** The two fields read from GitHub's "latest release" answer; every other field is ignored. */
@Serializable
private class LatestRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
)

/**
 * [UpdateCheckPort] over GitHub's public Releases API: one anonymous GET of `/repos/[repository]/releases/latest`
 * with an `Accept` and a `User-Agent: PianoAnchor/[appVersion]` header, nothing else. The tag becomes the version
 * (without a leading `v`); the page must lie under that repository's releases, otherwise the answer is ignored. Any
 * failure is null. [repository] is `owner/name`. Create it on Android with
 * `GitHubUpdateCheckAdapter(appVersion, repository)`.
 * Contract: `UpdateCheckPortContractTest`; details in `docs/protocols/update-check.md`.
 */
public class GitHubUpdateCheckAdapter internal constructor(
    private val getter: HttpGetter,
    private val appVersion: String,
    private val repository: String,
) : UpdateCheckPort {
    override suspend fun latest(): Release? =
        try {
            val response =
                getter.get(
                    "https://api.github.com/repos/$repository/releases/latest",
                    mapOf("Accept" to ACCEPT, "User-Agent" to "PianoAnchor/$appVersion"),
                )
            if (response.status == HTTP_OK) release(json.decodeFromString<LatestRelease>(response.body)) else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Offline, a timeout, an unreadable answer: the check is best effort and silent.
            null
        }

    private fun release(answer: LatestRelease): Release? {
        val version = answer.tagName.removePrefix("v")
        val onReleasePage = answer.htmlUrl.startsWith("https://github.com/$repository/releases/")
        return if (version.isNotBlank() && onReleasePage) Release(version, answer.htmlUrl) else null
    }
}
