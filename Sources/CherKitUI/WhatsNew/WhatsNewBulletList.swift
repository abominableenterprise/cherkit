#if canImport(SwiftUI)
import SwiftUI

/// A release's bullets, one per line, wrapping freely at every Dynamic Type size. Each bullet is its own VoiceOver
/// element; the "•" glyph is hidden from VoiceOver.
@available(iOS 16, macOS 13, *)
struct WhatsNewBulletList: View {
    let bullets: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(bullets.enumerated()), id: \.offset) { _, bullet in
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(verbatim: "•")
                        .foregroundStyle(.secondary)
                        .accessibilityHidden(true)
                    Text(bullet)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .accessibilityElement(children: .combine)
            }
        }
    }
}
#endif
