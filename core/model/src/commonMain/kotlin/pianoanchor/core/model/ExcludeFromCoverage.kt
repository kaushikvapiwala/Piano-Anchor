package pianoanchor.core.model

/**
 * Excludes a declaration from the 100% coverage gate (Kover). Use only for code that cannot run in a
 * JVM unit test; [reason] must say why and where it is tested instead (e.g. a device test).
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
public annotation class ExcludeFromCoverage(
    val reason: String,
)
