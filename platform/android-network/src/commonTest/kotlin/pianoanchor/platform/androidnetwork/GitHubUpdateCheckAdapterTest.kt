package pianoanchor.platform.androidnetwork

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Release
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private const val PAGE = "https://github.com/$REPOSITORY/releases/tag/v0.2.0"

class GitHubUpdateCheckAdapterTest {
    private fun answer(
        tag: String = "v0.2.0",
        page: String = PAGE,
        extra: String = "",
    ) = HttpResponse(200, """{"id":1,"tag_name":"$tag","html_url":"$page"$extra}""")

    private fun adapter(getter: HttpGetter) = GitHubUpdateCheckAdapter(getter, "0.1.0", REPOSITORY)

    @Test
    fun `parses tag and page`() =
        runTest {
            val getter = FakeHttpGetter(answer(extra = ""","name":"Second","assets":[{"url":"x"}],"draft":false"""))
            assertEquals(Release("0.2.0", PAGE), adapter(getter).latest())
        }

    @Test
    fun `strips a leading v`() =
        runTest {
            assertEquals("0.2.0", adapter(FakeHttpGetter(answer(tag = "v0.2.0"))).latest()?.version)
            assertEquals("0.2.0", adapter(FakeHttpGetter(answer(tag = "0.2.0"))).latest()?.version)
        }

    @Test
    fun `404, 403 rate limit, timeout and malformed JSON give null`() =
        runTest {
            val getters =
                listOf(
                    FakeHttpGetter(HttpResponse(404, """{"message":"Not Found"}""")),
                    FakeHttpGetter(HttpResponse(403, """{"message":"API rate limit exceeded"}""")),
                    FakeHttpGetter(failure = RuntimeException("timeout")),
                    FakeHttpGetter(HttpResponse(200, "not json")),
                    FakeHttpGetter(HttpResponse(200, """{"tag_name":"v1.0.0"}""")),
                    FakeHttpGetter(answer(tag = "v")),
                )
            getters.forEach { assertNull(adapter(it).latest()) }
        }

    @Test
    fun `rejects a page outside the repo's releases`() =
        runTest {
            for (page in listOf(
                "https://evil.example/kaushikvapiwala/Piano-Anchor/releases/tag/v0.2.0",
                "https://github.com/kaushikvapiwala/Piano-Anchor/issues/1",
                "https://github.com/kaushikvapiwala/Piano-Anchor-fork/releases/tag/v0.2.0",
                "http://github.com/$REPOSITORY/releases/tag/v0.2.0",
            )) {
                assertNull(adapter(FakeHttpGetter(answer(page = page))).latest(), page)
            }
        }

    @Test
    fun `sends only the two headers`() =
        runTest {
            val getter = FakeHttpGetter(answer())
            adapter(getter).latest()
            assertEquals(
                listOf(
                    "https://api.github.com/repos/$REPOSITORY/releases/latest" to
                        mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "PianoAnchor/0.1.0"),
                ),
                getter.requests,
            )
        }

    @Test
    fun `cancellation is not swallowed`() =
        runTest {
            val getter = FakeHttpGetter(failure = CancellationException("cancelled"))
            assertFailsWith<CancellationException> { adapter(getter).latest() }
        }
}
