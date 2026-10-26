/// The platform whose block of the hosted config applies to this app build.
public enum SunsetPlatform: String, CaseIterable, Sendable {
    /// The `"ios"` block: App Store builds.
    case ios
    /// The `"android"` block: Google Play builds.
    case android
}
