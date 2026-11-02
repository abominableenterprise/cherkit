package com.abominableenterprise.cherkit.core

/** Minimal diagnostics contract; modules need not depend on a telemetry provider. */
public interface TelemetrySink {
    /** Reports a safe, app-defined domain/code, never a localized error message. */
    public fun recordError(domain: String, code: Int, attributes: Map<String, Any> = emptyMap())

    /** Records an enum-like name with allowlisted attributes. */
    public fun breadcrumb(name: String, attributes: Map<String, Any> = emptyMap())
}
