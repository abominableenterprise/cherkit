package com.abominableenterprise.cherkit.attribution.revenuecat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RevenueCatCoordinatorTest {
    private class RecordingSdk(var configured: Boolean = false) : RevenueCatSdk<Any> {
        val calls = mutableListOf<String>()
        var received: Any? = null
        var fail = false
        override val isConfigured: Boolean get() {
            calls += "isConfigured"
            return configured
        }
        override fun configure(configuration: Any) {
            calls += "configure"
            if (fail) error("configuration failed")
            received = configuration
            configured = true
        }
    }

    @Test fun checksThenBuildsThenConfiguresExactlyOnceWithTheSameConfiguration() {
        val sdk = RecordingSdk()
        val coordinator = RevenueCatCoordinator(sdk)
        val options = Any()
        repeat(3) {
            coordinator.start {
                sdk.calls += "build"
                options
            }
        }
        assertEquals(listOf("isConfigured", "build", "configure"), sdk.calls)
        assertSame(options, sdk.received)
    }

    @Test fun preconfiguredSdkIsUntouchedAndBuilderIsNeverCalled() {
        val sdk = RecordingSdk(configured = true)
        val coordinator = RevenueCatCoordinator(sdk)
        repeat(3) { coordinator.start { error("must not build") } }
        assertEquals(listOf("isConfigured"), sdk.calls)
    }

    @Test fun sdkConfiguredAfterCoordinatorCreationIsAlsoRespected() {
        val sdk = RecordingSdk()
        val coordinator = RevenueCatCoordinator(sdk)
        sdk.configured = true
        coordinator.start { error("must not build") }
        assertEquals(listOf("isConfigured"), sdk.calls)
    }

    @Test fun failedConfigureDoesNotMarkStartupComplete() {
        val sdk = RecordingSdk().apply { fail = true }
        val coordinator = RevenueCatCoordinator(sdk)
        assertFailsWith<IllegalStateException> { coordinator.start { Any() } }
        sdk.fail = false
        coordinator.start { Any() }
        assertEquals(listOf("isConfigured", "configure", "isConfigured", "configure"), sdk.calls)
    }
}
