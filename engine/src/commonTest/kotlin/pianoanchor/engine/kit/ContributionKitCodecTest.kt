package pianoanchor.engine.kit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.engine.fixture.Fixture
import pianoanchor.engine.fixture.FixtureCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContributionKitCodecTest {
    private val fixture =
        Fixture(
            Fixture.Header(schema = 1, device = "Fake Piano", recordedAt = "2026-11-12T10:00:00Z"),
            listOf(Fixture.Message(0, byteArrayOf(0x90.toByte(), 0x3C, 0x64))),
        )
    private val identity = InstrumentIdentity(listOf(65), family = 25, model = 1, version = listOf(0, 1, 0, 0))

    private fun fixtureField(text: String) =
        Json
            .parseToJsonElement(text)
            .jsonObject
            .getValue("fixture")
            .jsonPrimitive.content

    @Test
    fun withIdentityWritesTheExactFile() {
        val expected =
            """{"kit":1,"appVersion":"0.1.0","identity":{"manufacturerId":[65],"family":25,"model":1,""" +
                """"version":[0,1,0,0]},"fixture":""" +
                Json.encodeToString(FixtureCodec.encode(fixture)) + "}\n"
        assertEquals(expected, ContributionKitCodec.encode(ContributionKit("0.1.0", identity, fixture)))
    }

    @Test
    fun withoutIdentityWritesNull() {
        val text = ContributionKitCodec.encode(ContributionKit("0.1.0", null, fixture))
        assertTrue(""""identity":null""" in text)
        val expected =
            """{"kit":1,"appVersion":"0.1.0","identity":null,"fixture":""" +
                Json.encodeToString(FixtureCodec.encode(fixture)) + "}\n"
        assertEquals(expected, text)
    }

    @Test
    fun fixtureFieldDecodesToTheInput() {
        val text = ContributionKitCodec.encode(ContributionKit("0.1.0", identity, fixture))
        assertEquals(fixture, FixtureCodec.decode(fixtureField(text)))
    }

    @Test
    fun quoteInAppVersionIsEscaped() {
        val text = ContributionKitCodec.encode(ContributionKit("0.1\"x", null, fixture))
        val parsed = Json.parseToJsonElement(text).jsonObject
        assertEquals("0.1\"x", parsed.getValue("appVersion").jsonPrimitive.content)
        assertEquals(1, text.trimEnd('\n').lines().size)
    }
}
