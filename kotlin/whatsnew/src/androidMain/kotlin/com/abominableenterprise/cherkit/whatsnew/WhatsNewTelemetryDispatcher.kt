package com.abominableenterprise.cherkit.whatsnew

import android.os.Handler
import android.os.Looper

internal actual fun reportWhatsNewTelemetry(report: () -> Unit) {
    // Plain host tests have no Android looper; a real Android process always has a main looper.
    val main = runCatching { Looper.getMainLooper() }.getOrNull()
    if (main == null || Looper.myLooper() == main) {
        runCatching(report)
    } else {
        // Never block notes loading or carry an exception/notes payload across this boundary.
        Handler(main).post { runCatching(report) }
    }
}
