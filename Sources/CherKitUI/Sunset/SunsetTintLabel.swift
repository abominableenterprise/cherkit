#if canImport(SwiftUI)
import SwiftUI
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// Picks a readable label color (black or white) for a button filled with the hard block's `tint:`, using the WCAG 2
/// relative luminance, so a bright tint such as a light dark-mode accent gets a black label.
///
/// White, the system look, stays while it keeps WCAG's 3:1 contrast minimum for large or bold text (the label is
/// bold `.headline`), so common accents such as system blue keep their white label. Brighter fills get black, which
/// then has at least 7:1.
enum SunsetTintLabel {
    /// The least contrast ratio white must keep against the fill: WCAG 2 AA for large or bold text.
    static let whiteMinimumContrast = 3.0

    /// The relative luminance above which white falls below ``whiteMinimumContrast`` and the label turns black:
    /// 1.05 / (L + 0.05) = 3 gives L = 0.30.
    static let luminanceThreshold = 1.05 / whiteMinimumContrast - 0.05

    /// The WCAG 2 relative luminance of a color with gamma-encoded sRGB components in `0...1` (clamped).
    static func relativeLuminance(red: Double, green: Double, blue: Double) -> Double {
        func linear(_ component: Double) -> Double {
            let c = min(max(component, 0), 1)
            return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }

    /// Whether a black label reads better than a white one on a fill with these sRGB components.
    static func prefersBlack(red: Double, green: Double, blue: Double) -> Bool {
        relativeLuminance(red: red, green: green, blue: blue) > luminanceThreshold
    }

    /// Black or white for a label on `tint`, resolved in `environment` (so it follows light and dark mode).
    static func color(on tint: Color, in environment: EnvironmentValues) -> Color {
        guard let rgb = components(of: tint, in: environment) else { return .white }
        return prefersBlack(red: rgb.red, green: rgb.green, blue: rgb.blue) ? .black : .white
    }

    /// `tint`'s gamma-encoded sRGB components in `environment`, or `nil` if they cannot be read.
    static func components(of tint: Color, in environment: EnvironmentValues) -> (red: Double, green: Double, blue: Double)? {
        if #available(iOS 17, macOS 14, tvOS 17, watchOS 10, *) {
            let resolved = tint.resolve(in: environment)
            return (Double(resolved.red), Double(resolved.green), Double(resolved.blue))
        }
        #if canImport(UIKit)
        let style: UIUserInterfaceStyle = environment.colorScheme == .dark ? .dark : .light
        let color = UIColor(tint).resolvedColor(with: UITraitCollection(userInterfaceStyle: style))
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        guard color.getRed(&red, green: &green, blue: &blue, alpha: &alpha) else { return nil }
        return (Double(red), Double(green), Double(blue))
        #elseif canImport(AppKit)
        let name: NSAppearance.Name = environment.colorScheme == .dark ? .darkAqua : .aqua
        var rgb: (red: Double, green: Double, blue: Double)?
        (NSAppearance(named: name) ?? NSAppearance.currentDrawing()).performAsCurrentDrawingAppearance {
            if let color = NSColor(tint).usingColorSpace(.sRGB) {
                rgb = (Double(color.redComponent), Double(color.greenComponent), Double(color.blueComponent))
            }
        }
        return rgb
        #else
        return nil
        #endif
    }
}
#endif
