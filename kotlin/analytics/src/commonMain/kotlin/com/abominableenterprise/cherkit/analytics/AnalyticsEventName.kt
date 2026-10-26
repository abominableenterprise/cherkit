package com.abominableenterprise.cherkit.analytics

/**
 * The event-name rule shared with Swift: `^[a-z][a-z0-9_]{0,63}$`, ASCII only.
 * Apps can call [isValid] in their own tests to check every entry of their event type.
 */
public object AnalyticsEventName {
    public const val MAX_LENGTH: Int = 64

    public fun isValid(name: String): Boolean {
        if (name.isEmpty() || name.length > MAX_LENGTH || name[0] !in 'a'..'z') return false
        return name.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }
    }
}
