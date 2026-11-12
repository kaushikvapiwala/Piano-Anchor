package pianoanchor.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration

/** The parsed source scopes and the selections (ports, tunables, contract tests) shared by the architecture tests. */
internal object Sources {
    const val PORTS = "pianoanchor.core.ports"
    const val TESTING = "pianoanchor.testing"

    // Parsed once for all tests (JUnit creates a new instance per test method).
    val production = Konsist.scopeFromProduction()
    val tests = Konsist.scopeFromTest()

    /** True when [name] is [root] itself or lives below it (so `engine` doesn't match `engineering`). */
    fun under(
        name: String?,
        root: String,
    ) = name != null && (name == root || name.startsWith("$root."))

    fun underAny(
        name: String?,
        roots: List<String>,
    ) = roots.any { under(name, it) }

    val ports = production.interfaces(includeNested = false).filter { under(it.packagee?.name, PORTS) }

    /** Properties of the `Tunables` object. */
    val tunables: List<KoPropertyDeclaration> =
        production
            .objects()
            .filter { it.name == "Tunables" }
            .flatMap { it.properties() }

    /** Abstract classes in `pianoanchor.testing`; the contract tests live in :testing's main source set. */
    val contractTests: List<KoClassDeclaration> =
        (production.classes() + tests.classes())
            .filter { under(it.packagee?.name, TESTING) && it.hasAbstractModifier }

    fun contractTestFor(port: String) = contractTests.firstOrNull { it.name == "${port}ContractTest" }
}
