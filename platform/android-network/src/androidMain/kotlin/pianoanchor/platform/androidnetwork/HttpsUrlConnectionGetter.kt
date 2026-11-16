package pianoanchor.platform.androidnetwork

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pianoanchor.core.model.ExcludeFromCoverage
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val GLUE = "thin HttpsURLConnection glue; checked on a device against the real API (owner check, M3.6)"

/** Time allowed to connect, and then to wait for data: the update check must never hold anything up for long. */
private const val TIMEOUT_MS = 10_000

/** Logcat tag of the network glue (docs/debugging.md); debug and info lines are stripped from release. */
private const val TAG = "PianoAnchorNet"

/**
 * Creates the update check for [repository] (`owner/name`), identifying the app as `PianoAnchor/[appVersion]`. It
 * keeps no state, so one instance serves the whole app.
 */
@ExcludeFromCoverage(GLUE)
public fun GitHubUpdateCheckAdapter(
    appVersion: String,
    repository: String,
): GitHubUpdateCheckAdapter = GitHubUpdateCheckAdapter(HttpsUrlConnectionGetter(), appVersion, repository)

/**
 * [HttpGetter] over `HttpsURLConnection`: [TIMEOUT_MS] to connect and to read, no cache, no cookies and no request
 * body. Blocks a `Dispatchers.IO` thread for at most the timeouts. Logs each request's status or failure under [TAG].
 */
@ExcludeFromCoverage(GLUE)
internal class HttpsUrlConnectionGetter : HttpGetter {
    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): HttpResponse =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.useCaches = false
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                val status = connection.responseCode
                if (status <
                    HTTP_BAD_REQUEST
                ) {
                    Log.i(TAG, "GET $url: HTTP $status")
                } else {
                    Log.w(TAG, "GET $url: HTTP $status")
                }
                val stream = if (status < HTTP_BAD_REQUEST) connection.inputStream else connection.errorStream
                HttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } catch (e: IOException) {
                Log.w(TAG, "GET $url failed", e)
                throw e
            } finally {
                connection.disconnect()
            }
        }
}

private const val HTTP_BAD_REQUEST = 400
