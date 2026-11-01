#if canImport(SwiftUI)
import SwiftUI

/// Remembers the last value reported, so a callback hears each change once. Shared by the `onVisibilityChange` and
/// `onPromptChange` callbacks of the Sunset and WhatsNew modifiers and by `.reviewInterruption`.
@MainActor
final class VisibilityTracker<Value: Equatable> {
    /// The value last reported; the hidden value before the first report.
    private(set) var reported: Value

    init(hidden: Value) {
        reported = hidden
    }

    /// Calls `report(old, new)` when `value` differs from the last value reported, and remembers it.
    func update(_ value: Value, report: (_ old: Value, _ new: Value) -> Void) {
        guard value != reported else { return }
        let old = reported
        reported = value
        report(old, value)
    }
}

/// Reports `value` through `report` on appear and on every change, and `hidden` when the view goes away. Never for
/// the initial hidden value, never twice in a row for the same value.
@available(iOS 16, macOS 13, *)
struct VisibilityChangeReporter<Value: Equatable>: ViewModifier {
    let value: Value
    let hidden: Value
    let report: @MainActor (_ old: Value, _ new: Value) -> Void
    @State private var tracker: VisibilityTracker<Value>

    init(value: Value, hidden: Value, report: @escaping @MainActor (_ old: Value, _ new: Value) -> Void) {
        self.value = value
        self.hidden = hidden
        self.report = report
        _tracker = State(initialValue: VisibilityTracker(hidden: hidden))
    }

    func body(content: Content) -> some View {
        content
            .onAppear { tracker.update(value, report: report) }
            .onChange(of: value) { tracker.update($0, report: report) }
            .onDisappear { tracker.update(hidden, report: report) }
    }
}
#endif
