package com.abominableenterprise.cherkit.review

/**
 * [ReviewStore] over a [ReviewKeyValueStorage], under `<keyPrefix>successCount` (Int), `<keyPrefix>isDue` (Boolean)
 * and `<keyPrefix>lastPromptedVersion` (String). A value of the wrong type counts as absent.
 */
public class KeyValueReviewStore(
    private val storage: ReviewKeyValueStorage,
    private val keyPrefix: String = DEFAULT_KEY_PREFIX,
) : ReviewStore {
    private val countKey get() = keyPrefix + "successCount"
    private val dueKey get() = keyPrefix + "isDue"
    private val versionKey get() = keyPrefix + "lastPromptedVersion"

    override fun load(): ReviewState = ReviewState(
        successCount = storage.getInt(countKey) ?: 0,
        isDue = storage.getBoolean(dueKey) ?: false,
        lastPromptedVersion = storage.getString(versionKey),
    )

    override fun save(state: ReviewState) {
        storage.put(
            mapOf(
                countKey to state.successCount,
                dueKey to state.isDue,
                versionKey to state.lastPromptedVersion,
            ),
        )
    }

    public companion object {
        /** The default key prefix. */
        public const val DEFAULT_KEY_PREFIX: String = "cherkit.review."
    }
}
