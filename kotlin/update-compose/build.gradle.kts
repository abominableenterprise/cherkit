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
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.androidx.activity.compose)
            implementation(libs.play.app.update.ktx)
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
