package com.abominableenterprise.cherkit.sunset

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs every case in `fixtures/sunset/message.json`; Swift's `SunsetMessagesFixtureTests` runs the same cases. */
class SunsetMessagesFixtureTest {
    @Serializable
    private data class MessageFixture(val cases: List<MessageCase>)

    @Serializable
    private data class MessageCase(
        val name: String,
        val messages: Map<String, String>,
        val languages: List<String>,
        val expected: String?,
    )

    @Test
    fun messageCases() {
        val cases = SunsetFixtures.json.decodeFromString<MessageFixture>(SunsetFixtures.text("message.json")).cases
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            assertEquals(case.expected, SunsetMessages.resolve(case.messages, case.languages), case.name)
        }
    }
}
