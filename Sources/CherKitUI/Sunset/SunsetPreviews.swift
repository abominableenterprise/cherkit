#if canImport(SwiftUI) && DEBUG
import CherKitSunset
import SwiftUI

@available(iOS 16, macOS 13, *)
private enum SunsetPreviewData {
    static let storeURL = URL(string: "https://apps.apple.com/app/id123456789")!
    static let prompt = SunsetPrompt(storeURL: storeURL, message: nil)
    static let longPrompt = SunsetPrompt(
        storeURL: storeURL,
        message: "We rebuilt syncing from the ground up, so your data now stays in step across all your devices. "
            + "This version can no longer talk to the new sync service. Updating takes a minute, and everything "
            + "you have saved stays right where it is. Thanks for sticking with us!"
    )
    static let icon = Image(systemName: "app.badge.fill")

    /// A throwaway suite so previews never touch the app's real "Not now" memory.
    static var memory: SunsetSoftPromptMemory {
        let defaults = UserDefaults(suiteName: "cherkit.sunset.previews") ?? .standard
        let memory = SunsetSoftPromptMemory(defaults: defaults)
        memory.reset()
        return memory
    }
}

@available(iOS 16, macOS 13, *)
private struct SunsetPromptPreviewHost: View {
    @State var decision: SunsetDecision?

    var body: some View {
        VStack(spacing: 16) {
            Text("Your app")
                .font(.title)
            Button("Show again") { decision = .softUpdate(SunsetPreviewData.prompt) }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .sunsetPrompt(decision: $decision, latestVersion: "2.0", memory: SunsetPreviewData.memory)
    }
}

@available(iOS 17, macOS 14, *)
#Preview("Soft prompt") {
    SunsetPromptPreviewHost(decision: .softUpdate(SunsetPreviewData.prompt))
}

@available(iOS 17, macOS 14, *)
#Preview("Hard block") {
    SunsetUpdateView(prompt: SunsetPreviewData.prompt, appIcon: SunsetPreviewData.icon)
}

@available(iOS 17, macOS 14, *)
#Preview("Hard block, tinted") {
    SunsetUpdateView(prompt: SunsetPreviewData.prompt, appIcon: SunsetPreviewData.icon, tint: .orange)
}

@available(iOS 17, macOS 14, *)
#Preview("Hard block, bright tint (black label)") {
    SunsetUpdateView(prompt: SunsetPreviewData.prompt, appIcon: SunsetPreviewData.icon, tint: Color(red: 1, green: 0.9, blue: 0.5))
        .preferredColorScheme(.dark)
}

@available(iOS 17, macOS 14, *)
#Preview("Hard block via modifier") {
    SunsetPromptPreviewHost(decision: .hardUpdate(SunsetPreviewData.prompt))
}

@available(iOS 17, macOS 14, *)
#Preview("Long message") {
    SunsetUpdateView(prompt: SunsetPreviewData.longPrompt, appIcon: SunsetPreviewData.icon)
}

@available(iOS 17, macOS 14, *)
#Preview("Largest Dynamic Type") {
    SunsetUpdateView(prompt: SunsetPreviewData.longPrompt, appIcon: SunsetPreviewData.icon)
        .dynamicTypeSize(.accessibility5)
}
#endif
