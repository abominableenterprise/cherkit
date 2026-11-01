import CherKitTestSupport
import Foundation

/// `fixtures/review/coordinator.json`; Kotlin's `ReviewCoordinatorFixtureTest` runs the same cases.
struct ReviewFixture: Decodable {
    let cases: [Case]

    static func load() throws -> ReviewFixture {
        try Fixtures.decode(ReviewFixture.self, module: "review", name: "coordinator.json")
    }

    struct Case: Decodable {
        let name: String
        let version: String
        let enabled: Bool?
        let successThreshold: Int?
        let cooldownSeconds: Double?
        let stored: Stored?
        let steps: [Step]
    }

    struct Stored: Decodable, Equatable {
        let successCount: Int
        let isDue: Bool
        let lastPromptedVersion: String?
    }

    struct Step: Decodable {
        let `do`: String
        let times: Int?
        let source: String?
        let visible: Bool?
        let value: Bool?
        let seconds: Double?
        let version: String?
        let isIdle: Bool?
        let shouldPrompt: Bool?
        let isDue: Bool?
        let cooldownRemaining: Double?
        let result: Bool?
        let successCount: Int?
        let lastPromptedVersion: String?
    }
}
