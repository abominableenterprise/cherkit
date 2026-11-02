/// Sanitized error identity only: never retains an Error, message, localized description or userInfo.
public struct TelemetryError: Equatable, Sendable {
    public let type: String
    public let domain: String?
    public let code: Int?

    public init(type: String, domain: String? = nil, code: Int? = nil) {
        self.type = type
        self.domain = domain
        self.code = code
    }
}

public enum SpanStatus: Equatable, Sendable {
    case ok
    case error
}
