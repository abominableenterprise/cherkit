/// Only this small interface can perform Meta effects. No purchase or advertising-ID API is exposed.
@MainActor protocol MetaClient: AnyObject {
    func disableAutomaticCollection()
    func initialize()
    var anonymousID: String { get }
    func activate()
}

@MainActor protocol MetaRevenueCatClient: AnyObject {
    var isConfigured: Bool { get }
    func startPurchases()
    func setAnonymousID(_ value: String)
}

@MainActor protocol MetaObservation: AnyObject {
    /// One owned subscription, covering foreground and customer-info/identity updates.
    func start(foreground: @escaping () -> Void, identityChanged: @escaping () -> Void)
    func stop()
}

@MainActor final class MetaStartup {
    private var started = false
    private var active = false
    private let meta: MetaClient
    private let revenueCat: MetaRevenueCatClient
    private let observation: MetaObservation
    private let eligible: () -> Bool

    init(meta: MetaClient, revenueCat: MetaRevenueCatClient,
         observation: MetaObservation, eligible: @escaping () -> Bool) {
        self.meta = meta
        self.revenueCat = revenueCat
        self.observation = observation
        self.eligible = eligible
    }

    func start() {
        guard !started else { return }
        started = true
        // Never retrofit onto a vendor instance whose startup privacy we did not establish.
        let alreadyConfigured = revenueCat.isConfigured
        revenueCat.startPurchases()
        guard !alreadyConfigured, eligible() else { return }
        meta.disableAutomaticCollection()
        meta.initialize()
        active = true
        refresh(activate: true)
        observation.start(foreground: { [weak self] in self?.refresh(activate: true) },
                          identityChanged: { [weak self] in self?.refresh(activate: false) })
    }

    func stop() {
        guard active else { return }
        active = false
        observation.stop()
    }

    private func refresh(activate: Bool) {
        guard active, eligible() else { return }
        let id = meta.anonymousID
        if !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            revenueCat.setAnonymousID(id)
        }
        // Missing evidence never delays purchases or invents an identifier.
        if activate { meta.activate() }
    }
}

import Foundation
