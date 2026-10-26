import Foundation

/// Source of the notes files bundled in the app. ``BundleReleaseNotesSource`` reads them from the app bundle.
public protocol ReleaseNotesSource: Sendable {
    /// Every bundled notes file: file name (e.g. `1.4.0.json`) -> content. Never touches the network.
    func notesFiles() -> [String: Data]

    /// The valid notes among ``notesFiles()``. The default reads them with ``ReleaseNotesLoader/load(_:onSkip:)``.
    func releaseNotes() -> [ReleaseNotes]
}

extension ReleaseNotesSource {
    /// The valid notes among ``notesFiles()``, read with ``ReleaseNotesLoader/load(_:onSkip:)``.
    public func releaseNotes() -> [ReleaseNotes] { ReleaseNotesLoader.load(notesFiles()) }
}
