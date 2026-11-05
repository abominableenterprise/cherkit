attribution-meta: added

- Android one-call Meta/RevenueCat setup uses `MetaRevenueCatAttribution.start(application, revenueCat: RevenueCatSetup, audience: Audience)`; `stop()` removes CherKit's observation/activation work, not vendor queues or server events. RevenueCat remains the only Meta purchase sender.
- Document required manifest provider removal and privacy flags, isolated RevenueCat CAPI datasets/event selection, and pinned Facebook 19.0.1 / RevenueCat 9.29.1 capability limits. Internal identity-storage observation preserves the app's customer-info listener but requires re-audit on SDK upgrades.
- Keep Meta disabled where advertising-ID access, automatic matching or required withdrawal suppression cannot be verified. Automatic session activation events are suppressed while automatic event logging is OFF; neither install delivery nor CAPI delivery guarantees campaign attribution.
