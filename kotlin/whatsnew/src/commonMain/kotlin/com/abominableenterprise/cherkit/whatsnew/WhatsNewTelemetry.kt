package com.abominableenterprise.cherkit.whatsnew

import com.abominableenterprise.cherkit.core.TelemetrySink

internal fun TelemetrySink?.notesError(code: Int) {
    // A diagnostics provider must never prevent notes from loading.
    if (this != null) reportWhatsNewTelemetry { recordError("cherkit.whatsnew", code) }
}

/** Android marshals IO-based notes loading back to the main-thread diagnostics owner. */
internal expect fun reportWhatsNewTelemetry(report: () -> Unit)

internal fun TelemetrySink?.notesShown() {
    if (this != null) reportWhatsNewTelemetry { breadcrumb("cherkit.whatsnew.shown") }
}

internal fun List<ReleaseNotes>.reportLocaleFallback(locale: List<String>, telemetrySink: TelemetrySink?) {
    if (telemetrySink == null) return
    val preferred = normalizeLocale(locale.firstOrNull() ?: LocaleFallback.DEFAULT_LOCALE)
    if (any { notes ->
            val resolved = LocaleFallback.resolve(locale, notes.notes.keys)
            resolved != null && normalizeLocale(resolved) != preferred
        }) {
        telemetrySink.notesError(3)
    }
}

private fun normalizeLocale(locale: String): String =
    locale.trimAsciiWhitespace().replace('_', '-').lowercase()
