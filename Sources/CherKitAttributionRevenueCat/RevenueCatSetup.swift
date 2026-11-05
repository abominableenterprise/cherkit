#if canImport(RevenueCat)
import RevenueCat

/// Native configuration pass-through, except device-identifier collection is always disabled.
public struct RevenueCatSetup {
    let apiKey: String
    let appUserID: String?
    let configure: (Configuration.Builder) -> Void

    /// Move all existing builder options into `configure`; identity and purchase APIs remain app-owned.
    public init(
        apiKey: String,
        appUserID: String? = nil,
        configure: @escaping (Configuration.Builder) -> Void = { _ in }
    ) {
        self.apiKey = apiKey
        self.appUserID = appUserID
        self.configure = configure
    }

    func configuration() -> Configuration {
        let builder = Configuration.Builder(withAPIKey: apiKey).with(appUserID: appUserID)
        configure(builder)
        // Apply after app options so an inherited setting cannot re-enable collection.
        return builder.with(automaticDeviceIdentifierCollectionEnabled: false).build()
    }
}
#endif
