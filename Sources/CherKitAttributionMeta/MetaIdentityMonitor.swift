/// RevenueCat has no public identity-change notification. Sample the current identity itself,
/// not CustomerInfo (which may never arrive after an offline logout).
@MainActor final class MetaIdentityMonitor {
    private var lastIdentity: String
    private let currentIdentity: () -> String
    private let changed: () -> Void

    init(currentIdentity: @escaping () -> String, changed: @escaping () -> Void) {
        self.currentIdentity = currentIdentity
        self.changed = changed
        self.lastIdentity = currentIdentity()
    }

    func sample() {
        let identity = currentIdentity()
        guard identity != lastIdentity else { return }
        lastIdentity = identity
        changed()
    }
}
