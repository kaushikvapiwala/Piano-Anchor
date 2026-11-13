package pianoanchor.testing

import pianoanchor.core.ports.InstrumentRepositoryPort

class FakeInstrumentRepositoryContractTest : InstrumentRepositoryPortContractTest() {
    override fun createSubject(): InstrumentRepositoryPort = FakeInstrumentRepository()
}
