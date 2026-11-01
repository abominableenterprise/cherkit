import Foundation

extension SunsetChannel {
    /// The file name of the App Store receipt in a TestFlight install (and in Xcode, ad hoc and enterprise installs).
    static let sandboxReceiptName = "sandboxReceipt"

    /// `true` when this code runs in the iOS Simulator.
    static var runsInSimulator: Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }

    /// How the running iOS app was installed, from `bundle`'s App Store receipt URL:
    ///
    /// - ``other`` in the Simulator;
    /// - ``testFlight`` when the receipt file is named `sandboxReceipt`;
    /// - ``store`` otherwise.
    ///
    /// Outside iOS (macOS, Linux) it answers ``store``, the default from before channels existed.
    ///
    /// A library cannot tell a debug build from a TestFlight one: an install from Xcode has a `sandboxReceipt` too.
    /// Pass ``other`` yourself in debug builds:
    ///
    /// ```swift
    /// #if DEBUG
    /// let channel = SunsetChannel.other
    /// #else
    /// let channel = SunsetChannel.current()
    /// #endif
    /// ```
    public static func current(bundle: Bundle = .main) -> SunsetChannel {
        #if os(iOS)
        return current(receiptURL: bundle.appStoreReceiptURL, isSimulator: runsInSimulator)
        #else
        return .store
        #endif
    }

    /// The rule behind ``current(bundle:)`` on iOS, pure so it can be tested: ``other`` when `isSimulator`,
    /// ``testFlight`` when `receiptURL`'s last path component is `sandboxReceipt`, else ``store`` (a `nil` URL
    /// included).
    public static func current(receiptURL: URL?, isSimulator: Bool) -> SunsetChannel {
        if isSimulator { return .other }
        return receiptURL?.lastPathComponent == sandboxReceiptName ? .testFlight : .store
    }
}
