package com.abominableenterprise.cherkit.review.compose

import com.abominableenterprise.cherkit.core.TelemetrySink

/** Diagnostics are optional and must never replace a Play result with a sink failure. */
internal object ReviewTelemetry {
    fun report(sink: TelemetrySink?, code: Int) {
        try {
            sink?.recordError("cherkit.review", code)
        } catch (_: Exception) {
            // A provider failure cannot change the request/launch result or consume another prompt.
        }
    }
}
