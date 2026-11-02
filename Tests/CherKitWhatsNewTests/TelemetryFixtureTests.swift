import CherKitCore
import CherKitTestSupport
@testable import CherKitWhatsNew
import Foundation
import XCTest

@MainActor
private final class WhatsNewRecordingSink: TelemetrySink {
    struct ErrorRecord: Equatable {
        let domain: String
        let code: Int
    }
    var errors: [ErrorRecord] = []
    var breadcrumbs: [String] = []
    var attributeCounts: [Int] = []
    var onReport: (() -> Void)?

    func recordError(domain: String, code: Int, attributes: [String: Any]) {
        errors.append(ErrorRecord(domain: domain, code: code))
        attributeCounts.append(attributes.count)
        onReport?()
    }

    func breadcrumb(name: String, attributes: [String: Any]) {
        breadcrumbs.append(name)
        attributeCounts.append(attributes.count)
        onReport?()
    }
}

final class TelemetryFixtureTests: XCTestCase {
    private struct Contract: Decodable {
        let domain: String
        let shown: String
        let failureCases: [Failure]
        let localeCases: [LocaleCase]
        let presentationCases: [Presentation]
    }
    private struct Failure: Decodable {
        let name: String
        let input: String
        let codes: [Int]
    }
    private struct LocaleCase: Decodable {
        let name: String
        let preferred: [String]
        let available: [String]
        let selected: String?
        let codes: [Int]
    }
    private struct Presentation: Decodable {
        let name: String
        let lastSeen: String?
        let actions: [String]
        let shownCount: Int
    }

    private let valid = Data(#"{"version":"1.1","notes":{"en":["Private release text."]}}"#.utf8)

    private func contract() throws -> Contract {
        try Fixtures.decode(Contract.self, module: "whatsnew", name: "telemetry.json")
    }

    private func source(for input: String, sink: TelemetrySink? = nil) throws -> (BundleReleaseNotesSource, TempNotesDirectory) {
        let folder = try TempNotesDirectory(files: [:])
        var directory: URL? = folder.url
        switch input {
        case "nilDirectory": directory = nil
        case "missingDirectory": directory = folder.url.appendingPathComponent("missing-private-path")
        case "unreadableFile":
            // A directory named .json is unreadable as Data even when tests run as root.
            try FileManager.default.createDirectory(
                at: folder.url.appendingPathComponent("1.1.json"), withIntermediateDirectories: true)
        case "malformed": try Data("private malformed text".utf8).write(to: folder.url.appendingPathComponent("1.1.json"))
        case "invalid":
            try Data(#"{"version":"private invalid version","notes":{"en":[]}}"#.utf8)
                .write(to: folder.url.appendingPathComponent("1.1.json"))
        case "valid": try valid.write(to: folder.url.appendingPathComponent("1.1.json"))
        case "emptyDirectory": break
        default: XCTFail("Unknown fixture input")
        }
        return (BundleReleaseNotesSource(directory: directory, telemetrySink: sink), folder)
    }

    @MainActor
    func testSourceFailuresThroughCheckerExactlyOnceAndNilIsNoOp() async throws {
        let contract = try contract()
        for testCase in contract.failureCases {
            let (source, directory) = try source(for: testCase.input)
            defer { directory.remove() }
            let sink = WhatsNewRecordingSink()
            let checker = WhatsNewChecker(source: source, store: FakeWhatsNewStore(lastSeen: "1.0"), telemetrySink: sink)
            let result = checker.onLaunch(installedVersion: "1.1", locale: ["en"])
            await checker.telemetry?.drain()
            XCTAssertEqual(sink.errors.map(\.code), testCase.codes, testCase.name)
            XCTAssertTrue(sink.errors.allSatisfy { $0.domain == contract.domain }, testCase.name)
            XCTAssertTrue(sink.breadcrumbs.isEmpty, testCase.name)
            XCTAssertTrue(sink.attributeCounts.allSatisfy { $0 == 0 }, testCase.name)

            let nilChecker = WhatsNewChecker(source: source, store: FakeWhatsNewStore(lastSeen: "1.0"))
            XCTAssertEqual(nilChecker.onLaunch(installedVersion: "1.1", locale: ["en"]), result, testCase.name)
            nilChecker.markSeen()
            nilChecker.markSeen()
            XCTAssertNil(nilChecker.telemetry)
        }
    }

    @MainActor
    func testSourceSinkStandaloneAndCheckerOverrideDoNotDuplicateErrors() async throws {
        for testCase in try contract().failureCases {
            let sourceSink = WhatsNewRecordingSink()
            let checkerSink = WhatsNewRecordingSink()
            let (source, directory) = try source(for: testCase.input, sink: sourceSink)
            defer { directory.remove() }
            _ = source.releaseNotes()
            await source.telemetry?.drain()
            XCTAssertEqual(sourceSink.errors.map(\.code), testCase.codes, testCase.name)
            sourceSink.errors.removeAll()

            let checker = WhatsNewChecker(source: source, store: FakeWhatsNewStore(), telemetrySink: checkerSink)
            _ = checker.history(locale: ["en"])
            await checker.telemetry?.drain()
            await source.telemetry?.drain()
            XCTAssertEqual(checkerSink.errors.map(\.code), testCase.codes, testCase.name)
            XCTAssertTrue(sourceSink.errors.isEmpty, testCase.name)

            _ = WhatsNewChecker(source: source, store: FakeWhatsNewStore()).history(locale: ["en"])
            await source.telemetry?.drain()
            XCTAssertEqual(sourceSink.errors.map(\.code), testCase.codes, testCase.name)
        }
    }

    @MainActor
    func testDefaultCheckerSourceReportsMissingFolder() async throws {
        let sink = WhatsNewRecordingSink()
        // The SwiftPM test bundle has no whatsnew resource folder.
        let checker = WhatsNewChecker(store: FakeWhatsNewStore(), telemetrySink: sink)
        _ = checker.onLaunch(installedVersion: "1.1", locale: ["en"])
        await checker.telemetry?.drain()
        XCTAssertEqual(sink.errors.map(\.code), [1])
        XCTAssertEqual(sink.errors.map(\.domain), [try contract().domain])
    }

    @MainActor
    func testLoaderValidationReportsOncePerFileNotPerProblem() async throws {
        let sink = WhatsNewRecordingSink()
        let reporter = WhatsNewTelemetry(sink)
        let skipped = SkipLog()
        let files = [
            "1.1.json": Data("private malformed text".utf8),
            "1.2.json": Data(#"{"version":"invalid private text","show":7,"notes":5}"#.utf8),
            "1.3.txt": Data("ignored".utf8)
        ]
        XCTAssertEqual(ReleaseNotesLoader.load(files, onSkip: skipped.record, reportingTo: reporter), [])
        await reporter.drain()
        XCTAssertEqual(sink.errors.map(\.code), [2, 2])
        XCTAssertEqual(sink.errors.map(\.domain), [try contract().domain, try contract().domain])
        XCTAssertEqual(skipped.names, ["1.1.json", "1.2.json"])
        XCTAssertEqual(sink.attributeCounts, [0, 0])
        XCTAssertEqual(ReleaseNotesLoader.load(files, telemetrySink: nil), [])
    }

    @MainActor
    func testStandalonePublicAPIsDeliverAsynchronously() async throws {
        let sink = WhatsNewRecordingSink()
        let delivered = expectation(description: "Public loader and locale reports delivered")
        delivered.expectedFulfillmentCount = 2
        delivered.assertForOverFulfill = true
        sink.onReport = { delivered.fulfill() }
        XCTAssertEqual(ReleaseNotesLoader.load(["1.1.json": Data("private text".utf8)], telemetrySink: sink), [])
        XCTAssertEqual(LocaleFallback.resolve(preferred: ["ja"], available: ["en"], telemetrySink: sink), "en")
        await fulfillment(of: [delivered], timeout: 5)
        XCTAssertEqual(sink.errors.map(\.code).sorted(), [2, 3])
        XCTAssertEqual(sink.attributeCounts, [0, 0])
    }

    @MainActor
    func testUnreadableFileStillCallsOnSkipAndNotesFilesReportsOnlyReadError() async throws {
        let sink = WhatsNewRecordingSink()
        let (base, directory) = try source(for: "unreadableFile")
        defer { directory.remove() }
        let skipped = SkipLog()
        let source = BundleReleaseNotesSource(directory: base.directory, onSkip: skipped.record, telemetrySink: sink)
        XCTAssertEqual(source.releaseNotes(), [])
        await source.telemetry?.drain()
        XCTAssertEqual(skipped.names, ["1.1.json"])
        XCTAssertEqual(sink.errors.map(\.code), [1])
        XCTAssertEqual(source.notesFiles(), ["1.1.json": Data()])
        await source.telemetry?.drain()
        XCTAssertEqual(sink.errors.map(\.code), [1, 1])
    }

    @MainActor
    func testLocaleFallbackContractAndNilIsNoOp() async throws {
        let contract = try contract()
        for testCase in contract.localeCases {
            let sink = WhatsNewRecordingSink()
            let reporter = WhatsNewTelemetry(sink)
            XCTAssertEqual(
                LocaleFallback.resolve(preferred: testCase.preferred, available: testCase.available, reportingTo: reporter),
                testCase.selected, testCase.name)
            await reporter.drain()
            XCTAssertEqual(sink.errors.map(\.code), testCase.codes, testCase.name)
            XCTAssertTrue(sink.errors.allSatisfy { $0.domain == contract.domain }, testCase.name)
            XCTAssertTrue(sink.attributeCounts.allSatisfy { $0 == 0 }, testCase.name)
            XCTAssertEqual(
                LocaleFallback.resolve(preferred: testCase.preferred, available: testCase.available),
                testCase.selected, testCase.name)
        }
    }

    @MainActor
    func testCheckerReportsFallbackPerEntryOnLaunchAndHistory() async throws {
        let folder = try TempNotesDirectory(files: ["1.1.json": valid])
        defer { folder.remove() }
        let sink = WhatsNewRecordingSink()
        let checker = WhatsNewChecker(
            source: BundleReleaseNotesSource(directory: folder.url),
            store: FakeWhatsNewStore(lastSeen: "1.0"), telemetrySink: sink)
        XCTAssertEqual(checker.onLaunch(installedVersion: "1.1", locale: ["ja"]).count, 1)
        XCTAssertEqual(checker.history(locale: ["ja"]).count, 1)
        await checker.telemetry?.drain()
        XCTAssertEqual(sink.errors.map(\.code), [3, 3])
        XCTAssertTrue(sink.breadcrumbs.isEmpty)
        XCTAssertEqual(sink.attributeCounts, [0, 0])
    }

    @MainActor
    func testShownOnlyAfterSuccessfulMarkSeenOnce() async throws {
        let contract = try contract()
        for testCase in contract.presentationCases {
            let folder = try TempNotesDirectory(files: [
                "1.1.json": valid,
                "1.0.1.json": Data(#"{"version":"1.0.1","notes":{"en":["Earlier private text."]}}"#.utf8)
            ])
            defer { folder.remove() }
            let sink = WhatsNewRecordingSink()
            let store = FakeWhatsNewStore(lastSeen: testCase.lastSeen)
            let checker = WhatsNewChecker(
                source: BundleReleaseNotesSource(directory: folder.url), store: store, telemetrySink: sink)
            for action in testCase.actions {
                switch action {
                case "launch": _ = checker.onLaunch(installedVersion: "1.1", locale: ["en"])
                case "history": _ = checker.history(locale: ["en"])
                case "markSeen": checker.markSeen()
                default: XCTFail("Unknown fixture action")
                }
            }
            await checker.telemetry?.drain()
            XCTAssertEqual(sink.breadcrumbs, Array(repeating: contract.shown, count: testCase.shownCount), testCase.name)
            XCTAssertTrue(sink.errors.isEmpty, testCase.name)
            XCTAssertTrue(sink.attributeCounts.allSatisfy { $0 == 0 }, testCase.name)
            if testCase.shownCount > 0 { XCTAssertEqual(store.lastSeenVersion(), "1.1", testCase.name) }
        }
    }
}
