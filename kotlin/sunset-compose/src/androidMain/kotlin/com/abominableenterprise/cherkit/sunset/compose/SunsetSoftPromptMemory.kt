package com.abominableenterprise.cherkit.sunset.compose

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.abominableenterprise.cherkit.sunset.AndroidSunset
import com.abominableenterprise.cherkit.sunset.SunsetDecision

/**
 * Remembers the version the user tapped "Not now" for, so the soft prompt is not shown again for that version.
 *
 * Stores one string under [key] in [prefs]. Pass your own SharedPreferences (or a throwaway one in tests) or key.
 */
public class SunsetSoftPromptMemory(
    /** Where the dismissed version is stored. */
    public val prefs: SharedPreferences,
    /** The key it is stored under. */
    public val key: String = DEFAULT_KEY,
) {
    /** The version the user last tapped "Not now" for, or `null` when never (or when another type is stored). */
    public val dismissedVersion: String?
        get() = try {
            prefs.getString(key, null)
        } catch (_: ClassCastException) {
            null
        }

    /** Records "Not now" for [version]. Any other version shows the prompt again. */
    public fun rememberNotNow(version: String) {
        prefs.edit().putString(key, version).apply()
    }

    /** Forgets any "Not now", e.g. from a debug menu. */
    public fun reset() {
        prefs.edit().remove(key).apply()
    }

    /** Whether the soft prompt should appear for [decision] about [version]. See [SunsetSoftPromptRule]. */
    public fun shouldShowSoftPrompt(decision: SunsetDecision?, version: String): Boolean =
        SunsetSoftPromptRule.shouldShow(decision, version, dismissedVersion)

    public companion object {
        /** The key used unless you pass another; the same as iOS's `SunsetSoftPromptMemory.defaultKey`. */
        public const val DEFAULT_KEY: String = "cherkit.sunset.softPromptDismissedVersion"

        /** A memory in the SharedPreferences file [name] of [context] (by default the Sunset module's own file). */
        public fun from(
            context: Context,
            name: String = AndroidSunset.DEFAULT_PREFERENCES_NAME,
            key: String = DEFAULT_KEY,
        ): SunsetSoftPromptMemory =
            SunsetSoftPromptMemory(context.getSharedPreferences(name, Context.MODE_PRIVATE), key)
    }
}

/** [SunsetSoftPromptMemory.from] for the current context, kept across recompositions. */
@Composable
public fun rememberSunsetSoftPromptMemory(): SunsetSoftPromptMemory {
    val context = LocalContext.current
    return remember(context) { SunsetSoftPromptMemory.from(context) }
}
