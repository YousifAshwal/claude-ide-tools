package com.igorlink.claudejetbrainstools

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Smoke test to verify test infrastructure is working.
 */
class SmokeTest {

    @Test
    fun `test infrastructure works`() {
        assertTrue(true, "Test infrastructure should be working")
    }

    @Test
    fun `kotlin version is correct`() {
        val version = KotlinVersion.CURRENT
        assertTrue(version.major >= 1 && version.minor >= 9, "Kotlin version should be 1.9+")
    }
}
