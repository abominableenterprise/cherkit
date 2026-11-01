package com.abominableenterprise.cherkit.review

/** A [ReviewStore] in memory, for tests and previews. Nothing survives the process. */
public class InMemoryReviewStore(
    /** The current state. */
    public var state: ReviewState = ReviewState(),
) : ReviewStore {
    override fun load(): ReviewState = state

    override fun save(state: ReviewState) {
        this.state = state
    }
}
