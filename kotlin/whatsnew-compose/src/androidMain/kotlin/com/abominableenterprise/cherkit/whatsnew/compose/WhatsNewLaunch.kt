package com.abominableenterprise.cherkit.whatsnew.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.abominableenterprise.cherkit.whatsnew.WhatsNewChecker
import com.abominableenterprise.cherkit.whatsnew.WhatsNewEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The state behind `WhatsNewPrompt(checker = ...)`, the Android twin of CherKitUI's `WhatsNewPresenter`: runs
 * `onLaunch` at most once and holds the notes until the user dismisses them, then calls `markSeen`.
 *
 * The composable uses one [shared] instance for the process, so recomposition, the root being rebuilt and activity
 * recreation (rotation) neither ask again nor lose notes the user has not dismissed yet. Call it from the main thread
 * only; `onLaunch` reads the bundled notes and preferences on [io].
 */
internal class WhatsNewLaunch(
    private val scope: CoroutineScope = MainScope(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** The notes to show; empty before `onLaunch` returns, when there is nothing new, and after the dismissal. */
    var entries: List<WhatsNewEntry> by mutableStateOf(emptyList())
        private set

    /**
     * What the launch check found: `null` until `onLaunch` returns, then whether it returned notes to show (`false`
     * for nothing new and when it failed open). Set right after [entries], with no suspension in between, so a
     * composition that sees it also sees those notes. Unlike [entries] it keeps its value after the dismissal.
     */
    var launchHadNotes: Boolean? by mutableStateOf(null)
        private set

    /** Whether `onLaunchCheckComplete` has been called: at most once per instance (per process for [shared]). */
    var launchReported: Boolean = false

    /** Whether [launched] already ran `onLaunch` (or started it). */
    var hasLaunched: Boolean = false
        private set

    /** The checker `onLaunch` ran on: only that instance knows which version to mark seen. */
    private var checker: WhatsNewChecker? = null

    /** The first call runs `checker.onLaunch(installedVersion, locales)`; later calls do nothing. */
    fun launched(checker: WhatsNewChecker, installedVersion: String, locales: List<String>) {
        if (hasLaunched) return
        hasLaunched = true
        this.checker = checker
        scope.launch {
            val found = try {
                withContext(io) { checker.onLaunch(installedVersion, locales) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The checker never throws; should it still, show nothing (fail open).
                emptyList()
            }
            entries = found
            launchHadNotes = found.isNotEmpty()
        }
    }

    /** The user dismissed the sheet: hide it and mark the notes seen, once. */
    fun dismissed() {
        if (entries.isEmpty()) return
        entries = emptyList()
        checker?.markSeen()
    }

    companion object {
        /** The instance every `WhatsNewPrompt(checker = ...)` uses. */
        val shared: WhatsNewLaunch by lazy { WhatsNewLaunch() }
    }
}
