package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.AppVersion

/**
 * The versions installed on this device, oldest first, for the full history view ("you are here" is the last one).
 * Contract: `fixtures/whatsnew/install-history.json`.
 */
public object InstallHistory {
    /** Default number of entries kept. */
    public const val DEFAULT_CAP: Int = 100

    /**
     * [history] after launching [current]: the normalized version (e.g. `1.4.0`) is appended when it differs from the
     * last entry (a downgrade is a change too), then the oldest entries are dropped to keep at most [cap] (at least 1).
     * An unreadable [current] fails open: nothing is appended. Stored entries are never rewritten.
     */
    public fun updated(history: List<String>, current: String, cap: Int = DEFAULT_CAP): List<String> {
        val version = AppVersion.parse(current)
        val last = history.lastOrNull()?.let(AppVersion::parse)
        val result = if (version != null && version != last) history + version.toString() else history
        return result.takeLast(maxOf(cap, 1))
    }
}
