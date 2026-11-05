package com.abominableenterprise.cherkit.attribution.meta

import com.abominableenterprise.cherkit.attribution.revenuecat.Audience
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetaGateFixtureTest {
    @Test fun sharedGates() {
        val cases = Json.parseToJsonElement(File(
            requireNotNull(System.getProperty("cherkit.fixtures")), "attribution/meta-gates.json",
        ).readText()).jsonArray
        assertTrue(cases.isNotEmpty())
        for (element in cases) {
            val case = element.jsonObject
            fun text(key: String): String = requireNotNull(case[key]).jsonPrimitive.content
            fun flag(key: String): Boolean = requireNotNull(case[key]).jsonPrimitive.boolean
            val audience = when (text("audience")) {
                "adultOnly" -> Audience.AdultOnly
                "restrictedOrSwitchable" -> Audience.RestrictedOrSwitchable
                else -> error("Unknown audience")
            }
            assertEquals(flag("enableMetaAttribution"), shouldEnableMetaAttribution(
                text("platform"), audience, flag("isDebug"), flag("isTestHost"), flag("isSimulator"),
                flag("hasMetaConfiguration"),
            ), text("name"))
        }
    }
}
