import Foundation
#if canImport(FirebaseCrashlytics)
import FirebaseCrashlytics
#endif
#if canImport(FirebasePerformance)
import FirebasePerformance
#endif

/// Does not call FirebaseApp.configure. App plist defaults must disable collection before configuration.
@MainActor
final class LiveFirebaseTelemetrySDK: FirebaseTelemetrySDK {
    func setCrashCollectionEnabled(_ enabled: Bool) {
        #if canImport(FirebaseCrashlytics)
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(enabled)
        #endif
    }

    func setPerformanceCollectionEnabled(_ enabled: Bool) {
        #if canImport(FirebasePerformance)
        Performance.sharedInstance().isDataCollectionEnabled = enabled
        #endif
    }

    func setInstrumentationEnabled(_ enabled: Bool) {
        #if canImport(FirebasePerformance)
        Performance.sharedInstance().isInstrumentationEnabled = enabled
        #endif
    }

    func deleteUnsentReports() {
        #if canImport(FirebaseCrashlytics)
        Crashlytics.crashlytics().deleteUnsentReports()
        #endif
    }

    func record(_ error: NSError) {
        #if canImport(FirebaseCrashlytics)
        Crashlytics.crashlytics().record(error: error)
        #endif
    }

    func log(_ message: String) {
        #if canImport(FirebaseCrashlytics)
        Crashlytics.crashlytics().log(message)
        #endif
    }

    func setKey(_ key: String, value: String?) {
        #if canImport(FirebaseCrashlytics)
        Crashlytics.crashlytics().setCustomValue(value, forKey: key)
        #endif
    }

    func startTrace(name: String) -> (any FirebaseTelemetryTrace)? {
        #if canImport(FirebasePerformance)
        guard let trace = Performance.startTrace(name: name) else { return nil }
        return LiveFirebaseTrace(trace: trace)
        #else
        return nil
        #endif
    }
}

#if canImport(FirebasePerformance)
@MainActor
private final class LiveFirebaseTrace: FirebaseTelemetryTrace {
    let trace: Trace
    init(trace: Trace) { self.trace = trace }
    func setValue(_ value: String, forAttribute key: String) { trace.setValue(value, forAttribute: key) }
    func stop() { trace.stop() }
}
#endif
