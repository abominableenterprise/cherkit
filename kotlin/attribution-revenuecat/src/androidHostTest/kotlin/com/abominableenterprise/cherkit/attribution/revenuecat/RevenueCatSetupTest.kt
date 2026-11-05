package com.abominableenterprise.cherkit.attribution.revenuecat

import com.revenuecat.purchases.DangerousSettings
import com.revenuecat.purchases.EntitlementVerificationMode
import com.revenuecat.purchases.PurchasesAreCompletedBy
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.Store
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class RevenueCatSetupTest {
    private class RecordingSdk : RevenueCatSdk<PurchasesConfiguration> {
        val calls = mutableListOf<String>()
        var received: PurchasesConfiguration? = null
        override val isConfigured: Boolean get() {
            calls += "isConfigured"
            return received != null
        }
        override fun configure(configuration: PurchasesConfiguration) {
            calls += "configure"
            received = configuration
        }
    }

    @Test fun nativeOptionsPassThroughOnceAndPrivacyOverrideIsAppliedLast() {
        val application = RuntimeEnvironment.getApplication()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val dangerous = DangerousSettings()
            val sdk = RecordingSdk()
            val coordinator = RevenueCatCoordinator(sdk)
            var built: PurchasesConfiguration? = null
            val setup = RevenueCatSetup("public_key", "existing-user") {
                sdk.calls += "options"
                purchasesAreCompletedBy(PurchasesAreCompletedBy.MY_APP)
                entitlementVerificationMode(EntitlementVerificationMode.INFORMATIONAL)
                store(Store.AMAZON)
                service(executor)
                dangerousSettings(dangerous)
                diagnosticsEnabled(true)
                showInAppMessagesAutomatically(false)
                pendingTransactionsForPrepaidPlansEnabled(true)
                preferredUILocaleOverride("fr_FR")
                // Even a legacy app option cannot turn automatic ID collection back on.
                automaticDeviceIdentifierCollectionEnabled(true)
            }
            repeat(3) {
                coordinator.start { setup.configuration(application).also { built = it } }
            }
            assertEquals(listOf("isConfigured", "options", "configure"), sdk.calls)
            val config = requireNotNull(sdk.received)
            assertSame(built, config)
            assertSame(application, config.context)
            assertEquals("public_key", config.apiKey)
            assertEquals("existing-user", config.appUserID)
            assertEquals(PurchasesAreCompletedBy.MY_APP, config.purchasesAreCompletedBy)
            assertEquals(EntitlementVerificationMode.INFORMATIONAL, config.verificationMode)
            assertEquals(Store.AMAZON, config.store)
            assertSame(executor, config.service)
            assertSame(dangerous, config.dangerousSettings)
            assertTrue(config.diagnosticsEnabled)
            assertFalse(config.showInAppMessagesAutomatically)
            assertTrue(config.pendingTransactionsForPrepaidPlansEnabled)
            assertEquals("fr_FR", config.preferredUILocaleOverride)
            assertFalse(config.automaticDeviceIdentifierCollectionEnabled)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun defaultsAllowAnonymousPurchasesButDisableAutomaticIdentifiers() {
        val config = RevenueCatSetup("public_key").configuration(RuntimeEnvironment.getApplication())
        assertEquals(null, config.appUserID)
        assertFalse(config.automaticDeviceIdentifierCollectionEnabled)
    }

    @Test fun nativeBuilderCanOverrideSetupUserIdWithoutLosingOptions() {
        val config = RevenueCatSetup("public_key", "initial-user") {
            appUserID("builder-user")
        }.configuration(RuntimeEnvironment.getApplication())
        assertEquals("builder-user", config.appUserID)
    }
}
