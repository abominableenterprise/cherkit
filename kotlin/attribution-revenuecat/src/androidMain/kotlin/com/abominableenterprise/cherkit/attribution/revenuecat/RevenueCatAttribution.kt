package com.abominableenterprise.cherkit.attribution.revenuecat

import android.app.Application

/** Drop-in RevenueCat configuration. Android has no Apple attribution route. */
public object RevenueCatAttribution {
    private val lock = Any()
    private val coordinator = RevenueCatCoordinator(AndroidRevenueCatSdk)

    /**
     * Call at Application startup instead of Purchases.configure, before purchase UI.
     * First configuration wins; repeated or concurrent starts do not replace the SDK instance.
     * An already configured SDK is left untouched, including its existing privacy settings.
     * [audience] has API parity with Apple; both values enable purchases only on Android.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun start(application: Application, revenueCat: RevenueCatSetup, audience: Audience) {
        synchronized(lock) {
            coordinator.start { revenueCat.configuration(application) }
        }
    }
}
