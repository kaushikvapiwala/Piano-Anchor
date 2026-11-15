package pianoanchor.testing

import pianoanchor.core.ports.LearnerRepositoryPort

class FakeLearnerRepositoryContractTest : LearnerRepositoryPortContractTest() {
    override fun createSubject(): LearnerRepositoryPort = FakeLearnerRepository()
}
