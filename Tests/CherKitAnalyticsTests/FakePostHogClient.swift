#if canImport(PostHog)
@testable import CherKitAnalytics
import PostHog

/// Records every PostHog call the adapter makes as "setup", "optIn", "capture:<name>", "optOut" or "close".
/// The protocol has no way to pass properties, identify or register, so recording the calls covers everything sent.
final class FakePostHogClient: PostHogClient {
    private(set) var calls: [String] = []
    private(set) var configs: [PostHogConfig] = []

    func setup(_ config: PostHogConfig) {
        configs.append(config)
        calls.append("setup")
    }

    func optIn() { calls.append("optIn") }
    func capture(_ event: String) { calls.append("capture:\(event)") }
    func optOut() { calls.append("optOut") }
    func close() { calls.append("close") }
}
#endif
