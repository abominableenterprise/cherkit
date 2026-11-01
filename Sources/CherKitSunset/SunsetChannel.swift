/// How the running build was installed. It picks which "newer build" sources apply (see
/// ``SunsetDecider/decide(config:platform:appVersion:osVersion:languages:channel:installedBuild:lookup:now:)``).
public enum SunsetChannel: String, CaseIterable, Sendable {
    /// A release install from the App Store or Google Play (the default): the config and, when enabled, the App Store
    /// lookup can offer an update.
    case store
    /// A TestFlight beta install: the config's `testFlight` block can offer a newer test build. Never gets App Store
    /// lookup prompts.
    case testFlight
    /// Anything else (debug, simulator, sideload): only the config applies, as before channels existed.
    case other
}
