package com.abominableenterprise.cherkit.analytics

internal actual fun classExists(name: String): Boolean = try {
    Class.forName(name, false, AnalyticsTestHost::class.java.classLoader)
    true
} catch (_: ClassNotFoundException) {
    false
} catch (_: LinkageError) {
    false
}
