package com.abominableenterprise.cherkit.attribution.revenuecat

import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

internal object AndroidRevenueCatSdk : RevenueCatSdk<PurchasesConfiguration> {
    override val isConfigured: Boolean get() = Purchases.isConfigured

    override fun configure(configuration: PurchasesConfiguration) {
        Purchases.configure(configuration)
    }
}
