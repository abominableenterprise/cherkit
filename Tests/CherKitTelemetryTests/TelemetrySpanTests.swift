import CherKitTelemetry
import XCTest

@MainActor
final class TelemetrySpanTests: XCTestCase {
    private final class ExpectedError: Error {}

    func testSyncSpanReturnsResultAndEndsWithStatus() throws {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let result = telemetry.span("work", attributes: ["state": "ready"]) { 42 }
        XCTAssertEqual(result, 42)
        let expected = ExpectedError()
        XCTAssertThrowsError(try telemetry.span("work") { throw expected }) { error in
            XCTAssertTrue(error as? ExpectedError === expected)
        }
        XCTAssertEqual(backend.stops.map { $0.1 }, [.ok, .error])
        XCTAssertTrue(backend.liveTraces.isEmpty)
    }

    func testAsyncSpanReturnsResultAndRethrowsSameError() async throws {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let result = await telemetry.span("work") { () async -> Int in
            await Task.yield()
            return 42
        }
        XCTAssertEqual(result, 42)
        let expected = ExpectedError()
        do {
            _ = try await telemetry.span("work") { () async throws -> Int in
                await Task.yield()
                throw expected
            }
            XCTFail("Expected the original error")
        } catch {
            XCTAssertTrue(error as? ExpectedError === expected)
        }
        XCTAssertEqual(backend.stops.map { $0.1 }, [.ok, .error])
        XCTAssertTrue(backend.liveTraces.isEmpty)
    }

    func testManualSpanUsesBackendIDAndIsIdempotent() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let span = telemetry.startSpan(name: "work")
        span.setAttribute("state", value: "ready")
        span.end(status: .error)
        span.setAttribute("state", value: "later")
        span.end()
        XCTAssertEqual(backend.traceAttributes.count, 1)
        XCTAssertEqual(backend.traceAttributes.first?.0, "trace-1")
        XCTAssertEqual(backend.stops.first?.0, "trace-1")
        XCTAssertEqual(backend.stops.first?.1, .error)
        XCTAssertEqual(backend.calls, ["start", "startTrace", "setAttribute", "stopTrace"])
    }

    func testRestrictionStopsAllLiveTracesAndPermanentlyInvalidatesHandles() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let first = telemetry.startSpan(name: "first")
        let second = telemetry.startSpan(name: "second")
        telemetry.restricted = true
        XCTAssertTrue(backend.liveTraces.isEmpty)
        XCTAssertEqual(backend.stops.count, 2)
        XCTAssertFalse(backend.calls.contains("stop"))
        let count = backend.calls.count
        first.setAttribute("state", value: "ready")
        first.end()
        telemetry.restricted = false
        second.setAttribute("state", value: "ready")
        second.end(status: .error)
        XCTAssertEqual(backend.calls.count, count)
        let fresh = telemetry.startSpan(name: "fresh")
        fresh.setAttribute("state", value: "ready")
        fresh.end()
        XCTAssertEqual(backend.traceAttributes.count, 1)
        XCTAssertEqual(backend.stops.count, 3)
    }

    func testDisablingStopsTracesBeforeBackendAndOldHandlesStayInvalidAfterRestart() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let span = telemetry.startSpan(name: "work")
        telemetry.enabled = false
        XCTAssertEqual(backend.calls, ["start", "startTrace", "stopTrace", "stop"])
        XCTAssertTrue(backend.liveTraces.isEmpty)
        span.setAttribute("state", value: "ready")
        telemetry.enabled = true
        let count = backend.calls.count
        span.setAttribute("state", value: "ready")
        span.end()
        XCTAssertEqual(backend.calls.count, count)
    }

    func testFailedCleanupDoesNotPreventOtherTraceCleanupOrBackendStop() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let first = telemetry.startSpan(name: "first")
        let second = telemetry.startSpan(name: "second")
        backend.failNext = ["stopTrace", "stop"]
        telemetry.enabled = false
        XCTAssertEqual(backend.calls.suffix(3), ["stopTrace", "stopTrace", "stop"])
        XCTAssertTrue(backend.liveTraces.isEmpty)
        telemetry.enabled = true
        let count = backend.calls.count
        first.end()
        second.end()
        XCTAssertEqual(backend.calls.count, count)
    }

    func testRestrictionCleanupContinuesAfterStopFailure() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let first = telemetry.startSpan(name: "first")
        let second = telemetry.startSpan(name: "second")
        backend.failNext.insert("stopTrace")
        telemetry.restricted = true
        XCTAssertEqual(backend.stops.count, 2)
        XCTAssertTrue(backend.liveTraces.isEmpty)
        telemetry.recordError(domain: "test", code: 1)
        XCTAssertEqual(backend.errors.count, 1)
        telemetry.restricted = false
        let count = backend.calls.count
        first.setAttribute("state", value: "ready")
        second.end()
        XCTAssertEqual(backend.calls.count, count)
    }

    func testUnchangedGatesDoNotInvalidateSpans() {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let span = telemetry.startSpan(name: "work")
        telemetry.enabled = true
        telemetry.restricted = false
        span.setAttribute("state", value: "ready")
        span.end()
        XCTAssertEqual(backend.traceAttributes.count, 1)
        XCTAssertEqual(backend.stops.count, 1)
    }

    func testAsyncGateChangeCleansUpBeforeBodyFinishes() async {
        let backend = RecordingBackend()
        let telemetry = Telemetry(backend: backend, buildEnabled: true)
        let result = await telemetry.span("work") { () async -> Int in
            telemetry.restricted = true
            XCTAssertTrue(backend.liveTraces.isEmpty)
            telemetry.restricted = false
            await Task.yield()
            return 42
        }
        XCTAssertEqual(result, 42)
        XCTAssertEqual(backend.stops.count, 1)
    }
}
