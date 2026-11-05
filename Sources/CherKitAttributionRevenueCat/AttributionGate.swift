/// A deployment property, not the role of the currently signed-in user.
public enum Audience: String, Codable, Sendable {
    /// The entire deployment is audited for adults only, with no restricted sessions.
    case adultOnly
    /// Includes unknown, restricted, or switchable audiences. Purchases only.
    case restrictedOrSwitchable
}

struct AttributionEnvironment {
    let platform: String
    let isDebug: Bool
    let isTestHost: Bool
    let isSimulator: Bool
}

func shouldEnableAppleAttribution(audience: Audience, environment: AttributionEnvironment) -> Bool {
    audience == .adultOnly && environment.platform == "iOS"
        && !environment.isDebug && !environment.isTestHost && !environment.isSimulator
}
