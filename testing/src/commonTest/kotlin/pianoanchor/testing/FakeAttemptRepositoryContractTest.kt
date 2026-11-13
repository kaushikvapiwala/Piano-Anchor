package pianoanchor.testing

import pianoanchor.core.ports.AttemptRepositoryPort

class FakeAttemptRepositoryContractTest : AttemptRepositoryPortContractTest() {
    override fun createSubject(): AttemptRepositoryPort = FakeAttemptRepository()
}
