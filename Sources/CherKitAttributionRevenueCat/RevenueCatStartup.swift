/// SDK effects stay separate from the Foundation-only policy and startup ordering.
protocol RevenueCatClient {
    var isConfigured: Bool { get }
    func configure()
    func enableAppleAttribution()
}

enum RevenueCatStartup {
    /// Already configured SDKs are left untouched: their startup privacy cannot be established here.
    static func start(client: RevenueCatClient, audience: Audience, environment: AttributionEnvironment) {
        guard !client.isConfigured else { return }
        client.configure()
        if shouldEnableAppleAttribution(audience: audience, environment: environment) {
            client.enableAppleAttribution()
        }
    }
}
