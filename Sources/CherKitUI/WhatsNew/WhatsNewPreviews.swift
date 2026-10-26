#if canImport(SwiftUI) && DEBUG
import CherKitCore
import CherKitWhatsNew
import SwiftUI

/// Sample releases of an "Example App" for the previews below.
@available(iOS 16, macOS 13, *)
enum WhatsNewPreviewData {
    static let releases: [(version: String, date: String?, bullets: [String])] = [
        ("2.1.0", "2026-11-02", [
            "Export your entries to CSV from Settings.",
            "Widgets now follow your accent color.",
            "Fixed a crash when opening a shared link.",
        ]),
        ("2.0.0", "2026-09-14", [
            "A fresh new look across Example App.",
            "Search now finds notes as well as titles.",
        ]),
        ("1.9.2", nil, []),
        ("1.9.0", "2026-06-30", ["Dark mode for every screen."]),
    ]

    static func entries(_ count: Int) -> [WhatsNewEntry] {
        releases.filter { !$0.bullets.isEmpty }.prefix(count).compactMap { release in
            AppVersion(release.version).map { WhatsNewEntry(version: $0, date: release.date, bullets: release.bullets) }
        }
    }

    static var history: [WhatsNewHistoryEntry] {
        releases.compactMap { release in
            AppVersion(release.version).map {
                WhatsNewHistoryEntry(
                    version: $0, date: release.date, bullets: release.bullets, isInstalled: release.version == "2.0.0")
            }
        }
    }
}

@available(iOS 17, macOS 14, *)
#Preview("Sheet, one version") {
    WhatsNewSheet(entries: WhatsNewPreviewData.entries(1))
}

@available(iOS 17, macOS 14, *)
#Preview("Sheet, three versions") {
    WhatsNewSheet(entries: WhatsNewPreviewData.entries(3))
}

@available(iOS 17, macOS 14, *)
#Preview("History") {
    NavigationStack {
        WhatsNewHistoryView(entries: WhatsNewPreviewData.history)
    }
}

@available(iOS 17, macOS 14, *)
#Preview("Largest Dynamic Type") {
    TabView {
        WhatsNewSheet(entries: WhatsNewPreviewData.entries(3))
            .tabItem { Text(verbatim: "Sheet") }
        NavigationStack { WhatsNewHistoryView(entries: WhatsNewPreviewData.history) }
            .tabItem { Text(verbatim: "History") }
    }
    .dynamicTypeSize(.accessibility5)
}

@available(iOS 17, macOS 14, *)
#Preview("Presented by .whatsNew(entries:)") {
    Text(verbatim: "Example App")
        .whatsNew(entries: WhatsNewPreviewData.entries(1)) {}
}
#endif
