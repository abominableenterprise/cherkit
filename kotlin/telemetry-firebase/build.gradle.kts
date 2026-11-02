plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("maven-publish")
}

kotlin {
    explicitApi()
    android {
        namespace = "com.abominableenterprise.cherkit.telemetry.firebase"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
    }
    jvm()
    sourceSets {
        commonMain.dependencies { api(project(":telemetry")) }
        androidMain.dependencies {
            compileOnly(project.dependencies.platform(libs.firebase.bom))
            compileOnly(libs.firebase.crashlytics)
            compileOnly(libs.firebase.perf)
        }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
