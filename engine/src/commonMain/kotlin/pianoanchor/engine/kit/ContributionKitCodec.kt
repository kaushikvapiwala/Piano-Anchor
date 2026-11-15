package pianoanchor.engine.kit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.engine.fixture.FixtureCodec

/** Writes a [ContributionKit] as the JSON file described in `docs/contribute-a-piano.md`. */
public object ContributionKitCodec {
    /** The file format version this codec writes. */
    public const val VERSION: Int = 1

    /** One JSON object on one line, ended by `\n`. */
    public fun encode(kit: ContributionKit): String {
        val file =
            JsonObject(
                linkedMapOf(
                    "kit" to JsonPrimitive(VERSION),
                    "appVersion" to JsonPrimitive(kit.appVersion),
                    "identity" to (if (kit.identity == null) JsonNull else kit.identity.toJson()),
                    "fixture" to JsonPrimitive(FixtureCodec.encode(kit.fixture)),
                ),
            )
        return file.toString() + "\n"
    }
}

private fun InstrumentIdentity.toJson() =
    JsonObject(
        linkedMapOf(
            "manufacturerId" to manufacturerId.toJson(),
            "family" to JsonPrimitive(family),
            "model" to JsonPrimitive(model),
            "version" to version.toJson(),
        ),
    )

private fun List<Int>.toJson() = JsonArray(map(::JsonPrimitive))
