#if canImport(SwiftUI)
import CherKitWhatsNew
import SwiftUI

/// Every release in `entries` (newest first, as ``WhatsNewChecker/history(locale:)`` returns them) with its date and
/// bullets; the installed one carries a "You're on this version" badge. Push it from Settings:
///
/// ```swift
/// NavigationLink("Version History") { WhatsNewHistoryView(entries: whatsNew.history()) }
/// ```
///
/// It sets its own navigation title; it does not add a `NavigationStack`.
@available(iOS 16, macOS 13, *)
public struct WhatsNewHistoryView: View {
    private let entries: [WhatsNewHistoryEntry]
    private let strings: WhatsNewStrings

    /// A history list for `entries`, with English text unless you pass `strings`.
    public init(entries: [WhatsNewHistoryEntry], strings: WhatsNewStrings = .english) {
        self.entries = entries
        self.strings = strings
    }

    public var body: some View {
        List {
            if entries.isEmpty {
                Text(strings.emptyHistory)
                    .foregroundStyle(.secondary)
            }
            ForEach(Array(entries.enumerated()), id: \.offset) { _, entry in
                Section {
                    row(entry)
                }
            }
        }
        .navigationTitle(strings.historyTitle)
        #if os(iOS)
        .listStyle(.insetGrouped)
        #endif
    }

    private func row(_ entry: WhatsNewHistoryEntry) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            WhatsNewReleaseHeader(title: strings.versionTitle(entry.version.description), date: entry.date)
            if entry.isInstalled {
                WhatsNewInstalledBadge(text: strings.installedBadge)
            }
            if entry.bullets.isEmpty {
                Text(strings.noNotes)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                WhatsNewBulletList(bullets: entry.bullets)
            }
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .contain)
    }
}

/// The "You're on this version" capsule: tinted text on a tinted background, readable in light and dark mode,
/// wrapping instead of clipping at large Dynamic Type sizes.
@available(iOS 16, macOS 13, *)
struct WhatsNewInstalledBadge: View {
    let text: String

    var body: some View {
        Label {
            Text(text)
                .fixedSize(horizontal: false, vertical: true)
        } icon: {
            Image(systemName: "checkmark.circle.fill")
                .accessibilityHidden(true)
        }
        .font(.footnote.weight(.semibold))
        .foregroundStyle(Color.accentColor)
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(Color.accentColor.opacity(0.15), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
#endif
