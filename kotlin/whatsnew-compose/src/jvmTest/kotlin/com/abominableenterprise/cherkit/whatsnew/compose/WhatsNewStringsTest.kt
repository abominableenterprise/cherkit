package com.abominableenterprise.cherkit.whatsnew.compose

import kotlin.test.Test
import kotlin.test.assertEquals

class WhatsNewStringsTest {
    @Test
    fun defaultsMatchIos() {
        val strings = WhatsNewStrings()
        assertEquals("What's New", strings.title)
        assertEquals("Continue", strings.continueButton)
        assertEquals("Version 1.4.0", strings.versionTitle("1.4.0"))
        assertEquals("Version History", strings.historyTitle)
        assertEquals("You're on this version", strings.installedBadge)
        assertEquals("Bug fixes and improvements.", strings.noNotes)
        assertEquals("No release notes yet.", strings.emptyHistory)
    }

    @Test
    fun overridesKeepTheOtherDefaults() {
        val strings = WhatsNewStrings(title = "Neuigkeiten", versionTitle = { "Version $it (de)" })
        assertEquals("Neuigkeiten", strings.title)
        assertEquals("Version 2 (de)", strings.versionTitle("2"))
        assertEquals("Continue", strings.continueButton)
        assertEquals(WhatsNewStrings(), WhatsNewStrings())
    }
}
