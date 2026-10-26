package com.abominableenterprise.cherkit.analytics

import android.content.Context
import com.posthog.PostHogInterface
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

/** Creates a PostHog client from a config. The seam lets host tests use a fake client and never touch the network. */
internal fun interface PostHogClientFactory {
    fun create(config: PostHogAndroidConfig): PostHogInterface
}

/** The real factory: a new SDK instance (not the global singleton) bound to the application context. */
internal fun sdkClientFactory(context: Context): PostHogClientFactory {
    val appContext = context.applicationContext ?: context
    return PostHogClientFactory { config -> PostHogAndroid.with(appContext, config) }
}
