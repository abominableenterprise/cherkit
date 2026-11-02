package com.abominableenterprise.cherkit.update.compose

import com.abominableenterprise.cherkit.core.TelemetrySink

/**
 * The once-per-session gate: after the update sheet started once, [UpdateLauncher] never starts it again. A session
 * is the process: [shared] lives as long as it, so recomposition, rotation and returning from the background do not
 * ask again. Tests pass their own instance.
 */
public class UpdateSession {
    private var downloadRecorded = false

    internal fun recordDownloaded(sink: TelemetrySink?) {
        if (downloadRecorded) return
        downloadRecorded = true
        UpdateTelemetry.breadcrumb(sink, "downloaded")
    }

    /** The versionCode the sheet started for this session, or `null` before. */
    public var promptedVersionCode: Int? = null
        private set

    /** Whether the sheet already started this session. */
    public val prompted: Boolean get() = promptedVersionCode != null

    /** Records that the sheet started for [versionCode]. */
    public fun markPrompted(versionCode: Int) {
        promptedVersionCode = versionCode
    }

    public companion object {
        /** The process-wide session `PlayUpdatePrompt` uses. */
        public val shared: UpdateSession = UpdateSession()
    }
}
