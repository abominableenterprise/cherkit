/// A ``ReviewStore`` in memory, for tests and previews. Nothing survives the process.
public final class InMemoryReviewStore: ReviewStore {
    /// The current state.
    public var state: ReviewState

    public init(_ state: ReviewState = ReviewState()) {
        self.state = state
    }

    public func load() -> ReviewState { state }

    public func save(_ state: ReviewState) { self.state = state }
}
