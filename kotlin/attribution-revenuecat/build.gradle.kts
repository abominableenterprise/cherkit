plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("maven-publish")
}

kotlin {
    explicitApi()
    android {
        namespace = "com.abominableenterprise.cherkit.attribution.revenuecat"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest { isIncludeAndroidResources = true }
    }
    jvm()
    sourceSets {
        commonMain.dependencies { api(project(":core")) }
        androidMain.dependencies { compileOnly(libs.revenuecat.purchases) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
        jvmTest.dependencies { implementation(libs.kotlinx.serialization.json) }
        getByName("androidHostTest").dependencies {
            implementation(libs.revenuecat.purchases)
            implementation(libs.junit)
            implementation(libs.robolectric)
        }
    }
}
