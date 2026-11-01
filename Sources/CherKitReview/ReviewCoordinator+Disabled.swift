extension ReviewCoordinator {
    /// A coordinator that is switched off: it never counts, arms or prompts and never reads or writes a store. Use it
    /// in UI tests and previews: `let review = isUITest ? .disabled : ReviewCoordinator()`.
    public static let disabled = ReviewCoordinator(store: InMemoryReviewStore(), appVersion: "", enabled: false)
}
