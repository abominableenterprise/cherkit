package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.AppVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Field-level checks of the parsed config, beyond the decisions in the shared fixtures. */
class SunsetConfigParseTest {
    @Test
    fun parsesEveryField() {
        val json = """
            {"schemaVersion": 1, "ios": {"minimumVersion": "1.2", "latestVersion": "1.4.0", "latestMinimumOS": "17.0",
             "storeURL": "https://apps.apple.com/app/id1", "message": {"en": "Update", "de": "Aktualisieren"}}}
        """
        val config = assertNotNull(SunsetConfig.parse(json))
        assertNull(config.android)
        val ios = assertNotNull(config.block(SunsetPlatform.IOS))
        assertEquals(AppVersion.parse("1.2.0"), ios.minimumVersion)
        assertEquals(AppVersion.parse("1.4"), ios.latestVersion)
        assertEquals(AppVersion.parse("17"), ios.latestMinimumOS)
        assertEquals("https://apps.apple.com/app/id1", ios.storeUrl)
        assertEquals(mapOf("en" to "Update", "de" to "Aktualisieren"), ios.messages)
    }

    @Test
    fun invalidBlockDoesNotDropTheOtherPlatform() {
        val json = """
            {"schemaVersion": 1, "ios": {"minimumVersion": "x", "storeURL": "https://a.example"},
             "android": {"minimumVersion": "2", "storeURL": "https://b.example/p?id=1"}}
        """
        val config = assertNotNull(SunsetConfig.parse(json))
        assertNull(config.ios)
        assertEquals(AppVersion.parse("2"), config.android?.minimumVersion)
    }

    @Test
    fun schemaVersionMustBeExactlyOne() {
        assertNotNull(SunsetConfig.parse("""{"schemaVersion": 1.0}"""))
        assertNull(SunsetConfig.parse("""{"schemaVersion": 1.5}"""))
        assertNull(SunsetConfig.parse("""{"schemaVersion": 0}"""))
        assertNull(SunsetConfig.parse("""{"schemaVersion": null}"""))
    }

    @Test
    fun promptAccessor() {
        val prompt = SunsetPrompt("https://a.example", null)
        assertNull(SunsetDecision.UpToDate.prompt)
        assertEquals(prompt, SunsetDecision.SoftUpdate(prompt).prompt)
        assertEquals(prompt, SunsetDecision.HardUpdate(prompt).prompt)
    }
}
