package com.abominableenterprise.cherkit.analytics

import android.content.Context
import com.posthog.PostHogInterface

/**
 * The Android [AnalyticsBackend] backed by PostHog, with a hardened config (no autocapture, replay,
 * surveys, feature flags, person profiles or error autocapture). Only event names are sent: never properties,
 * `identify`, person properties, groups or aliases.
 *
 * The client is created on the first [start] after construction or after a [stop]; [stop] opts out, closes it
 * and drops it, so the next [start] creates a fresh one. Nothing touches the SDK or the network before [start].
 *
 * @param context any context; only its application context is kept.
 * @param apiKey the PostHog project key: a public client key the app supplies. A blank key makes [start] throw
 *   [IllegalArgumentException], which [Analytics] swallows (it rolls back and stays off).
 * @param host the PostHog ingestion host.
 */
public class PostHogAnalyticsBackend internal constructor(
    private val apiKey: String,
    private val host: String,
    private val clientFactory: PostHogClientFactory,
) : AnalyticsBackend {
    public constructor(context: Context, apiKey: String, host: String = DEFAULT_HOST) :
        this(apiKey, host, sdkClientFactory(context))

    private var client: PostHogInterface? = null

    override fun start() {
        require(apiKey.isNotBlank()) { "PostHog API key is blank" }
        val current = client ?: clientFactory.create(postHogHardenedConfig(apiKey, host)).also { client = it }
        current.optIn()
    }

    override fun capture(name: String) {
        val current = checkNotNull(client) { "capture called before start" }
        current.capture(name)
    }

    override fun stop() {
        val current = client ?: return
        client = null
        try {
            current.optOut()
        } finally {
            current.close()
        }
    }

    public companion object {
        /** PostHog's US cloud ingestion host. */
        public const val DEFAULT_HOST: String = "https://us.i.posthog.com"
    }
}
