package com.abominableenterprise.cherkit.telemetry

/** Sanitized error identity. Never contains the original Throwable, its message, or its cause. */
public data class TelemetryError(
    public val type: String,
    public val domain: String? = null,
    public val code: Int? = null,
)
