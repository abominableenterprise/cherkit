import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

extension URLSessionSunsetLookupSource {
    /// A source for the running app: `bundle`'s identifier in the store of `locale`'s region (see
    /// ``country(for:)``). `nil` when the bundle has no identifier or the locale has no two-letter region, so the
    /// checker then simply has no lookup (fails open).
    ///
    /// The region is the user's settings, which usually but not always matches their App Store storefront. If you
    /// already know the storefront (StoreKit's `Storefront.current`), pass its country to
    /// ``init(bundleID:country:session:)`` instead.
    public init?(bundle: Bundle = .main, locale: Locale = .current, session: URLSession = .shared) {
        guard let bundleID = bundle.bundleIdentifier, !bundleID.isEmpty, let country = Self.country(for: locale)
        else { return nil }
        self.init(bundleID: bundleID, country: country, session: session)
    }

    /// `locale`'s region as a lowercase two-letter country code for the lookup, e.g. `"us"` for `en_US`; `nil` when
    /// it has none or it is not two ASCII letters (e.g. the numeric `419`).
    public static func country(for locale: Locale) -> String? {
        #if canImport(Darwin)
        let region = locale.region?.identifier
        #else
        let region = locale.regionCode
        #endif
        guard let region, region.count == 2,
              region.unicodeScalars.allSatisfy({ ("A"..."Z").contains($0) || ("a"..."z").contains($0) })
        else { return nil }
        return region.lowercased()
    }
}
