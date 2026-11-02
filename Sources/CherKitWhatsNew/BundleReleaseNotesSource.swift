import CherKitCore
import Foundation

/// Reads the app's bundled `whatsnew/<version>.json` files: every `.json` file in a folder of a `Bundle`'s resources.
/// In Xcode, add the `whatsnew` folder to the app target as a folder reference so it is copied as a folder.
public struct BundleReleaseNotesSource: ReleaseNotesSource, ReportingReleaseNotesSource {
    /// Default resources subdirectory holding the notes files.
    public static let defaultSubdirectory = "whatsnew"

    /// The folder read, or `nil` when the bundle has no resources folder.
    public let directory: URL?
    private let onSkip: ReleaseNotesLoader.SkipHandler?
    internal let telemetry: WhatsNewTelemetry?

    /// Reads `subdirectory` of `bundle`'s resources. `onSkip` is told about every invalid file that is skipped.
    public init(
        bundle: Bundle = .main, subdirectory: String = defaultSubdirectory,
        onSkip: ReleaseNotesLoader.SkipHandler? = nil, telemetrySink: TelemetrySink? = nil
    ) {
        self.init(
            directory: bundle.resourceURL?.appendingPathComponent(subdirectory, isDirectory: true),
            onSkip: onSkip, telemetrySink: telemetrySink)
    }

    /// Reads the `.json` files directly inside `directory` (for tests, or notes kept outside a bundle).
    public init(directory: URL?, onSkip: ReleaseNotesLoader.SkipHandler? = nil, telemetrySink: TelemetrySink? = nil) {
        self.directory = directory
        self.onSkip = onSkip
        self.telemetry = telemetrySink.map(WhatsNewTelemetry.init)
    }

    /// Every `.json` file in ``directory``: file name -> content. A missing folder gives no files; a file that cannot
    /// be read is returned empty, so ``releaseNotes()`` skips and reports it. Never throws.
    public func notesFiles() -> [String: Data] { readFiles(reportingTo: telemetry).files }

    private func readFiles(reportingTo telemetry: WhatsNewTelemetry?) -> (files: [String: Data], unreadable: Set<String>) {
        guard let directory,
              let urls = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
        else {
            telemetry?.error(1)
            return ([:], [])
        }
        var files: [String: Data] = [:]
        var unreadable = Set<String>()
        for url in urls.sorted(by: { $0.lastPathComponent < $1.lastPathComponent }) where url.pathExtension == "json" {
            let name = url.lastPathComponent
            if let data = try? Data(contentsOf: url) {
                files[name] = data
            } else {
                telemetry?.error(1)
                unreadable.insert(name)
                files[name] = Data()
            }
        }
        return (files, unreadable)
    }

    /// The valid notes in ``directory``; invalid files are skipped and reported to `onSkip`.
    public func releaseNotes() -> [ReleaseNotes] { releaseNotes(reportingTo: nil) }

    internal func releaseNotes(reportingTo override: WhatsNewTelemetry?) -> [ReleaseNotes] {
        let reporter = override ?? telemetry
        let result = readFiles(reportingTo: reporter)
        // Preserve onSkip's file-name order and empty-data validation, without also reporting code 2.
        return ReleaseNotesLoader.load(
            result.files, onSkip: onSkip, reportingTo: reporter, unreadableFileNames: result.unreadable)
    }
}
