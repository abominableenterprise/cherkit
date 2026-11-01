#if canImport(SwiftUI)
import CherKitReview

extension ReviewCoordinator {
    /// Reports Sunset's prompts as interruptions, for `.sunsetPrompt(onPromptChange:)`: `.soft` as
    /// ``ReviewInterruption/sunsetPrompt``, `.hard` as ``ReviewInterruption/sunsetBlock``, `nil` hides both.
    ///
    /// ```swift
    /// .sunsetPrompt(checker: sunset, onPromptChange: { review.setSunsetPromptKind($0) })
    /// ```
    public func setSunsetPromptKind(_ kind: SunsetPromptKind?) {
        setInterruptionVisible(kind == .soft, source: ReviewInterruption.sunsetPrompt)
        setInterruptionVisible(kind == .hard, source: ReviewInterruption.sunsetBlock)
    }
}
#endif
