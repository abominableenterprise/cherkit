// Plugins are declared once here (not applied) so every kotlin/<module> uses the same versions.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
}

// Shared cross-platform test contract: every module's JVM-based tests read ../fixtures through this property.
val fixturesDir = layout.projectDirectory.dir("fixtures").asFile.absolutePath
subprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("cherkit.fixtures", fixturesDir)
        inputs.dir(fixturesDir).withPropertyName("cherkitFixtures").withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
