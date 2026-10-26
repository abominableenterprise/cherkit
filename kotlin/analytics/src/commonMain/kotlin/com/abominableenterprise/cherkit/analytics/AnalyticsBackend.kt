package com.abominableenterprise.cherkit.analytics

/**
 * What a provider adapter (e.g. the PostHog adapter) implements. [Analytics] decides when each call happens;
 * the backend only forwards to the provider SDK.
 *
 * - Calls arrive on the thread that uses [Analytics] (the main thread) and must return quickly:
 *   enqueue work, never block on the network.
 * - Throwing is allowed; [Analytics] catches every exception and treats it as a failed, best-effort call.
 */
public interface AnalyticsBackend {
    /**
     * Set up (or resume) the provider SDK and opt in. Called lazily, each time the gate opens.
     * If it throws, [Analytics] calls [stop] right away to roll back any partial opt-in.
     */
    public fun start()

    /** Send one event by name. Only called between a successful [start] and the next [stop]. */
    public fun capture(name: String)

    /**
     * Opt out and close the provider SDK. Called whenever the gate closes. Must not send anything
     * queued after the gate closed, and must leave the SDK able to [start] again.
     */
    public fun stop()
}
