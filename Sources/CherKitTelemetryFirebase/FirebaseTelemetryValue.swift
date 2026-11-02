import CherKitCore
import Foundation

/// Repeat the core's shape checks at the vendor boundary; never stringify arbitrary objects.
enum FirebaseTelemetryValue {
    static func name(_ value: String) -> Bool {
        let bytes = Array(value.utf8)
        return (1...64).contains(bytes.count) && bytes.first.map { (97...122).contains($0) } == true
            && bytes.allSatisfy { lowerOrDigit($0) || $0 == 95 || $0 == 46 }
    }

    static func attributeName(_ value: String) -> Bool {
        name(value) && value.utf8.count <= 40
            && value.utf8.allSatisfy { (97...122).contains($0) || $0 == 95 }
            && !["firebase", "google", "ga_"].contains(where: value.hasPrefix)
    }

    static func string(_ value: Any) -> String? {
        switch Swift.type(of: value) {
        case is Bool.Type: return (value as! Bool) ? "true" : "false"
        case is Int.Type: return String(value as! Int)
        case is Double.Type:
            let number = value as! Double
            return number.isFinite ? String(number) : nil
        case is AppVersion.Type: return String(describing: value as! AppVersion)
        case is String.Type:
            let text = value as! String
            let bytes = Array(text.utf8)
            guard (1...64).contains(bytes.count),
                  bytes.allSatisfy({ lowerOrDigit($0) || $0 == 95 || $0 == 46 || $0 == 45 }) else { return nil }
            return text
        default: return nil
        }
    }

    private static func lowerOrDigit(_ value: UInt8) -> Bool {
        (97...122).contains(value) || (48...57).contains(value)
    }
}
