package com.abominableenterprise.cherkit.attribution.meta

import android.app.Application
import com.abominableenterprise.cherkit.attribution.revenuecat.Audience
import com.abominableenterprise.cherkit.attribution.revenuecat.RevenueCatAttribution
import com.abominableenterprise.cherkit.attribution.revenuecat.RevenueCatSetup
import com.revenuecat.purchases.Purchases

/** Optional Meta activation and anonymous-ID bridge; RevenueCat is the sole purchase sender. */
public object MetaRevenueCatAttribution {
    private val lock = Any()
    private var startup: MetaStartup? = null

    /**
     * Replace Purchases.configure at Application startup, before purchase UI. Native RevenueCat
     * options and once-only behavior are preserved. Missing/unsafe manifest settings, restricted
     * audiences, debug/test hosts and emulators enable purchases only. See the module README.
     */
    public fun start(application: Application, revenueCat: RevenueCatSetup, audience: Audience) {
        synchronized(lock) {
            // Use the existing adapter, including its enforced device-identifier collection override.
            RevenueCatAttribution.start(application, revenueCat, audience)
            val configuration = Purchases.sharedInstance.currentConfiguration
            val allowed = runCatching {
                !configuration.automaticDeviceIdentifierCollectionEnabled &&
                    MetaLaunchPolicy.allowed(application, audience)
            }.getOrDefault(false)
            if (!allowed) {
                startup?.stop()
                return
            }
            val coordinator = startup ?: MetaStartup(
                AndroidMetaSdk(application),
                MetaObserver(application, configuration.context, configuration.apiKey) { callback ->
                    synchronized(lock) { callback() }
                },
                { id -> Purchases.sharedInstance.setFBAnonymousID(id) },
            ).also { startup = it }
            coordinator.start(allowed = true) { /* RevenueCat adapter already ran above. */ }
        }
    }

    /** Stops our observer and future activations, not vendor queues, stored IDs or server events. */
    public fun stop() {
        synchronized(lock) { startup?.stop() }
    }
}
