attribution-revenuecat: added

- Replace RevenueCat configuration with one adapter call, preserving native purchase settings while disabling automatic device-identifier collection.
- Enable RevenueCat-owned Apple Search Ads attribution only for audited adult-only deployments on release iOS devices outside test hosts. Restricted or switchable deployments keep purchases without new attribution activation; Android configures purchases only.
