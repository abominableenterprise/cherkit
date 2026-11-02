package com.abominableenterprise.cherkit.update.compose

import com.abominableenterprise.cherkit.core.TelemetrySink

/** Diagnostics must never change the outcome of a Play flow. No exception text crosses this boundary. */
internal object UpdateTelemetry {
    fun error(sink: TelemetrySink?, code: Int) {
        try { sink?.recordError("cherkit.update", code) } catch (_: Exception) { }
    }

    fun breadcrumb(sink: TelemetrySink?, kind: String) {
        try { sink?.breadcrumb("cherkit.update.$kind") } catch (_: Exception) { }
    }
}
