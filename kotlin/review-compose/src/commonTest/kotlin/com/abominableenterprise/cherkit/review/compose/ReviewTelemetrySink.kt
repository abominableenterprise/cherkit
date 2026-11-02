package com.abominableenterprise.cherkit.review.compose

import com.abominableenterprise.cherkit.core.TelemetrySink

/** Captures the complete payload so tests also enforce the absence of private attributes. */
class ReviewTelemetrySink : TelemetrySink {
    val errors = mutableListOf<Triple<String, Int, Map<String, Any>>>()
    val breadcrumbs = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) {
        errors += Triple(domain, code, attributes)
    }
    override fun breadcrumb(name: String, attributes: Map<String, Any>) {
        breadcrumbs += name to attributes
    }
}
