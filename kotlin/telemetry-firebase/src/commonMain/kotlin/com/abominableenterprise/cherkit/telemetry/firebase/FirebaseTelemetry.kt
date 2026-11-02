package com.abominableenterprise.cherkit.telemetry.firebase

import com.abominableenterprise.cherkit.telemetry.Telemetry

internal fun firebaseTelemetry(
    backend: FirebaseBackend,
    buildEnabled: Boolean,
    enabled: Boolean,
    restricted: Boolean,
): Telemetry = Telemetry(backend, buildEnabled).also {
    // Assign restriction before enabled, which can start the backend.
    it.restricted = restricted
    it.enabled = enabled
}
