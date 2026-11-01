#if canImport(SwiftUI)
/// What the Sunset callbacks hear, as one value so a single change reports both in order: the prompt showing
/// (`onPromptChange`, `onVisibilityChange`) first, then whether the launch check has resolved
/// (`onFirstCheckComplete`). An app therefore never hears "settled" before the first decision's kind.
struct SunsetPromptState: Equatable, Sendable {
    /// The prompt showing, `nil` for none.
    var kind: SunsetPromptKind?
    /// Whether the `checker:` form's launch check has resolved. Always `false` for the `decision:` form.
    var settled = false
}
#endif
