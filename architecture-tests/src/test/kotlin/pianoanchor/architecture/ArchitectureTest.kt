package pianoanchor.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

/**
 * Hexagonal architecture and convention rules. Each rule names the doc that explains it.
 * When a review catches a new kind of mistake, add a rule here (the "ratchet rule", AGENTS.md).
 */
class ArchitectureTest {
    private val project = Konsist.scopeFromProject()
    private val production = Konsist.scopeFromProduction()
    private val tests = Konsist.scopeFromTest()

    private val domainPackages = listOf("pianoanchor.core.", "pianoanchor.engine")
    private val frameworkImports =
        listOf("android.", "androidx.", "org.jetbrains.compose.", "io.insert.koin.", "java.", "javax.")

    private fun isDomain(packageName: String?) = domainPackages.any { packageName.orEmpty().startsWith(it) }

    @Test
    fun `domain code has no framework or platform imports (docs ARCHITECTURE - Dependency rules)`() {
        production.files
            .filter { isDomain(it.packagee?.name) }
            .assertFalse(strict = true) { file ->
                file.imports.any { import -> frameworkImports.any { import.name.startsWith(it) } }
            }
    }

    @Test
    fun `domain code never depends on driving or driven adapters (docs ARCHITECTURE - Dependency rules)`() {
        val adapterPackages = listOf("pianoanchor.ui.", "pianoanchor.app", "pianoanchor.desktop", "pianoanchor.testing")
        production.files
            .filter { isDomain(it.packagee?.name) }
            .assertFalse(strict = true) { file ->
                file.imports.any { import -> adapterPackages.any { import.name.startsWith(it) } }
            }
    }

    @Test
    fun `ports are interfaces named Port with KDoc (docs ARCHITECTURE - Ports)`() {
        production
            .interfaces()
            .filter { it.resideInPackage("pianoanchor.core.ports..") }
            .assertTrue(strict = true) { it.name.endsWith("Port") && it.hasKDoc }
    }

    @Test
    fun `every port has an abstract contract test in testing (docs ARCHITECTURE - Testing)`() {
        val contractTests =
            production
                .classes()
                .filter { it.resideInPackage("pianoanchor.testing..") && it.hasAbstractModifier }
                .map { it.name }
                .toSet()
        production
            .interfaces()
            .filter { it.resideInPackage("pianoanchor.core.ports..") }
            .assertTrue(strict = true) { "${it.name}ContractTest" in contractTests }
    }

    @Test
    fun `port implementations are Adapters, or Fakes in testing (docs ARCHITECTURE - Adapters)`() {
        production
            .classes()
            .filter { cls -> cls.parents().any { it.name.endsWith("Port") } }
            .assertTrue(strict = false) { cls ->
                if (cls.resideInPackage("pianoanchor.testing..")) {
                    cls.name.startsWith("Fake")
                } else {
                    cls.name.endsWith("Adapter")
                }
            }
    }

    @Test
    fun `every port implementation runs its port contract test (docs ARCHITECTURE - Testing)`() {
        val contractSubclasses =
            tests
                .classes()
                .filter { cls -> cls.parents().any { it.name.endsWith("PortContractTest") } }
                .map { it.name.removeSuffix("ContractTest") }
                .toSet()
        production
            .classes()
            .filter { cls -> cls.parents().any { it.name.endsWith("Port") } && !cls.hasAbstractModifier }
            .assertTrue(strict = false) { it.name in contractSubclasses }
    }

    @Test
    fun `every engine tunable documents why and source (docs algorithms README)`() {
        production
            .objects()
            .filter { it.name == "Tunables" }
            .flatMap { it.properties() }
            .assertTrue(strict = true) { property ->
                val text = property.kDoc?.text.orEmpty()
                text.contains("Why:") && text.contains("Source:")
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

    @Test
    fun `no wildcard imports anywhere`() {
        project.imports.assertFalse(strict = true) { it.isWildcard }
    }
}
