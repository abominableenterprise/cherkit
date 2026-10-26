#if canImport(SwiftUI)
import SwiftUI

/// "Version 1.4.0" with the release date (shown as-is) underneath, marked as a heading for VoiceOver's rotor.
@available(iOS 16, macOS 13, *)
struct WhatsNewReleaseHeader: View {
    let title: String
    let date: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.headline)
                .accessibilityAddTraits(.isHeader)
            if let date, !date.isEmpty {
                Text(date)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}
#endif
