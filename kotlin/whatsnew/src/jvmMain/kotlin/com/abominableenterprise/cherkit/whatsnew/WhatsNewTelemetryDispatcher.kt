package com.abominableenterprise.cherkit.whatsnew

/** JVM hosts own their event loop; use a sink bound to the same thread as the checker. */
internal actual fun reportWhatsNewTelemetry(report: () -> Unit) {
    runCatching(report)
}
