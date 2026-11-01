import CherKitSunset
import Foundation
import XCTest

/// `SunsetChannel.current`: how the running app was installed, from its App Store receipt URL.
final class SunsetChannelCurrentTests: XCTestCase {
    private let sandbox = URL(fileURLWithPath: "/var/mobile/Containers/Data/Application/X/StoreKit/sandboxReceipt")
    private let production = URL(fileURLWithPath: "/var/mobile/Containers/Data/Application/X/StoreKit/receipt")

    func testSandboxReceiptIsTestFlight() {
        XCTAssertEqual(SunsetChannel.current(receiptURL: sandbox, isSimulator: false), .testFlight)
    }

    func testProductionReceiptIsTheStore() {
        XCTAssertEqual(SunsetChannel.current(receiptURL: production, isSimulator: false), .store)
    }

    func testNoReceiptURLIsTheStore() {
        XCTAssertEqual(SunsetChannel.current(receiptURL: nil, isSimulator: false), .store)
    }

    func testOnlyTheExactFileNameCounts() {
        for name in ["SandboxReceipt", "sandboxReceipt.bak", "sandbox", "receipt"] {
            let url = URL(fileURLWithPath: "/StoreKit").appendingPathComponent(name)
            XCTAssertEqual(SunsetChannel.current(receiptURL: url, isSimulator: false), .store, name)
        }
        let folder = URL(fileURLWithPath: "/sandboxReceipt/receipt")
        XCTAssertEqual(SunsetChannel.current(receiptURL: folder, isSimulator: false), .store)
    }

    func testTheSimulatorIsOtherWhateverTheReceipt() {
        for url in [sandbox, production, nil] {
            XCTAssertEqual(SunsetChannel.current(receiptURL: url, isSimulator: true), .other)
        }
    }

    func testTheRunningProcess() {
        #if os(iOS) && targetEnvironment(simulator)
        XCTAssertEqual(SunsetChannel.current(), .other, "the simulator never gets TestFlight or store prompts")
        #elseif !os(iOS)
        XCTAssertEqual(SunsetChannel.current(), .store, "outside iOS: the default from before channels")
        #endif
    }
}
