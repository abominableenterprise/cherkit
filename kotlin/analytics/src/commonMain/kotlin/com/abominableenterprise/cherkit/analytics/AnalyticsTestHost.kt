package com.abominableenterprise.cherkit.analytics

/**
 * Detects a process running JUnit or AndroidX tests, so apps can keep analytics off there:
 * `Analytics(backend, buildEnabled = !BuildConfig.DEBUG && !AnalyticsTestHost.isRunningTests)`.
 */
public object AnalyticsTestHost {
    private val testClasses = listOf(
        "org.junit.Test",
        "org.junit.jupiter.api.Test",
        "androidx.test.platform.app.InstrumentationRegistry",
    )

    /** True when a test framework class is on the classpath; release apps do not ship these. */
    public val isRunningTests: Boolean by lazy { isRunningTests(::classExists) }

    internal fun isRunningTests(classExists: (String) -> Boolean): Boolean = testClasses.any(classExists)
}

/** Whether a class with this fully-qualified name can be loaded. */
internal expect fun classExists(name: String): Boolean
