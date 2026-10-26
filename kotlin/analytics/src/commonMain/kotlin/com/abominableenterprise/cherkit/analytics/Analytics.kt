package com.abominableenterprise.cherkit.analytics

/**
 * The typed analytics facade apps call instead of a provider SDK.
 *
 * An event is sent only when the gate is open (`buildEnabled && consentGranted && sessionActive`) and its name
 * is valid (see [AnalyticsEventName]). The backend is started lazily when the gate opens, stopped whenever it
 * closes, and started again if it re-opens. Analytics is best-effort: it never throws an exception into app code.
 *
 * Not thread-safe, like the Swift version's `@MainActor`: use one instance from the main thread.
 *
 * @param buildEnabled fixed for the instance's lifetime. Apps pass `false` for debug builds, tests (see
 *   [AnalyticsTestHost]) and emulators.
 */
public class Analytics<E : AnalyticsEvent>(
    private val backend: AnalyticsBackend,
    public val buildEnabled: Boolean,
) {
    private var backendStarted = false

    /** The user's opt-in. Starts `false`; the app sets it from its persisted consent. */
    public var consentGranted: Boolean = false
        set(value) {
            field = value
            syncBackend()
        }

    /** Whether analytics may run for the current session, e.g. a signed-in adult. Never `true` on a kid's device. */
    public var sessionActive: Boolean = false
        set(value) {
            field = value
            syncBackend()
        }

    public val isGateOpen: Boolean
        get() = buildEnabled && consentGranted && sessionActive

    /** Sends [event] if the gate is open and its name is valid. Returns whether it was handed to the backend. */
    public fun track(event: E): Boolean {
        val name = try {
            event.eventName
        } catch (_: Exception) {
            return false
        }
        if (!isGateOpen || !AnalyticsEventName.isValid(name)) return false
        if (!backendStarted) startBackend()
        if (!backendStarted) return false
        return bestEffort { backend.capture(name) }
    }

    private fun syncBackend() {
        if (isGateOpen && !backendStarted) {
            startBackend()
        } else if (!isGateOpen && backendStarted) {
            backendStarted = false
            bestEffort { backend.stop() }
        }
    }

    private fun startBackend() {
        backendStarted = bestEffort { backend.start() }
        // Roll back a partial start: the SDK may have opted in before throwing.
        if (!backendStarted) bestEffort { backend.stop() }
    }

    /** Runs [call] and reports whether it returned normally; exceptions are swallowed. */
    private inline fun bestEffort(call: () -> Unit): Boolean = try {
        call()
        true
    } catch (_: Exception) {
        false
    }
}
