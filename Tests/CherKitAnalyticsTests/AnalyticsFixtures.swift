/// Shapes of `fixtures/analytics/gate.json` and `fixtures/analytics/lifecycle.json`.
struct GateFixture: Decodable {
    struct Case: Decodable {
        let description: String
        let buildEnabled: Bool
        let consentGranted: Bool
        let sessionActive: Bool
        let event: String
        let sent: Bool
    }

    let cases: [Case]
}

struct LifecycleFixture: Decodable {
    struct Operation: Decodable {
        let op: String
        let value: Bool?
        let event: String?
        let sent: Bool?
        let call: String?
    }

    struct Case: Decodable {
        let description: String
        let buildEnabled: Bool
        let operations: [Operation]
        let calls: [String]
    }

    let cases: [Case]
}
