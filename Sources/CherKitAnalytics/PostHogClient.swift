#if canImport(PostHog)
import PostHog

/// The only PostHog SDK calls the adapter makes. Tests swap in a fake so they never touch the SDK or the network.
/// Deliberately absent: `identify`, `alias`, `group`, `register`, person properties and capture with properties.
protocol PostHogClient: AnyObject {
    func setup(_ config: PostHogConfig)
    func optIn()
    func capture(_ event: String)
    func optOut()
    func close()
}

extension PostHogSDK: PostHogClient {}
#endif
