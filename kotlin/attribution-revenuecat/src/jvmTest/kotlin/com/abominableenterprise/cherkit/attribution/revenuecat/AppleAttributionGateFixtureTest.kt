package com.abominableenterprise.cherkit.attribution.revenuecat

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppleAttributionGateFixtureTest {
    @Test fun sharedGates() {
        val root = requireNotNull(System.getProperty("cherkit.fixtures"))
        val cases = Json.parseToJsonElement(File(root, "attribution/gates.json").readText()).jsonArray
        assertTrue(cases.isNotEmpty())
        for (element in cases) {
            val case = element.jsonObject
            fun text(key: String): String = requireNotNull(case[key]?.jsonPrimitive?.contentOrNull)
            fun flag(key: String): Boolean = requireNotNull(case[key]).jsonPrimitive.boolean
            val platform = when (val value = text("platform")) {
                "iOS" -> AttributionPlatform.IOS
                "android" -> AttributionPlatform.Android
                "macOS" -> AttributionPlatform.MacOS
                else -> error("Unknown fixture platform: $value")
            }
            val audience = when (val value = text("audience")) {
                "adultOnly" -> Audience.AdultOnly
                "restrictedOrSwitchable" -> Audience.RestrictedOrSwitchable
                else -> error("Unknown fixture audience: $value")
            }
            assertEquals(
                flag("enableAppleAttribution"),
                shouldEnableAppleAttribution(
                    platform, audience, flag("isDebug"), flag("isTestHost"), flag("isSimulator"),
                ),
                text("name"),
            )
        }
    }
}
