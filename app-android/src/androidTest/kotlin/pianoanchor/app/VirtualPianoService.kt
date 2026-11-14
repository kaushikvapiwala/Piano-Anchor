package pianoanchor.app

import android.media.midi.MidiDeviceService
import android.media.midi.MidiReceiver
import java.io.ByteArrayOutputStream

/** The name the virtual piano lists under; must match `res/xml/virtual_piano.xml`. */
internal const val TEST_PIANO_NAME = "Piano Anchor Test Piano"

/** The identity the virtual piano replies with: family, model and the 4 version bytes. */
internal const val TEST_PIANO_FAMILY = 1
internal const val TEST_PIANO_MODEL = 1
internal val TEST_PIANO_VERSION = listOf(0, 0, 0, 1)

/** The test's control port: every byte sent to it comes out of the output port unchanged. */
internal const val TEST_PIANO_CONTROL_PORT = 1

private const val NOTES_PORT = 0
private const val SYSEX_START = 0xF0
private const val SYSEX_END = 0xF7
private const val BYTE_MASK = 0xFF
private const val DATA_BITS = 7
private const val DATA_MASK = 0x7F
private const val UNIVERSAL_NON_REAL_TIME = 0x7E
private const val GENERAL_INFORMATION = 0x06
private const val IDENTITY_REQUEST = 0x01
private const val IDENTITY_REPLY = 0x02

/** The MIDI non-commercial manufacturer id: names no vendor. */
private const val NON_COMMERCIAL_ID = 0x7D

/** F0 7E <device id> 06 01 F7. */
private const val REQUEST_SIZE = 6
private const val DEVICE_ID_INDEX = 2
private const val SUB_ID_1_INDEX = 3
private const val SUB_ID_2_INDEX = 4

/**
 * A piano for the emulator end-to-end test, outside the app's process: the app lists and opens it through
 * `MidiManager` like a USB piano, so the real `AndroidMidiAdapter` is under test. Input port 0 (the app's) answers an
 * Identity Request with the test identity and ignores anything else; input port 1 (the test's control port) is
 * forwarded unchanged to the output port. Each input port takes one sender, so the app and the test get one each.
 */
class VirtualPianoService : MidiDeviceService() {
    override fun onGetInputPortReceivers(): Array<MidiReceiver> = arrayOf(IdentityResponder(), Forwarder())

    /** The receiver of the output port; valid once the service has created its server. */
    private fun notes(): MidiReceiver = outputPortReceivers[NOTES_PORT]

    /** Forwards every byte to the output port. */
    private inner class Forwarder : MidiReceiver() {
        override fun onSend(
            msg: ByteArray,
            offset: Int,
            count: Int,
            timestamp: Long,
        ) = notes().send(msg, offset, count, timestamp)
    }

    /** Collects SysEx messages (even split across sends) and answers each Identity Request. */
    private inner class IdentityResponder : MidiReceiver() {
        private val sysEx = ByteArrayOutputStream()
        private var collecting = false

        @Synchronized
        override fun onSend(
            msg: ByteArray,
            offset: Int,
            count: Int,
            timestamp: Long,
        ) {
            for (index in offset until offset + count) onByte(msg[index].toInt() and BYTE_MASK)
        }

        private fun onByte(byte: Int) {
            if (byte == SYSEX_START) {
                sysEx.reset()
                collecting = true
            }
            if (!collecting) return
            sysEx.write(byte)
            if (byte == SYSEX_END) {
                collecting = false
                identityRequestDeviceId(sysEx.toByteArray())?.let { deviceId ->
                    val reply = reply(deviceId)
                    notes().send(reply, 0, reply.size)
                }
            }
        }
    }
}

/** The device id of [message] if it is an Identity Request (F0 7E <id> 06 01 F7), else null. */
private fun identityRequestDeviceId(message: ByteArray): Int? {
    val bytes = message.map { it.toInt() and BYTE_MASK }
    val isRequest =
        bytes.size == REQUEST_SIZE &&
            bytes[1] == UNIVERSAL_NON_REAL_TIME &&
            bytes[SUB_ID_1_INDEX] == GENERAL_INFORMATION &&
            bytes[SUB_ID_2_INDEX] == IDENTITY_REQUEST
    return if (isRequest) bytes[DEVICE_ID_INDEX] else null
}

/** F0 7E <device id> 06 02 7D <family LSB MSB> <model LSB MSB> <version 4 bytes> F7. */
private fun reply(deviceId: Int): ByteArray =
    (
        listOf(SYSEX_START, UNIVERSAL_NON_REAL_TIME, deviceId, GENERAL_INFORMATION, IDENTITY_REPLY, NON_COMMERCIAL_ID) +
            fourteenBit(TEST_PIANO_FAMILY) +
            fourteenBit(TEST_PIANO_MODEL) +
            TEST_PIANO_VERSION +
            SYSEX_END
    ).map(Int::toByte).toByteArray()

/** A 14-bit number as two data bytes, least significant first. */
private fun fourteenBit(value: Int): List<Int> = listOf(value and DATA_MASK, value shr DATA_BITS and DATA_MASK)
