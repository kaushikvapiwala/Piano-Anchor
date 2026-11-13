package pianoanchor.core.model

import kotlin.time.Instant

/**
 * A piano the app has seen. [deviceId] is its stable device id ([MidiDevice.id]); [name] is the platform's display
 * name; [identity] is null when the piano did not answer the Identity Request. [lastSeen] is never before [firstSeen].
 */
public data class Instrument(
    val deviceId: String,
    val name: String,
    val identity: InstrumentIdentity?,
    val firstSeen: Instant,
    val lastSeen: Instant,
) {
    init {
        require(deviceId.isNotBlank()) { "instrument device id must not be blank" }
        require(lastSeen >= firstSeen) { "lastSeen ($lastSeen) must not be before firstSeen ($firstSeen)" }
    }
}
