import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.gradle.tasks.BundleAar
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

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
    // Keep the build JDK (21) independent of the consumer bytecode contract (17).
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinJvmTarget>().configureEach {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
                compilations.configureEach {
                    compileJavaTaskProvider?.configure { options.release.set(17) }
                }
            }
            targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
        }
    }
    // Android javac uses the SDK bootclasspath, so use source/target instead of --release there.
    // JVM compilations additionally use --release above to constrain Java API usage.
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    tasks.withType<Test>().configureEach {
        systemProperty("cherkit.fixtures", fixturesDir)
        inputs.dir(fixturesDir).withPropertyName("cherkitFixtures").withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

// Publishing (JitPack): a module opts in with id("maven-publish"); KMP then publishes the root publication plus its
// android and jvm variants. JitPack's multi-module convention is group com.github.<owner>.<repo>, artifact = module
// name. The version is the git tag JitPack builds (VERSION, e.g. v0.1.0), else `version` from gradle.properties.
// Coordinates are set on every project before any module build script runs, so no publication, project dependency
// or Gradle module metadata file can see the defaults (the root project name `cherkit`, an empty group).
val publishGroup = "com.github.abominableenterprise.cherkit"
val publishVersion = providers.environmentVariable("VERSION").orElse(providers.gradleProperty("version")).get()
allprojects {
    group = publishGroup
    version = publishVersion
}

// Every generated .module must name its own coordinates: component <group>:<module>, `available-at` pointing at
// <module>-<platform>, and no explicit capability (Gradle derives each from the coordinates). The publish fails
// otherwise. JitPack rewrites the component block of the files it serves (see the README's Install section); Gradle
// reads only its attributes, but the files JitPack starts from are kept correct here.
subprojects {
    val g = publishGroup
    val m = name
    val v = publishVersion
    plugins.withId("maven-publish") {
        val bytecodeScript = rootProject.layout.projectDirectory.file("scripts/check-bytecode.sh")
        // These are the archive producers used by the Android and JVM Maven publications.
        // A live task collection also picks up targets registered later in module build scripts.
        val archives = objects.fileCollection().from(tasks.withType<AbstractArchiveTask>().matching {
            (it is BundleAar && it.name == "bundleAndroidMainAar") || it.name == "jvmJar"
        })
        val checkBytecode = tasks.register<Exec>("checkBytecode") {
            group = "verification"
            description = "Verify published Android and JVM archives use Java 17 bytecode or lower"
            inputs.file(bytecodeScript)
            inputs.files(archives)
            commandLine("bash", bytecodeScript.asFile.absolutePath)
            argumentProviders.add(CommandLineArgumentProvider { archives.files.map { it.absolutePath }.sorted() })
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(checkBytecode) }
        tasks.withType<GenerateModuleMetadata>().configureEach {
            val file = outputFile
            doLast {
                val metadataFile = file.get().asFile
                val problems = mutableListOf<String>()
                fun expect(what: String, actual: Any?, expected: Any?) {
                    if (actual != expected) problems += "$what is $actual, expected $expected"
                }

                @Suppress("UNCHECKED_CAST")
                val json = groovy.json.JsonSlurper().parse(metadataFile) as Map<String, Any?>
                val component = json["component"] as Map<*, *>
                expect("component", "${component["group"]}:${component["module"]}:${component["version"]}", "$g:$m:$v")
                component["url"]?.let { expect("component url", it, "../../$m/$v/$m-$v.module") }
                for (variant in json["variants"] as List<*>) {
                    variant as Map<*, *>
                    val at = variant["available-at"] as Map<*, *>?
                    if (at != null) {
                        val target = at["module"] as String
                        expect("${variant["name"]} available-at group", at["group"], g)
                        if (!target.startsWith("$m-")) problems += "${variant["name"]} is available at $target"
                        expect("${variant["name"]} available-at url", at["url"], "../../$target/$v/$target-$v.module")
                    }
                    for (capability in variant["capabilities"] as List<*>? ?: emptyList<Any>()) {
                        capability as Map<*, *>
                        expect("${variant["name"]} capability", "${capability["group"]}:${capability["name"]}", "$g:$m")
                    }
                }
                check(problems.isEmpty()) { "$metadataFile names the wrong coordinates:\n" + problems.joinToString("\n") }
            }
        }
    }
}
