import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("maven-publish")
}

// The Android adapter for :review: Play In-App Review plus Compose effects. A module of its own, like sunset-compose
// next to sunset, so :review keeps no runtime dependencies and apps without Play Services can use the rules alone.
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
            implementation(libs.play.review.ktx)
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
