#if canImport(PostHog)
import Foundation
import PostHog

/// The PostHog adapter for `Analytics` (iOS and macOS). It drives `PostHogSDK.shared` with a hardened config
/// (see `PostHogHardenedConfig`) and only ever calls `setup`, `optIn`, `capture(name)`, `optOut` and `close`:
/// no properties, no identify, alias, group or register, no person properties.
///
/// Don't set up `PostHogSDK.shared` yourself as well: this backend owns it.
///
/// Lifecycle: `start()` sets the SDK up if it isn't (first start, or after a `stop()`) and opts in. `stop()` opts
/// out (persisted, so a relaunch stays silent until the next `start()`) and closes the SDK. In posthog-ios 3.71.4
/// `close()` clears the instance's state and marks it not set up, so the next `start()` calls `setup` again on
/// the same shared instance.
public final class PostHogAnalyticsBackend: AnalyticsBackend {
    enum StartError: Error, Equatable {
        case blankAPIKey
    }

    enum CaptureError: Error, Equatable {
        case notStarted
    }

    private let apiKey: String
    private let host: String
    private let client: PostHogClient
    private var isSetUp = false

    /// - Parameters:
    ///   - apiKey: the PostHog project key. It is a public client key, safe to ship in the app. A blank key makes
    ///     `start()` throw, so `Analytics` keeps analytics off.
    ///   - host: the PostHog ingestion host, e.g. `"https://eu.i.posthog.com"` for EU Cloud projects.
    public convenience init(apiKey: String, host: String = "https://us.i.posthog.com") {
        self.init(apiKey: apiKey, host: host, client: PostHogSDK.shared)
    }

    init(apiKey: String, host: String, client: PostHogClient) {
        self.apiKey = apiKey
        self.host = host
        self.client = client
    }

    public func start() throws {
        guard !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw StartError.blankAPIKey }
        if !isSetUp {
            client.setup(PostHogHardenedConfig.make(apiKey: apiKey, host: host))
            isSetUp = true
        }
        client.optIn()
    }

    public func capture(_ name: String) throws {
        guard isSetUp else { throw CaptureError.notStarted }
        client.capture(name)
    }

    public func stop() throws {
        guard isSetUp else { return }
        isSetUp = false
        client.optOut()
        client.close()
    }
}
#endif
