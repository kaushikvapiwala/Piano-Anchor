package pianoanchor.testing

import pianoanchor.core.ports.SettingsRepositoryPort

class FakeSettingsRepositoryContractTest : SettingsRepositoryPortContractTest() {
    override fun createSubject(): SettingsRepositoryPort = FakeSettingsRepository()
}
