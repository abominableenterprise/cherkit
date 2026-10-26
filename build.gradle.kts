// Plugins are declared once here (not applied) so every kotlin/<module> uses the same versions.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
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

// Publishing (JitPack): a module opts in with id("maven-publish"); KMP then publishes the root publication plus its
// android and jvm variants. JitPack's multi-module convention is group com.github.<owner>.<repo>, artifact = module
// name. The version is the git tag JitPack builds (VERSION, e.g. v0.1.0), else `version` from gradle.properties.
val publishGroup = "com.github.abominableenterprise.cherkit"
val publishVersion = providers.environmentVariable("VERSION").orElse(providers.gradleProperty("version")).get()
subprojects {
    plugins.withId("maven-publish") {
        group = publishGroup
        version = publishVersion
    }
}
