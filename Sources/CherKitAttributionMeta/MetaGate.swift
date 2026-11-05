import Foundation
import CoreFoundation
import CherKitAttributionRevenueCat

/// Same deployment guards as the RevenueCat route, with bundled Meta policy added.
struct MetaGate {
    let audience: Audience
    let platform: String
    let isDebug: Bool
    let isTestHost: Bool
    let isSimulator: Bool
    let configuration: [String: Any]

    var allowsAttribution: Bool {
        audience == .adultOnly && platform == "iOS"
            && !isDebug && !isTestHost && !isSimulator && hasSafeConfiguration
    }

    var hasSafeConfiguration: Bool {
        let identifiers = ["FacebookAppID", "FacebookClientToken"]
        let flags = ["FacebookAutoLogAppEventsEnabled", "FacebookAdvertiserIDCollectionEnabled",
                     "FBSDKAutoLogMetaDataEnabled", "FBSDKAutoLogProductImprovementEnabled",
                     "FacebookCodelessDebugLogEnabled", "FacebookSKAdNetworkReportEnabled",
                     "FBSDKAemAutoSetupEnabled"]
        return identifiers.allSatisfy {
            guard let value = configuration[$0] as? String else { return false }
            return !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        } && flags.allSatisfy {
            // Missing and string-valued flags are not an approved startup policy.
            guard let value = configuration[$0] as? NSNumber,
                  CFGetTypeID(value) == CFBooleanGetTypeID() else { return false }
            return !value.boolValue
        }
    }
}
