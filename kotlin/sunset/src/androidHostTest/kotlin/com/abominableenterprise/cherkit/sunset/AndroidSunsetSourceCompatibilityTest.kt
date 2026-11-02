package com.abominableenterprise.cherkit.sunset

import android.content.Context
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidSunsetSourceCompatibilityTest {
    @Test
    fun originalPositionalAndCurrentNamedCallbacksCompile() {
        // Compile the Android wiring without executing android.jar stubs.
        val calls: List<(Context, SunsetFetch) -> SunsetChecker> = listOf(
            { context, fetch -> AndroidSunset.checker(context, SunsetChecker.DEFAULT_TIMEOUT, fetch) },
            { context, fetch -> AndroidSunset.checker(context, SunsetChecker.DEFAULT_TIMEOUT) { fetch.fetch() } },
            { context, fetch -> AndroidSunset.checker(context) { fetch.fetch() } },
            { context, fetch -> AndroidSunset.checker(context, fetch = fetch) },
            { context, fetch -> AndroidSunset.checker(context, telemetrySink = null, fetch = fetch) },
        )
        assertEquals(5, calls.size)
    }
}
