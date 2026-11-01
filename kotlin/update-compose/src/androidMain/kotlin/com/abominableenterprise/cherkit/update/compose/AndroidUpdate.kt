package com.abominableenterprise.cherkit.update.compose

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Android wiring for the update prompt: where "Not now" lives and whether it runs by default. */
public object AndroidUpdate {
    /** Default SharedPreferences file name. */
    public const val DEFAULT_PREFERENCES_NAME: String = "cherkit.update"

    /** A [SharedPreferencesUpdatePromptStore] in the SharedPreferences file [name] of [context]. */
    public fun promptStore(
        context: Context,
        name: String = DEFAULT_PREFERENCES_NAME,
        key: String = SharedPreferencesUpdatePromptStore.DEFAULT_KEY,
    ): SharedPreferencesUpdatePromptStore =
        SharedPreferencesUpdatePromptStore(context.getSharedPreferences(name, Context.MODE_PRIVATE), key)

    /** `PlayUpdatePrompt`'s default for `enabled`: off in debuggable builds. */
    public fun isEnabledByDefault(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0
}

/** [AndroidUpdate.promptStore] for the current context, kept across recompositions. */
@Composable
public fun rememberUpdatePromptStore(): UpdatePromptStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidUpdate.promptStore(context) }
}
