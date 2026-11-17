import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("maven-publish")
}

// The Android "update available" adapter: Play In-App Updates (FLEXIBLE) plus a Compose effect. A module of its own,
// like review-compose, so :sunset and :sunset-compose stay free of Play Services. commonMain holds the pure launch
// order, the per-versionCode "Not now" rule and the once-per-session gate, so they run on the JVM too (the jvm
// artifact carries only those); androidMain holds Play and the composable.
// The Compose compiler runs only for Android: the jvm target has no Compose runtime.
composeCompiler {
    targetKotlinPlatforms.set(setOf(KotlinPlatformType.androidJvm))
}

kotlin {
    explicitApi()

    android {
        namespace = "com.abominableenterprise.cherkit.update.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest { isIncludeAndroidResources = true }
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.androidx.activity.compose)
            // AppUpdateManager is named by the public rememberPlayUpdateState factory.
            api(libs.play.app.update.ktx)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}

// Regression for #83: Maven consumers must be able to name AppUpdateManager/FakeAppUpdateManager
// without adding a second Play dependency. Check the published scope, not just this module's test classpath.
val checkPlayApiPublication = tasks.register("checkPlayApiPublication") {
    group = "verification"
    description = "Verify the Android POM exports Play In-App Updates at compile scope"
    dependsOn("generatePomFileForAndroidPublication")
    doLast {
        val pom = tasks.named<GenerateMavenPom>("generatePomFileForAndroidPublication").get()
        val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(pom.destination)
        val dependencies = xml.getElementsByTagName("dependency")
        val play = (0 until dependencies.length).map { dependencies.item(it) as org.w3c.dom.Element }
            .single {
                it.getElementsByTagName("groupId").item(0).textContent == "com.google.android.play" &&
                    it.getElementsByTagName("artifactId").item(0).textContent == "app-update-ktx"
            }
        check(play.getElementsByTagName("scope").item(0)?.textContent == "compile") {
            "Play In-App Updates is public API and must have Maven compile scope"
        }
    }
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(checkPlayApiPublication) }
