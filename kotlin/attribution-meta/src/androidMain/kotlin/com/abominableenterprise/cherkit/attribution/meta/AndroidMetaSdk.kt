package com.abominableenterprise.cherkit.attribution.meta

import android.app.Application
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger

internal class AndroidMetaSdk(private val application: Application) : MetaSdk {
    override fun disableAutomaticFeatures() {
        FacebookSdk.setAutoInitEnabled(false)
        FacebookSdk.setAutoLogAppEventsEnabled(false)
        FacebookSdk.setAdvertiserIDCollectionEnabled(false)
        FacebookSdk.setAutoLogMetaDataEnabled(false)
        FacebookSdk.setAutoLogProductImprovementEnabled(false)
    }

    @Suppress("DEPRECATION")
    override fun initialize() {
        // With the startup provider removed, fullyInitialize alone does not load SDK context/config.
        FacebookSdk.sdkInitialize(application)
        FacebookSdk.fullyInitialize()
    }

    override fun anonymousId(): String = AppEventsLogger.getAnonymousAppDeviceGUID(application)
    override fun activateApp() { AppEventsLogger.activateApp(application) }
}
