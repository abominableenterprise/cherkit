import CherKitCore
import Foundation

/// Turns hosted JSON into a ``SunsetConfig``, dropping whatever is malformed (see ``SunsetConfig/parse(_:)``).
enum SunsetConfigParser {
    static func parse(_ data: Data) -> SunsetConfig? {
        guard case .object(let root)? = try? JSONDecoder().decode(JSONValue.self, from: data),
              case .number(let schema)? = root["schemaVersion"],
              schema == Double(SunsetConfig.supportedSchemaVersion)
        else { return nil }
        return SunsetConfig(ios: block(root[SunsetPlatform.ios.rawValue]), android: block(root[SunsetPlatform.android.rawValue]))
    }

    private static func block(_ value: JSONValue?) -> SunsetPlatformConfig? {
        guard case .object(let fields)? = value,
              case .valid(let minimum) = version(fields["minimumVersion"]),
              case .valid(let latest) = version(fields["latestVersion"]),
              case .valid(let minimumOS) = version(fields["latestMinimumOS"]),
              case .string(let urlText)? = fields["storeURL"],
              let storeURL = SunsetStoreURL.validate(urlText)
        else { return nil }
        return SunsetPlatformConfig(
            minimumVersion: minimum,
            latestVersion: latest,
            latestMinimumOS: minimumOS,
            storeURL: storeURL,
            messages: messages(fields["message"])
        )
    }

    private enum OptionalVersion {
        case valid(AppVersion?)
        case invalid
    }

    /// Missing or `null` is a valid absent version; anything but a parseable string is invalid.
    private static func version(_ value: JSONValue?) -> OptionalVersion {
        switch value {
        case nil, .null?: return .valid(nil)
        case .string(let text)?: return AppVersion(text).map { .valid($0) } ?? .invalid
        default: return .invalid
        }
    }

    /// String entries of the `message` object; anything else is dropped.
    private static func messages(_ value: JSONValue?) -> [String: String] {
        guard case .object(let entries)? = value else { return [:] }
        return entries.compactMapValues { entry in
            guard case .string(let text) = entry else { return nil }
            return text
        }
    }
}
