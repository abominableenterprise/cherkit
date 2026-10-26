package com.abominableenterprise.cherkit.analytics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsTestHostTest {
    @Test
    fun detectsThisTestRun() {
        assertTrue(AnalyticsTestHost.isRunningTests)
    }

    @Test
    fun detectsAnyKnownTestFramework() {
        assertTrue(AnalyticsTestHost.isRunningTests { it == "androidx.test.platform.app.InstrumentationRegistry" })
    }

    @Test
    fun releaseClasspathIsNotATest() {
        assertFalse(AnalyticsTestHost.isRunningTests { false })
    }
}
