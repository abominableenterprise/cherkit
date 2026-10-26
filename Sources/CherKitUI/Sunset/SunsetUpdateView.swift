#if canImport(SwiftUI)
import CherKitSunset
import SwiftUI

/// The hard "Update required" block: optional app icon, title, message and an "Update" button that opens the store.
///
/// It has no way to dismiss it. Present it full screen (``SwiftUI/View/sunsetPrompt(decision:latestVersion:memory:strings:appIcon:tint:tintLabel:)``
/// does) for ``SunsetDecision/hardUpdate(_:)``. The text scrolls when it does not fit, so nothing clips on small
/// screens or at the largest Dynamic Type sizes, while the "Update" button stays pinned at the bottom, always visible.
@available(iOS 16, macOS 13, *)
public struct SunsetUpdateView: View {
    private let prompt: SunsetPrompt
    private let strings: SunsetStrings
    private let appIcon: Image?
    private let tint: Color?
    private let tintLabel: Color?

    @Environment(\.openURL) private var openURL
    /// Resolves `tint` for the automatic label color, so it follows light and dark mode.
    @Environment(\.self) private var environment
    @ScaledMetric(relativeTo: .largeTitle) private var iconSize: CGFloat = 88

    /// Creates the block.
    ///
    /// - Parameters:
    ///   - prompt: From ``SunsetDecision/hardUpdate(_:)``: the store URL and the optional custom message.
    ///   - strings: The text; English defaults.
    ///   - appIcon: Shown above the title when set, e.g. `Image("AppIconImage")`. Decorative: VoiceOver skips it.
    ///   - tint: The "Update" button's and accents' color, e.g. your app's accent color. `nil` (the default) keeps the
    ///     inherited tint. On iOS the block runs in its own window, which does not inherit the app's tint, so pass it.
    ///   - tintLabel: The "Update" button's label color on a `tint` fill. `nil` (the default) picks black or white,
    ///     whichever contrasts more with `tint` in the current light or dark mode. Ignored without a `tint`.
    public init(
        prompt: SunsetPrompt,
        strings: SunsetStrings = SunsetStrings(),
        appIcon: Image? = nil,
        tint: Color? = nil,
        tintLabel: Color? = nil
    ) {
        self.prompt = prompt
        self.strings = strings
        self.appIcon = appIcon
        self.tint = tint
        self.tintLabel = tintLabel
    }

    public var body: some View {
        if let tint {
            screen.tint(tint)
        } else {
            screen
        }
    }

    private var screen: some View {
        GeometryReader { geometry in
            ScrollView {
                content
                    .frame(maxWidth: 480)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 32)
                    // Centered when it fits, scrolling when it does not.
                    .frame(maxWidth: .infinity, minHeight: geometry.size.height)
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            updateButton
                .frame(maxWidth: 480)
                .padding(.horizontal, 24)
                .padding(.vertical, 16)
                .frame(maxWidth: .infinity)
                .background(Self.backgroundColor.ignoresSafeArea())
        }
        .background(Self.backgroundColor.ignoresSafeArea())
    }

    private var content: some View {
        VStack(spacing: 24) {
            if let appIcon {
                let side = min(iconSize, 160)
                appIcon
                    .resizable()
                    .scaledToFit()
                    .frame(width: side, height: side)
                    .clipShape(RoundedRectangle(cornerRadius: side * 0.225, style: .continuous))
                    .accessibilityHidden(true)
            }
            VStack(spacing: 12) {
                Text(strings.hardTitle)
                    .font(.largeTitle.weight(.bold))
                    .accessibilityAddTraits(.isHeader)
                Text(strings.hardText(for: prompt))
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var updateButton: some View {
        Button {
            openURL(prompt.storeURL)
        } label: {
            if let labelColor {
                updateLabel.foregroundStyle(labelColor)
            } else {
                updateLabel
            }
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .keyboardShortcut(.defaultAction)
        .accessibilityHint(strings.updateHint)
    }

    private var updateLabel: some View {
        Text(strings.updateButton)
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 4)
    }

    /// The label color on a `tint` fill: `tintLabel`, else black or white by contrast. `nil` without a tint, which
    /// keeps the system's look.
    private var labelColor: Color? {
        guard let tint else { return nil }
        return tintLabel ?? SunsetTintLabel.color(on: tint, in: environment)
    }

    private static var backgroundColor: Color {
        #if canImport(UIKit) && !os(watchOS)
        Color(uiColor: .systemBackground)
        #elseif canImport(AppKit)
        Color(nsColor: .windowBackgroundColor)
        #else
        Color.clear
        #endif
    }
}
#endif
