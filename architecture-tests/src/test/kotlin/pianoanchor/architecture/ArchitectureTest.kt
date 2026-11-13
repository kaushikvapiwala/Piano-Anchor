package pianoanchor.architecture

import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.provider.KoParentProvider
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pianoanchor.architecture.Sources.TESTING
import pianoanchor.architecture.Sources.ports
import pianoanchor.architecture.Sources.production
import pianoanchor.architecture.Sources.tests
import pianoanchor.architecture.Sources.tunables
import pianoanchor.architecture.Sources.under
import pianoanchor.architecture.Sources.underAny

/**
 * Hexagonal architecture and convention rules. Each rule names the doc that explains it.
 * When a review catches a new kind of mistake, add a rule here (the "ratchet rule", AGENTS.md).
 */
class ArchitectureTest {
    private data class PortImplementation(
        val name: String,
        val packageName: String?,
        val ports: Set<String>,
    )

    private companion object {
        val domainRoots = listOf("pianoanchor.core", "pianoanchor.engine")
        val adapterRoots =
            listOf(
                "pianoanchor.ui",
                "pianoanchor.app",
                "pianoanchor.desktop",
                "pianoanchor.platform",
                "pianoanchor.testing",
            )
        val domainImportRoots = domainRoots + listOf("kotlin", "kotlinx.coroutines")
        val frameworkRoots = listOf("android", "androidx", "org.jetbrains.compose", "org.koin", "java", "javax")
        val portNames = ports.map { it.name }.toSet()

        /** True when the declaration implements a project port, directly or via base classes/sub-interfaces. */
        fun KoParentProvider.implementsPort() = parentNames().any { it in portNames }

        /** Project ports the declaration implements, directly or indirectly. */
        fun KoParentProvider.implementedPorts() = parentNames().filter { it in portNames }.toSet()

        /** Simple names of all parents, also when written fully qualified (`pianoanchor.core.ports.ClockPort`). */
        fun KoParentProvider.parentNames() = parents(indirectParents = true).map { it.name.substringAfterLast('.') }

        /** Concrete classes and objects that implement a project port. */
        val portImplementations =
            production
                .classes()
                .filter { !it.hasAbstractModifier && it.implementsPort() }
                .map { PortImplementation(it.name, it.packagee?.name, it.implementedPorts()) } +
                production
                    .objects()
                    .filter { it.implementsPort() }
                    .map { PortImplementation(it.name, it.packagee?.name, it.implementedPorts()) }

        fun assertNone(
            violations: List<Any>,
            rule: String,
        ) = assertTrue(violations.isEmpty()) { "$rule violated by: $violations" }
    }

    @Test
    fun `domain code has no framework or platform imports (docs ARCHITECTURE - Dependency rules)`() {
        production.files
            .filter { underAny(it.packagee?.name, domainRoots) }
            .assertFalse(strict = true) { file -> file.imports.any { underAny(it.name, frameworkRoots) } }
    }

    @Test
    fun `domain code imports only Kotlin, kotlinx coroutines and domain code (docs ARCHITECTURE - Dependency rules)`() {
        production.files
            .filter { underAny(it.packagee?.name, domainRoots) }
            .assertTrue(strict = true) { file -> file.imports.all { underAny(it.name, domainImportRoots) } }
    }

    @Test
    fun `domain code never depends on driving or driven adapters (docs ARCHITECTURE - Dependency rules)`() {
        production.files
            .filter { underAny(it.packagee?.name, domainRoots) }
            .assertFalse(strict = true) { file -> file.imports.any { underAny(it.name, adapterRoots) } }
    }

    @Test
    fun `ports are top-level interfaces named Port with KDoc (docs ARCHITECTURE - Ports)`() {
        ports.assertTrue(strict = true) { it.name.endsWith("Port") && it.hasKDoc }
    }

    @Test
    fun `every port has an abstract contract test in testing (docs ARCHITECTURE - Testing)`() {
        val contractTests = Sources.contractTests.map { it.name }.toSet()
        ports.assertTrue(strict = true) { "${it.name}ContractTest" in contractTests }
    }

    @Test
    fun `port implementations are Adapters, or Fakes in testing (docs ARCHITECTURE - Adapters)`() {
        assertNone(
            portImplementations.filterNot {
                if (under(it.packageName, TESTING)) it.name.startsWith("Fake") else it.name.endsWith("Adapter")
            },
            "Port implementations must be named *Adapter (or Fake* in $TESTING)",
        )
    }

    @Test
    fun `every port implementation runs its port contract test (docs ARCHITECTURE - Testing)`() {
        // Each port an implementation provides needs a test class that extends <Port>ContractTest and whose name
        // starts with the implementation's name, e.g. FakeClockContractTest, FakePianoInputContractTest.
        val contractRuns =
            tests
                .classes()
                .flatMap { cls -> cls.parentNames().filter { it.endsWith("PortContractTest") }.map { cls.name to it } }
        assertNone(
            portImplementations.flatMap { impl ->
                impl.ports
                    .filterNot { port ->
                        contractRuns.any { (test, contract) ->
                            contract == "${port}ContractTest" && test.startsWith(impl.name) && test.endsWith("Test")
                        }
                    }.map { "${impl.name} -> ${it}ContractTest" }
            },
            "Every port implementation needs a test class extending its <Port>ContractTest",
        )
    }

    @Test
    fun `every engine tunable documents why once, then source once (docs algorithms README)`() {
        tunables.assertTrue(strict = true) { property ->
            val text = property.kDoc?.text.orEmpty()
            val why = text.indexOf("Why:")
            val source = text.indexOf("Source:")
            why >= 0 && source > why && why == text.lastIndexOf("Why:") && source == text.lastIndexOf("Source:")
        }
    }

    @Test
    fun `every ViewModel and UseCase has a test class (docs ARCHITECTURE - Testing)`() {
        val testClassNames = tests.classes().map { it.name }.toSet()
        production
            .classes()
            .withNameEndingWith("ViewModel", "UseCase")
            .assertTrue(strict = false) { "${it.name}Test" in testClassNames }
    }

    @Test
    fun `test classes are named Test (docs ARCHITECTURE - Testing)`() {
        tests
            .classes()
            .filter { cls -> cls.functions().any { fn -> fn.annotations.any { it.name == "Test" } } }
            .assertTrue(strict = true) { it.name.endsWith("Test") }
    }
}
