import Foundation

/// Reads the app's bundled `whatsnew/<version>.json` files: every `.json` file in a folder of a `Bundle`'s resources.
/// In Xcode, add the `whatsnew` folder to the app target as a folder reference so it is copied as a folder.
public struct BundleReleaseNotesSource: ReleaseNotesSource {
    /// Default resources subdirectory holding the notes files.
    public static let defaultSubdirectory = "whatsnew"

    /// The folder read, or `nil` when the bundle has no resources folder.
    public let directory: URL?
    private let onSkip: ReleaseNotesLoader.SkipHandler?

    /// Reads `subdirectory` of `bundle`'s resources. `onSkip` is told about every invalid file that is skipped.
    public init(
        bundle: Bundle = .main, subdirectory: String = defaultSubdirectory,
        onSkip: ReleaseNotesLoader.SkipHandler? = nil
    ) {
        self.init(directory: bundle.resourceURL?.appendingPathComponent(subdirectory, isDirectory: true), onSkip: onSkip)
    }

    /// Reads the `.json` files directly inside `directory` (for tests, or notes kept outside a bundle).
    public init(directory: URL?, onSkip: ReleaseNotesLoader.SkipHandler? = nil) {
        self.directory = directory
        self.onSkip = onSkip
    }

    /// Every `.json` file in ``directory``: file name -> content. A missing folder gives no files; a file that cannot
    /// be read is returned empty, so ``releaseNotes()`` skips and reports it. Never throws.
    public func notesFiles() -> [String: Data] {
        guard let directory,
              let urls = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
        else { return [:] }
        var files: [String: Data] = [:]
        for url in urls where url.pathExtension == "json" {
            files[url.lastPathComponent] = (try? Data(contentsOf: url)) ?? Data()
        }
        return files
    }

    /// The valid notes in ``directory``; invalid files are skipped and reported to `onSkip`.
    public func releaseNotes() -> [ReleaseNotes] { ReleaseNotesLoader.load(notesFiles(), onSkip: onSkip) }
}
