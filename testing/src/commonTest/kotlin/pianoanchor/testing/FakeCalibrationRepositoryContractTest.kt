package pianoanchor.testing

import pianoanchor.core.ports.CalibrationRepositoryPort

class FakeCalibrationRepositoryContractTest : CalibrationRepositoryPortContractTest() {
    override fun createSubject(): CalibrationRepositoryPort = FakeCalibrationRepository()
}
