package com.abominableenterprise.cherkit.sunset.compose

import com.abominableenterprise.cherkit.sunset.SunsetPrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SunsetStringsTest {
    private fun prompt(message: String?) = SunsetPrompt("https://play.google.com/store/apps/details?id=a", message)

    @Test
    fun defaultsMatchIos() {
        val strings = SunsetStrings()
        assertEquals("Update available", strings.softTitle)
        assertEquals("A new version of the app is ready, with the latest improvements and fixes.", strings.softMessage)
        assertEquals("Update required", strings.hardTitle)
        assertEquals("This version is no longer supported. Please update to keep using the app.", strings.hardMessage)
        assertEquals("Update", strings.updateButton)
        assertEquals("Not now", strings.notNowButton)
    }

    @Test
    fun customMessageWinsUnlessBlank() {
        val strings = SunsetStrings(softMessage = "soft", hardMessage = "hard")
        assertEquals("Please update.", strings.softText(prompt("Please update.")))
        assertEquals("Please update.", strings.hardText(prompt("Please update.")))
        for (missing in listOf(null, "", "  \n\t")) {
            assertEquals("soft", strings.softText(prompt(missing)))
            assertEquals("hard", strings.hardText(prompt(missing)))
        }
    }

    @Test
    fun neverTellsUsersToForceQuit() {
        val s = SunsetStrings()
        for (text in listOf(s.softTitle, s.softMessage, s.hardTitle, s.hardMessage, s.updateButton, s.notNowButton)) {
            assertFalse(text.contains("force", ignoreCase = true), text)
        }
    }
}
