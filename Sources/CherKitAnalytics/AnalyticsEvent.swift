/// One allowlisted analytics event. Each app owns its own type listing every event it may send,
/// usually a `String` enum, which gets `name` for free:
///
///     enum ExampleEvent: String, AnalyticsEvent {
///         case appOpened = "app_opened"
///     }
///
/// There are deliberately no event properties: only the name is ever sent, so no PII can leak through an event.
public protocol AnalyticsEvent {
    /// The event name sent to the provider. Must match `^[a-z][a-z0-9_]{0,63}$`, or the event is dropped.
    var name: String { get }
}

public extension AnalyticsEvent where Self: RawRepresentable, RawValue == String {
    var name: String { rawValue }
}
