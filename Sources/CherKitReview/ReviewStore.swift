/// Where ``ReviewCoordinator`` keeps its ``ReviewState``. Built in: ``UserDefaultsReviewStore`` (the default) and
/// ``InMemoryReviewStore`` (tests). Calls happen on the main actor and must be fast (local storage).
public protocol ReviewStore: AnyObject {
    /// The stored state; `ReviewState()` when nothing (readable) is stored. Must not throw.
    func load() -> ReviewState
    /// Replaces the stored state.
    func save(_ state: ReviewState)
}
