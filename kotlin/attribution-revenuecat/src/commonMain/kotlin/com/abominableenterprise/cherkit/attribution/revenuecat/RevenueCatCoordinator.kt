package com.abominableenterprise.cherkit.attribution.revenuecat

/** Vendor-free boundary; intentionally exposes neither Apple nor device-identifier collection. */
internal interface RevenueCatSdk<Configuration> {
    val isConfigured: Boolean
    fun configure(configuration: Configuration)
}

/** Caller serializes access. Build options lazily so repeated starts have no configuration side effects. */
internal class RevenueCatCoordinator<Configuration>(private val sdk: RevenueCatSdk<Configuration>) {
    private var started = false

    fun start(configuration: () -> Configuration) {
        if (started) return
        if (!sdk.isConfigured) sdk.configure(configuration())
        started = true
    }
}
