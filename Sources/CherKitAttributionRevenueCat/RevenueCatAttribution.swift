#if canImport(RevenueCat)
import Foundation
import RevenueCat

/// Replaces, rather than follows, the app's `Purchases.configure` call.
@MainActor public enum RevenueCatAttribution {
    /// Configures the shared SDK once. Later calls (including changed audiences/options) are no-ops.
    /// Use only at startup in a deployment with an audited, stable audience policy.
    public static func start(revenueCat: RevenueCatSetup, audience: Audience) {
        RevenueCatStartup.start(
            client: LiveRevenueCatClient(setup: revenueCat), audience: audience,
            environment: .current
        )
    }
}

private struct LiveRevenueCatClient: RevenueCatClient {
    let setup: RevenueCatSetup
    var isConfigured: Bool { Purchases.isConfigured }
    func configure() { Purchases.configure(with: setup.configuration()) }
    func enableAppleAttribution() {
        #if os(iOS)
        Purchases.shared.attribution.enableAdServicesAttributionTokenCollection()
        #endif
    }
}
#endif
