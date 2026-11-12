package pianoanchor

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** The `libs` version catalog for use inside precompiled convention plugins. */
val Project.catalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** An integer version from the catalog, e.g. `catalog.int("android-minSdk")`. */
fun VersionCatalog.int(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()
