package com.abominableenterprise.cherkit.update.compose

/** An [UpdatePromptStore] that forgets on process death, for tests and previews. */
public class InMemoryUpdatePromptStore(override var declinedVersionCode: Int? = null) : UpdatePromptStore {
    override fun rememberDeclined(versionCode: Int) {
        declinedVersionCode = versionCode
    }
}
