import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("maven-publish")
}

// Compose UI for Sunset, Android only. commonMain holds the pure "Not now" rule and the strings, so the shared
// fixture runs on the JVM too (the jvm artifact carries only those); androidMain holds the composables.
// The Compose compiler runs only for Android: the jvm target has no Compose runtime.
composeCompiler {
    targetKotlinPlatforms.set(setOf(KotlinPlatformType.androidJvm))
}

kotlin {
    explicitApi()

    android {
        namespace = "com.abominableenterprise.cherkit.sunset.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // Robolectric renders the composables in host tests, which needs the merged resources and manifest.
        withHostTest { isIncludeAndroidResources = true }
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":sunset"))
        }
        androidMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            // @Preview annotations only (tiny); a runtime dependency so R8 in apps never sees a missing class.
            implementation(libs.compose.ui.tooling.preview)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
            // Espresso.pressBack(); the same version compose ui-test already brings at runtime.
            implementation(libs.androidx.test.espresso.core)
        }
    }
}

