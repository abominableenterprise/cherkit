package com.abominableenterprise.cherkit.attribution.revenuecat

/** An audited deployment property, never the current user's role or consent state. */
public enum class Audience {
    /** Every session in this deployment is eligible for adult-only attribution. */
    AdultOnly,

    /** Restricted, unknown or switchable audiences: purchases only. */
    RestrictedOrSwitchable,
}
