package com.abominableenterprise.cherkit.analytics

/**
 * One allowlisted analytics event. Each app owns its own type listing every event it may send, usually an enum:
 *
 * ```
 * enum class ExampleEvent(override val eventName: String) : AnalyticsEvent {
 *     APP_OPENED("app_opened"),
 * }
 * ```
 *
 * The property is `eventName`, not `name` as in Swift, because a Kotlin enum's `name` is final.
 * There are deliberately no event properties: only the name is ever sent, so no PII can leak through an event.
 */
public interface AnalyticsEvent {
    /** The event name sent to the provider. Must match `^[a-z][a-z0-9_]{0,63}$`, or the event is dropped. */
    public val eventName: String
}
