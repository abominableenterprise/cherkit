extension ReviewCoordinator {
    /// A coordinator that is switched off: it never counts, arms or prompts and never reads or writes a store. Use it
    /// in UI tests and previews: `let review = isUITest ? .disabled : ReviewCoordinator()`.
    ///
    /// Each access returns a fresh, independent disabled coordinator (like Kotlin's `ReviewCoordinator.disabled()`),
    /// so changing one (e.g. its ``isEligible``) never affects another. Keep the one you got for the app's lifetime:
    /// store it in a `let` or `@State` and pass that around, rather than reading `.disabled` again in each view.
    public static var disabled: ReviewCoordinator {
        ReviewCoordinator(store: InMemoryReviewStore(), appVersion: "", enabled: false)
    }
}
