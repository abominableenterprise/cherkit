/// Well-known sources for ``ReviewCoordinator/setInterruptionVisible(_:source:)``. Apps may use any string of their
/// own (e.g. `"paywall"`); each source is tracked separately, so two modals never clear each other.
public enum ReviewInterruption {
    /// Sunset's soft "Update available" prompt.
    public static let sunsetPrompt = "sunset.prompt"
    /// Sunset's hard "Update required" block.
    public static let sunsetBlock = "sunset.block"
}
