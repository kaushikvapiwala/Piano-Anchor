package pianoanchor.platform.androidnetwork

/** An [HttpGetter] that answers [response] or throws [failure], recording each request. */
internal class FakeHttpGetter(
    private val response: HttpResponse? = null,
    private val failure: Exception? = null,
) : HttpGetter {
    /** Each request as (url, headers). */
    val requests = mutableListOf<Pair<String, Map<String, String>>>()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): HttpResponse {
        requests += url to headers
        failure?.let { throw it }
        return checkNotNull(response)
    }
}
