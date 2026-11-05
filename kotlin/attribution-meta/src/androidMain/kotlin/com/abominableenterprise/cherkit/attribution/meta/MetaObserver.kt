package com.abominableenterprise.cherkit.attribution.meta

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle

/** One owned observer; never takes over the app's RevenueCat customer-info listener. */
internal class MetaObserver(
    private val application: Application,
    storageContext: Context,
    apiKey: String,
    private val dispatch: (() -> Unit) -> Unit,
) : MetaObservation, Application.ActivityLifecycleCallbacks, SharedPreferences.OnSharedPreferenceChangeListener {
    // Pinned RevenueCat 9.29.1 storage contract (SharedPreferencesManager + DeviceCache).
    private val preferences = storageContext.getSharedPreferences("com_revenuecat_purchases_preferences", Context.MODE_PRIVATE)
    private val identityKey = "com.revenuecat.purchases.$apiKey.new"
    private var foreground: (() -> Unit)? = null
    private var identityChange: (() -> Unit)? = null
    private var activities = 0
    private var changingConfiguration = false

    override fun observe(onForeground: () -> Unit, onIdentityChange: () -> Unit) {
        if (foreground != null) return
        foreground = onForeground
        identityChange = onIdentityChange
        preferences.registerOnSharedPreferenceChangeListener(this)
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun stop() {
        foreground = null
        identityChange = null
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        application.unregisterActivityLifecycleCallbacks(this)
        activities = 0
        changingConfiguration = false
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (key == identityKey && !preferences.getString(identityKey, null).isNullOrBlank()) {
            dispatch { identityChange?.invoke() }
        }
    }

    override fun onActivityStarted(activity: Activity) = dispatch {
        if (foreground != null && activities++ == 0 && !changingConfiguration) foreground?.invoke()
    }
    override fun onActivityStopped(activity: Activity) = dispatch {
        if (foreground != null) {
            activities = (activities - 1).coerceAtLeast(0)
            changingConfiguration = activity.isChangingConfigurations
        }
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
