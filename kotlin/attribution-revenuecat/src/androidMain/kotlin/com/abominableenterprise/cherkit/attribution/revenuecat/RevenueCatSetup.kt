package com.abominableenterprise.cherkit.attribution.revenuecat

import android.app.Application
import com.revenuecat.purchases.PurchasesConfiguration

/**
 * Native RevenueCat options, applied before configuration. The builder receiver preserves every
 * native option except automatic device-identifier collection, which CherKit always disables.
 * Keep [configure] side-effect free; it is not called if RevenueCat is already configured.
 */
public class RevenueCatSetup(
    public val apiKey: String,
    public val appUserId: String? = null,
    public val configure: PurchasesConfiguration.Builder.() -> Unit = {},
) {
    internal fun configuration(application: Application): PurchasesConfiguration =
        PurchasesConfiguration.Builder(application, apiKey)
            .appUserID(appUserId)
            .apply(configure)
            .automaticDeviceIdentifierCollectionEnabled(false)
            .build()
}
