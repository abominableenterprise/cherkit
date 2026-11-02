import CherKitCore

/// Full ASCII matching avoids regex end-anchor exceptions for trailing newlines.
enum TelemetryValidation {
    static func name(_ name: String) -> Bool {
        let bytes = Array(name.utf8)
        return (1...64).contains(bytes.count) && bytes.first.map { (97...122).contains($0) } == true
            && bytes.allSatisfy { lowerOrDigit($0) || $0 == 95 || $0 == 46 }
    }

    static func value(_ value: Any) -> Bool {
        // Exact types prevent Foundation bridging from admitting NSNumber, Float or arbitrary objects.
        switch Swift.type(of: value) {
        case is Bool.Type, is Int.Type, is AppVersion.Type: return true
        case is Double.Type: return (value as! Double).isFinite
        case is String.Type:
            let bytes = Array((value as! String).utf8)
            return (1...64).contains(bytes.count)
                && bytes.allSatisfy { lowerOrDigit($0) || $0 == 95 || $0 == 46 || $0 == 45 }
        default: return false
        }
    }

    static func attributes(_ attributes: [String: Any]) -> [String: Any] {
        attributes.filter { name($0.key) && value($0.value) }
    }

    private static func lowerOrDigit(_ byte: UInt8) -> Bool {
        (97...122).contains(byte) || (48...57).contains(byte)
    }
}
