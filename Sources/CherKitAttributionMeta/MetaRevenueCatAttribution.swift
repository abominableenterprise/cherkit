#if canImport(RevenueCat)
import Foundation
import CherKitAttributionRevenueCat
import RevenueCat

/// Optional composition of safe RevenueCat configuration and eligible Meta activation.
@MainActor public enum MetaRevenueCatAttribution {
    #if canImport(FacebookCore) && os(iOS)
    private static var startup: MetaStartup?
    #endif

    /// Replaces `Purchases.configure`. Later starts cannot upgrade audience or restart after stop.
    public static func start(revenueCat: RevenueCatSetup, audience: Audience) {
        #if canImport(FacebookCore) && os(iOS)
        guard startup == nil else { return }
        let coordinator = MetaStartup(
            meta: LiveMetaClient(),
            revenueCat: LiveMetaRevenueCatClient(setup: revenueCat, audience: audience),
            observation: LiveMetaObservation(),
            eligible: { MetaGate.current(audience: audience).allowsAttribution }
        )
        startup = coordinator
        coordinator.start()
        #else
        RevenueCatAttribution.start(revenueCat: revenueCat, audience: audience)
        #endif
    }

    /// Stops only our subscription and future activations, not vendor queues or server forwarding.
    public static func stop() {
        #if canImport(FacebookCore) && os(iOS)
        startup?.stop()
        #endif
    }
}
#endif
