package pianoanchor.platform.androidnetwork

import pianoanchor.core.model.Release
import pianoanchor.core.ports.UpdateCheckPort
import pianoanchor.testing.UpdateCheckPortContractTest

class GitHubUpdateCheckAdapterContractTest : UpdateCheckPortContractTest() {
    override fun createPublishing(release: Release): UpdateCheckPort =
        adapter(
            FakeHttpGetter(
                HttpResponse(200, """{"tag_name":"v${release.version}","html_url":"${release.pageUrl}"}"""),
            ),
        )

    override fun createFailing(): List<UpdateCheckPort> =
        listOf(
            FakeHttpGetter(failure = RuntimeException("offline")),
            FakeHttpGetter(HttpResponse(404, "{}")),
            FakeHttpGetter(HttpResponse(200, "<html>")),
        ).map(::adapter)

    private fun adapter(getter: HttpGetter) = GitHubUpdateCheckAdapter(getter, "0.1.0", REPOSITORY)
}

internal const val REPOSITORY = "kaushikvapiwala/Piano-Anchor"
