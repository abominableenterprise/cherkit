#if canImport(SwiftUI)
import CherKitWhatsNew
import SwiftUI

/// The "What's new" sheet: every release in `entries` (newest first, as ``WhatsNewChecker`` returns them) with its
/// date and bullets, and a "Continue" button that dismisses it. One release shows as a single block; several get one
/// section each. Everything is local: no network, no loading state.
///
/// Usually presented by ``SwiftUI/View/whatsNew(checker:installedVersion:locale:strings:)``; present it yourself with
/// `.sheet` if you need to. "Continue" calls `onContinue` when given, else the environment's `dismiss`.
@available(iOS 16, macOS 13, *)
public struct WhatsNewSheet: View {
    private let entries: [WhatsNewEntry]
    private let strings: WhatsNewStrings
    private let onContinue: (() -> Void)?
    @Environment(\.dismiss) private var dismiss

    /// A sheet for `entries`, with English text unless you pass `strings`.
    public init(entries: [WhatsNewEntry], strings: WhatsNewStrings = .english, onContinue: (() -> Void)? = nil) {
        self.entries = entries
        self.strings = strings
        self.onContinue = onContinue
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text(strings.title)
                    .font(.largeTitle.bold())
                    .accessibilityAddTraits(.isHeader)
                    .fixedSize(horizontal: false, vertical: true)
                ForEach(Array(entries.enumerated()), id: \.offset) { index, entry in
                    if index > 0 { Divider() }
                    release(entry)
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 32)
            .padding(.bottom, 16)
            .frame(maxWidth: 560, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .safeAreaInset(edge: .bottom) { continueButton }
        #if os(macOS)
        .frame(minWidth: 380, idealWidth: 440, minHeight: 360, idealHeight: 520)
        #endif
    }

    private func release(_ entry: WhatsNewEntry) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            WhatsNewReleaseHeader(title: strings.versionTitle(entry.version.description), date: entry.date)
            WhatsNewBulletList(bullets: entry.bullets)
        }
        .accessibilityElement(children: .contain)
    }

    private var continueButton: some View {
        Button {
            if let onContinue { onContinue() } else { dismiss() }
        } label: {
            Text(strings.continueButton)
                .font(.headline)
                .frame(maxWidth: .infinity)
                .fixedSize(horizontal: false, vertical: true)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .keyboardShortcut(.defaultAction)
        .frame(maxWidth: 512)
        .padding(.horizontal, 24)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(.bar)
    }
}
#endif
