import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("maven-publish")
}

// The Android adapter for :review: Play In-App Review plus Compose effects. A module of its own, like sunset-compose
// next to sunset, so :review keeps only the Core contract and apps without Play Services can use the rules alone.
// commonMain holds the pure launch order (request, then markPrompted, then launch) and the settle loop, so they
// run on the JVM too (the jvm artifact carries only those); androidMain holds Play and the composables.
// The Compose compiler runs only for Android: the jvm target has no Compose runtime.
composeCompiler {
    targetKotlinPlatforms.set(setOf(KotlinPlatformType.androidJvm))
}

kotlin {
    explicitApi()

    android {
        namespace = "com.abominableenterprise.cherkit.review.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest { isIncludeAndroidResources = true }
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":review"))
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            // ReviewManager is named by the public PlayReview.launch factory.
            api(libs.play.review.ktx)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}

// Regression for #83: consumers must be able to name ReviewManager/FakeReviewManager without another dependency.
val checkPlayApiPublication = tasks.register("checkPlayApiPublication") {
    group = "verification"
    description = "Verify the Android POM exports Play Review at compile scope"
    dependsOn("generatePomFileForAndroidPublication")
    doLast {
        val pom = tasks.named<GenerateMavenPom>("generatePomFileForAndroidPublication").get()
        val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(pom.destination)
        val dependencies = xml.getElementsByTagName("dependency")
        val play = (0 until dependencies.length).map { dependencies.item(it) as org.w3c.dom.Element }
            .single {
                it.getElementsByTagName("groupId").item(0).textContent == "com.google.android.play" &&
                    it.getElementsByTagName("artifactId").item(0).textContent == "review-ktx"
            }
        check(play.getElementsByTagName("scope").item(0)?.textContent == "compile") {
            "Play Review is public API and must have Maven compile scope"
        }
    }
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(checkPlayApiPublication) }
