package com.abominableenterprise.cherkit.telemetry

/**
 * Provider adapter. Calls must return quickly on the caller's thread; failures are swallowed.
 * Stop must discard queued work and context, stop traces, and support restarting.
 */
public interface TelemetryBackend {
    /**
     * Apply the session restriction before start and on active transitions. Restricted sessions allow
     * errors only: disable automatic diagnostics and performance collection without starting here.
     * A failure prevents startup, or stops an active backend. Default is a no-op for manual-only adapters.
     */
    public fun setRestricted(restricted: Boolean) {}
    public fun start()
    public fun stop()
    public fun recordError(error: TelemetryError, attributes: Map<String, Any>)
    public fun breadcrumb(name: String, attributes: Map<String, Any>)
    /** Return an opaque ID unique among live traces, even when trace names are identical. */
    public fun startTrace(name: String, attributes: Map<String, Any>): String
    public fun stopTrace(id: String, status: SpanStatus)
    public fun setAttribute(traceID: String, key: String, value: Any)
    public fun setKey(key: String, value: Any)
}
