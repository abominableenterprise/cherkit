package com.abominableenterprise.cherkit.whatsnew

import android.content.Context
import android.content.res.AssetManager
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidWhatsNewSourceCompatibilityTest {
    @Test
    fun originalPositionalAndCurrentNamedCallbacksCompile() {
        // Compile the Android wiring without executing android.jar stubs.
        val checkers: List<(Context, SkipHandler?) -> WhatsNewChecker> = listOf(
            { context, onSkip -> AndroidWhatsNew.checker(context, onSkip) },
            { context, onSkip -> AndroidWhatsNew.checker(context, onSkip = onSkip) },
            { context, onSkip -> AndroidWhatsNew.checker(context, telemetrySink = null, onSkip = onSkip) },
            { context, _ -> AndroidWhatsNew.checker(context) { _, _ -> } },
        )
        val sources: List<(AssetManager, SkipHandler?) -> ReleaseNotesSource> = listOf(
            { assets, onSkip -> AndroidWhatsNew.assetsSource(assets, "whatsnew", onSkip) },
            { assets, onSkip -> AndroidWhatsNew.assetsSource(assets, onSkip = onSkip) },
            { assets, onSkip -> AndroidWhatsNew.assetsSource(assets, telemetrySink = null, onSkip = onSkip) },
            { assets, _ -> AndroidWhatsNew.assetsSource(assets, "whatsnew") { _, _ -> } },
        )
        assertEquals(4, checkers.size)
        assertEquals(4, sources.size)
    }
}
