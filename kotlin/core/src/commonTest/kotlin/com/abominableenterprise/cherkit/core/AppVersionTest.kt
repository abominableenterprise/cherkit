package com.abominableenterprise.cherkit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Smoke checks on every target (JVM and Android host); the full contract runs in jvmTest from the fixtures. */
class AppVersionTest {
    @Test
    fun parsesAndComparesStoreVersions() {
        val old = AppVersion.parse("v1.2 (45)")!!
        val new = AppVersion.parse("1.10.0-beta")!!
        assertEquals(listOf(1, 2), old.components)
        assertTrue(old < new)
        assertEquals(AppVersion.parse("1.2.0"), old)
        assertEquals(listOf(old, new), listOf(new, old).sorted())
    }

    @Test
    fun malformedInputIsNull() {
        assertNull(AppVersion.parse("1..2"))
        assertNull(AppVersion.parse("-1"))
    }
}
