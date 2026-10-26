#if canImport(SwiftUI) && canImport(UIKit) && !os(watchOS)
@testable import CherKitUI
import UIKit
import XCTest

/// Tests that the iOS hard block takes over even while the app is presenting a sheet: a `fullScreenCover` cannot be
/// presented from a presenter that is already busy, so the block lives in its own window above the app's windows.
@MainActor
final class SunsetBlockingWindowTests: XCTestCase {
    private var appWindow: UIWindow!
    private var blocking: SunsetBlockingWindow!

    override func setUp() async throws {
        try await super.setUp()
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = try XCTUnwrap(scenes.first ?? UIWindow(frame: UIScreen.main.bounds).windowScene,
                                  "needs a window scene (iOS Simulator)")
        appWindow = UIWindow(windowScene: scene)
        appWindow.rootViewController = UIViewController()
        appWindow.makeKeyAndVisible()
        blocking = SunsetBlockingWindow()
    }

    override func tearDown() async throws {
        blocking.hide()
        appWindow.rootViewController?.dismiss(animated: false)
        appWindow.isHidden = true
        appWindow = nil
        blocking = nil
        try await super.tearDown()
    }

    func testBlockCoversASheetTheAppIsAlreadyPresenting() throws {
        let root = try XCTUnwrap(appWindow.rootViewController)
        let sheet = UIViewController()
        root.present(sheet, animated: false)
        XCTAssertNotNil(root.presentedViewController, "precondition: the app is presenting a sheet")

        let block = UIViewController()
        blocking.show(block, in: try XCTUnwrap(appWindow.windowScene))

        let window = try XCTUnwrap(blocking.window)
        XCTAssertFalse(window.isHidden)
        XCTAssertTrue(window.isKeyWindow)
        XCTAssertIdentical(window.rootViewController, block)
        XCTAssertIdentical(window.windowScene, appWindow.windowScene)
        XCTAssertGreaterThan(window.windowLevel, UIWindow.Level.alert)
        XCTAssertGreaterThan(window.windowLevel, appWindow.windowLevel)
        XCTAssertTrue(block.view.accessibilityViewIsModal)
    }

    func testShowingTheSameBlockAgainKeepsTheWindow() throws {
        let scene = try XCTUnwrap(appWindow.windowScene)
        let block = UIViewController()
        blocking.show(block, in: scene)
        let first = blocking.window
        blocking.show(block, in: scene)
        XCTAssertIdentical(blocking.window, first)
    }

    func testHideRemovesTheBlock() throws {
        blocking.show(UIViewController(), in: try XCTUnwrap(appWindow.windowScene))
        let window = try XCTUnwrap(blocking.window)
        blocking.hide()
        XCTAssertNil(blocking.window)
        XCTAssertTrue(window.isHidden)
    }
}
#endif
