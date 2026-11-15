package pianoanchor.platform.androidnetwork

/**
 * Seam between [GitHubUpdateCheckAdapter] and the platform's HTTPS client, so the request and the response handling
 * run on the JVM.
 */
internal interface HttpGetter {
    /**
     * One GET of [url] with exactly [headers] (plus whatever the transport itself adds), read as text. Throws when
     * the request can't complete (offline, timeout); a status other than 200 is a normal [HttpResponse].
     */
    suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): HttpResponse
}

/** What a GET answered: its [status] code and [body] text. */
internal data class HttpResponse(
    val status: Int,
    val body: String,
)
