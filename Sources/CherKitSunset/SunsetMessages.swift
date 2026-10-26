import Foundation

/// Picks the custom prompt text for the user's languages.
public enum SunsetMessages {
    /// The language used when none of the user's languages has a message.
    public static let fallbackLanguage = "en"

    /// The first message matching, in order: each preferred tag, then that tag with trailing subtags removed one at a
    /// time (`de-DE` -> `de`), then ``fallbackLanguage``. `nil` when none matches: the UI shows its default text.
    ///
    /// Tags and keys match case-insensitively (ASCII), with `_` treated as `-` (so Android's `de_DE` works). Empty
    /// texts, empty tags, and tags or keys with non-ASCII characters are skipped. When two keys are the same after
    /// that (`de` and `DE`), the one sorting first wins.
    public static func resolve(_ messages: [String: String], languages: [String]) -> String? {
        var byTag: [String: String] = [:]
        // Keys are ASCII here, so Swift's and Kotlin's sort orders agree on the winner.
        for key in messages.keys.filter(isUsableTag).sorted() {
            guard let text = messages[key], !text.isEmpty else { continue }
            let tag = normalize(key)
            if byTag[tag] == nil { byTag[tag] = text }
        }
        for language in languages where isUsableTag(language) {
            var subtags = normalize(language).split(separator: "-", omittingEmptySubsequences: false)
            while !subtags.isEmpty {
                let tag = subtags.joined(separator: "-")
                if !tag.isEmpty, let text = byTag[tag] { return text }
                subtags.removeLast()
            }
        }
        return byTag[fallbackLanguage]
    }

    /// Non-empty and ASCII only, as BCP 47 tags are; Swift and Kotlin compare non-ASCII strings differently.
    private static func isUsableTag(_ tag: String) -> Bool {
        !tag.isEmpty && tag.unicodeScalars.allSatisfy(\.isASCII)
    }

    /// Lowercased with `_` replaced by `-`; only called on ASCII tags.
    private static func normalize(_ tag: String) -> String {
        tag.lowercased().replacingOccurrences(of: "_", with: "-")
    }
}
