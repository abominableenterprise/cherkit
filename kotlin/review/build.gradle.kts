plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("maven-publish")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.abominableenterprise.cherkit.review"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
    }
    jvm()

    sourceSets {
        // No runtime dependencies: pure rules. Serialization is for the fixture tests only.
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
