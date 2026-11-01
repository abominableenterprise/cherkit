import CherKitCore
import Foundation

/// One launch's inputs to ``SunsetDecider``, with each rule as its own small step.
struct SunsetDecisionInputs {
    let block: SunsetPlatformConfig?
    let installed: AppVersion
    let os: AppVersion?
    let languages: [String]
    let channel: SunsetChannel
    /// `nil` unless the channel is ``SunsetChannel/store``.
    let lookup: SunsetLookupResult?

    /// The block, when its `latestMinimumOS` (if any) lets this device install its versions.
    private var usableBlock: SunsetPlatformConfig? {
        guard let block else { return nil }
        guard let minimumOS = block.latestMinimumOS else { return block }
        guard let os, !(os < minimumOS) else { return nil }
        return block
    }

    /// The lookup, when the device meets its `minimumOsVersion` and some https link can open the store.
    private var usableLookup: SunsetLookupResult? {
        guard let lookup, let os, !(os < lookup.minimumOSVersion), (block?.storeURL ?? lookup.trackViewURL) != nil
        else { return nil }
        return lookup
    }

    /// The newest release version offered: the higher of the usable config `latestVersion` and lookup version.
    private var latestVersion: AppVersion? {
        let versions = [usableBlock?.latestVersion, usableLookup?.version].compactMap { $0 }
        return versions.max()
    }

    private func updatePrompt() -> SunsetPrompt? {
        guard let url = block?.storeURL ?? usableLookup?.trackViewURL else { return nil }
        let message = SunsetMessages.resolve(block?.messages ?? [:], languages: languages)
        return SunsetPrompt(storeURL: url, message: message, latestVersion: latestVersion)
    }

    /// A hard block from the config's `minimumVersion` alone.
    func hardUpdate() -> SunsetDecision? {
        guard let minimum = usableBlock?.minimumVersion, installed < minimum, let prompt = updatePrompt() else { return nil }
        return .hardUpdate(prompt)
    }

    /// A soft release prompt from the config's or the lookup's version.
    func softUpdate() -> SunsetDecision? {
        guard let latest = latestVersion, installed < latest, let prompt = updatePrompt() else { return nil }
        return .softUpdate(prompt)
    }

    /// A soft test-build prompt from the block's `testFlight` object.
    func testBuild(installedBuild: String?, now: Date) -> SunsetDecision? {
        guard let testFlight = block?.testFlight,
              let build = installedBuild.flatMap(SunsetBuildNumber.init), build < testFlight.latestBuild
        else { return nil }
        if let notBefore = testFlight.notBefore, now < notBefore { return nil }
        let prompt = SunsetPrompt(
            storeURL: testFlight.url ?? SunsetTestFlightConfig.defaultURL,
            message: SunsetMessages.resolve(testFlight.messages, languages: languages),
            kind: .testBuild,
            latestBuild: testFlight.latestBuild
        )
        return .softUpdate(prompt)
    }
}
