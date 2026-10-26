#if canImport(SwiftUI)
@testable import CherKitUI
import SwiftUI
import XCTest

/// When `.sunsetPrompt(checker:)` checks: once at launch, again only on a real return from the background, never two
/// at once, and re-checks no more often than `minimumInterval`.
final class SunsetCheckSchedulerTests: XCTestCase {
    private let start = Date(timeIntervalSinceReferenceDate: 1_000_000)

    private func at(_ seconds: TimeInterval) -> Date {
        start.addingTimeInterval(seconds)
    }

    /// A scheduler whose launch check has started and finished at `start`.
    private func launched(minimumInterval: TimeInterval = 60 * 60) -> SunsetCheckScheduler {
        var scheduler = SunsetCheckScheduler(minimumInterval: minimumInterval)
        XCTAssertTrue(scheduler.shouldCheckAtLaunch(now: start))
        scheduler.checkFinished()
        return scheduler
    }

    func testDefaultIntervalIsAnHour() {
        XCTAssertEqual(SunsetCheckScheduler().minimumInterval, 3600)
    }

    func testColdLaunchRunsExactlyOneCheck() {
        var scheduler = SunsetCheckScheduler()
        var checks = 0
        // The order SwiftUI reports a cold launch in: appear, then the initial phases.
        if scheduler.shouldCheckAtLaunch(now: start) { checks += 1 }
        for phase in [ScenePhase.inactive, .active] where scheduler.shouldCheck(onScenePhase: phase, now: at(0)) {
            checks += 1
        }
        scheduler.checkFinished()
        // Appearing again (the root view rebuilt) does not check either.
        if scheduler.shouldCheckAtLaunch(now: at(1)) { checks += 1 }
        XCTAssertEqual(checks, 1)
    }

    func testInitialActiveBeforeAppearDoesNotCheckAndAppearStillDoes() {
        var scheduler = SunsetCheckScheduler()
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .active, now: start))
        XCTAssertTrue(scheduler.shouldCheckAtLaunch(now: start))
    }

    func testInactiveToActiveRunsNoCheck() {
        var scheduler = launched(minimumInterval: 0)
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .inactive, now: at(7200)))
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .active, now: at(7200)))
    }

    func testBackgroundToActiveRunsOneCheck() {
        var scheduler = launched()
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .inactive, now: at(7200)))
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .background, now: at(7200)))
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .inactive, now: at(7300)))
        XCTAssertTrue(scheduler.shouldCheck(onScenePhase: .active, now: at(7300)))
        scheduler.checkFinished()
        // A second `.active` without a new trip to the background does not check again.
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .inactive, now: at(7400)))
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .active, now: at(7400)))
    }

    func testBackgroundDirectlyToActiveAlsoChecks() {
        var scheduler = launched()
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .background, now: at(7200)))
        XCTAssertTrue(scheduler.shouldCheck(onScenePhase: .active, now: at(7200)))
    }

    func testInFlightCheckSkipsANewOne() {
        var scheduler = SunsetCheckScheduler(minimumInterval: 0)
        XCTAssertTrue(scheduler.shouldCheckAtLaunch(now: start))
        XCTAssertTrue(scheduler.isChecking)
        // The launch check is still running when the app goes away and comes back.
        _ = scheduler.shouldCheck(onScenePhase: .background, now: at(10))
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .active, now: at(20)))

        scheduler.checkFinished()
        XCTAssertFalse(scheduler.isChecking)
        _ = scheduler.shouldCheck(onScenePhase: .background, now: at(30))
        XCTAssertTrue(scheduler.shouldCheck(onScenePhase: .active, now: at(40)))
        // And again while that one runs.
        _ = scheduler.shouldCheck(onScenePhase: .background, now: at(50))
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .active, now: at(60)))
    }

    func testIntervalIsRespected() {
        var scheduler = launched(minimumInterval: 3600)
        _ = scheduler.shouldCheck(onScenePhase: .background, now: at(60))
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .active, now: at(3599)), "too soon after the launch check")

        _ = scheduler.shouldCheck(onScenePhase: .background, now: at(3600))
        XCTAssertTrue(scheduler.shouldCheck(onScenePhase: .active, now: at(3600)), "the interval has passed")
        XCTAssertEqual(scheduler.lastCheckStarted, at(3600))
        scheduler.checkFinished()

        _ = scheduler.shouldCheck(onScenePhase: .background, now: at(4000))
        XCTAssertFalse(scheduler.shouldCheck(onScenePhase: .active, now: at(7199)), "counted from the last check")
        _ = scheduler.shouldCheck(onScenePhase: .background, now: at(7200))
        XCTAssertTrue(scheduler.shouldCheck(onScenePhase: .active, now: at(7200)))
    }

    func testZeroIntervalChecksOnEveryReturn() {
        var scheduler = launched(minimumInterval: 0)
        for second in [1.0, 2, 3] {
            _ = scheduler.shouldCheck(onScenePhase: .background, now: at(second))
            XCTAssertTrue(scheduler.shouldCheck(onScenePhase: .active, now: at(second)))
            scheduler.checkFinished()
        }
    }
}
#endif
