import CherKitWhatsNew
import Foundation
import XCTest

/// The Foundation adapters: `BundleReleaseNotesSource` on a real `Bundle`, `UserDefaultsWhatsNewStore` and the
/// `CFBundleShortVersionString` reader. These also run on Linux, where only swift-corelibs-foundation is exercised.
final class PlatformAdapterTests: XCTestCase {
    private let valid = Data(#"{ "version": "1.1.0", "notes": { "en": ["Dark mode."] } }"#.utf8)

    func testBundleSourceReadsTheWhatsNewFolderAndSkipsInvalidFiles() throws {
        let folder = try TempNotesDirectory(
            files: ["1.1.0.json": valid, "1.2.0.json": Data("{".utf8), "1.3.0.txt": valid, "v1.4.json": valid],
            subdirectory: "Bundle.bundle/whatsnew")
        defer { folder.remove() }
        let bundle = try XCTUnwrap(Bundle(path: folder.url.deletingLastPathComponent().path))
        let skipped = SkipLog()
        let source = BundleReleaseNotesSource(bundle: bundle, onSkip: skipped.record)

        XCTAssertEqual(source.releaseNotes().map(\.version.description), ["1.1.0"])
        XCTAssertEqual(Set(source.notesFiles().keys), ["1.1.0.json", "1.2.0.json", "v1.4.json"])
        XCTAssertEqual(skipped.names, ["1.2.0.json", "v1.4.json"])
    }

    func testBundleSourceWithoutTheFolderHasNoNotes() throws {
        let folder = try TempNotesDirectory(files: [:], subdirectory: "Empty.bundle")
        defer { folder.remove() }
        let bundle = try XCTUnwrap(Bundle(path: folder.url.path))
        XCTAssertEqual(BundleReleaseNotesSource(bundle: bundle).releaseNotes(), [])
        XCTAssertEqual(BundleReleaseNotesSource(directory: nil).notesFiles(), [:])
    }

    func testUserDefaultsStoreRoundTripsUnderItsPrefix() throws {
        let (defaults, suite) = try freshDefaults()
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = UserDefaultsWhatsNewStore(defaults: defaults, keyPrefix: "test.")
        XCTAssertNil(store.lastSeenVersion())
        XCTAssertEqual(store.installHistory(), [])

        store.setLastSeenVersion("1.2.0")
        store.setInstallHistory(["1.0.0", "1.2.0"])
        XCTAssertEqual(store.lastSeenVersion(), "1.2.0")
        XCTAssertEqual(store.installHistory(), ["1.0.0", "1.2.0"])
        XCTAssertEqual(defaults.string(forKey: "test.lastSeenVersion"), "1.2.0")
        XCTAssertNil(UserDefaultsWhatsNewStore(defaults: defaults).lastSeenVersion())
    }

    func testUserDefaultsStoreTreatsWrongTypesAsAbsent() throws {
        let (defaults, suite) = try freshDefaults()
        defer { defaults.removePersistentDomain(forName: suite) }
        defaults.set(42, forKey: "test.lastSeenVersion")
        defaults.set(["1.0.0", 7], forKey: "test.installHistory")
        let store = UserDefaultsWhatsNewStore(defaults: defaults, keyPrefix: "test.")
        XCTAssertNil(store.lastSeenVersion())
        XCTAssertEqual(store.installHistory(), [])

        let checker = WhatsNewChecker(source: BundleReleaseNotesSource(directory: nil), store: store)
        XCTAssertEqual(checker.onLaunch(installedVersion: "1.1.0", locale: ["en"]), [])
        XCTAssertEqual(store.lastSeenVersion(), "1.1.0")
        XCTAssertEqual(store.installHistory(), ["1.1.0"])
    }

    func testInstalledVersionReadsShortVersionString() throws {
        let folder = try TempNotesDirectory(files: [:], subdirectory: "App.bundle")
        defer { folder.remove() }
        let plist = try PropertyListSerialization.data(
            fromPropertyList: ["CFBundleShortVersionString": "1.4.0", "CFBundleIdentifier": "test.app"],
            format: .xml, options: 0)
        try plist.write(to: folder.url.appendingPathComponent("Info.plist"))
        let bundle = try XCTUnwrap(Bundle(path: folder.url.path))
        XCTAssertEqual(WhatsNewChecker.installedVersion(bundle: bundle), "1.4.0")

        let bare = try TempNotesDirectory(files: [:], subdirectory: "Bare.bundle")
        defer { bare.remove() }
        XCTAssertEqual(WhatsNewChecker.installedVersion(bundle: try XCTUnwrap(Bundle(path: bare.url.path))), "")
    }

    private func freshDefaults() throws -> (UserDefaults, String) {
        let suite = "cherkit.whatsnew.tests.\(UUID().uuidString)"
        return (try XCTUnwrap(UserDefaults(suiteName: suite)), suite)
    }
}
