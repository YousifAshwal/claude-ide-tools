package com.igorlink.claudeidetools.util

import com.intellij.openapi.application.ApplicationInfo
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for IdeDetector and JetBrainsIde enum.
 * Tests the IDE detection logic including:
 * - JetBrainsIde enum properties and uniqueness
 * - IDE detection based on ApplicationInfo
 * - Caching behavior
 * - Convenience methods
 */
class IdeDetectorTest {

    private lateinit var mockApplicationInfo: ApplicationInfo

    @BeforeEach
    fun setUp() {
        mockApplicationInfo = mockk(relaxed = true)

        mockkStatic(ApplicationInfo::class)
        every { ApplicationInfo.getInstance() } returns mockApplicationInfo

        // Clear cache before each test to ensure fresh detection
        IdeDetector.clearCache()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        IdeDetector.clearCache()
    }

    // ==================== JetBrainsIde Enum Tests ====================

    @Nested
    inner class JetBrainsIdeEnumTests {

        @Test
        fun `enum contains all expected IDEs`() {
            val expectedIdes = setOf(
                "IDEA", "WEBSTORM", "PYCHARM", "GOLAND", "PHPSTORM",
                "RUBYMINE", "CLION", "RIDER", "DATAGRIP", "ANDROID_STUDIO",
                "RUSTROVER", "AQUA", "DATASPELL", "UNKNOWN"
            )

            val actualIdes = JetBrainsIde.entries.map { it.name }.toSet()

            assertEquals(expectedIdes, actualIdes)
        }

        @Test
        fun `each IDE has unique port`() {
            val ports = JetBrainsIde.entries.map { it.port }
            val uniquePorts = ports.toSet()

            assertEquals(ports.size, uniquePorts.size, "All IDEs should have unique ports")
        }

        @Test
        fun `ports are in expected range`() {
            JetBrainsIde.entries.forEach { ide ->
                assertTrue(ide.port in 8765..8780, "${ide.name} port ${ide.port} should be in range 8765-8780")
            }
        }

        @Test
        fun `mcpServerName format is correct`() {
            JetBrainsIde.entries.forEach { ide ->
                val expected = "claude-${ide.shortName}-tools"
                assertEquals(expected, ide.mcpServerName, "mcpServerName for ${ide.name} should follow format")
            }
        }

        @Test
        fun `installDirName format is correct`() {
            JetBrainsIde.entries.forEach { ide ->
                val expected = ".claude-${ide.shortName}-tools"
                assertEquals(expected, ide.installDirName, "installDirName for ${ide.name} should follow format")
            }
        }

        @Test
        fun `IDEA has expected properties`() {
            assertEquals("IntelliJ IDEA", JetBrainsIde.IDEA.displayName)
            assertEquals("idea", JetBrainsIde.IDEA.shortName)
            assertEquals(8765, JetBrainsIde.IDEA.port)
            assertEquals("claude-idea-tools", JetBrainsIde.IDEA.mcpServerName)
            assertEquals(".claude-idea-tools", JetBrainsIde.IDEA.installDirName)
        }

        @Test
        fun `WEBSTORM has expected properties`() {
            assertEquals("WebStorm", JetBrainsIde.WEBSTORM.displayName)
            assertEquals("webstorm", JetBrainsIde.WEBSTORM.shortName)
            assertEquals(8766, JetBrainsIde.WEBSTORM.port)
        }

        @Test
        fun `ANDROID_STUDIO has expected properties`() {
            assertEquals("Android Studio", JetBrainsIde.ANDROID_STUDIO.displayName)
            assertEquals("android-studio", JetBrainsIde.ANDROID_STUDIO.shortName)
            assertEquals(8774, JetBrainsIde.ANDROID_STUDIO.port)
        }

        @Test
        fun `UNKNOWN has expected properties`() {
            assertEquals("Unknown IDE", JetBrainsIde.UNKNOWN.displayName)
            assertEquals("unknown", JetBrainsIde.UNKNOWN.shortName)
            assertEquals(8780, JetBrainsIde.UNKNOWN.port)
        }
    }

    // ==================== detect() Tests ====================

    @Nested
    inner class DetectMethodTests {

        @Test
        fun `detects IntelliJ IDEA from product name`() {
            setupApplicationInfo("IntelliJ IDEA 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.IDEA, result)
        }

        @Test
        fun `detects IntelliJ IDEA from version name`() {
            setupApplicationInfo("Some Product", "IDEA Ultimate")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.IDEA, result)
        }

        @Test
        fun `detects WebStorm from product name`() {
            setupApplicationInfo("WebStorm 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.WEBSTORM, result)
        }

        @Test
        fun `detects WebStorm from version name`() {
            setupApplicationInfo("Some Product", "WebStorm")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.WEBSTORM, result)
        }

        @Test
        fun `detects PyCharm from product name`() {
            setupApplicationInfo("PyCharm Professional 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.PYCHARM, result)
        }

        @Test
        fun `detects PyCharm from version name`() {
            setupApplicationInfo("Some Product", "PyCharm")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.PYCHARM, result)
        }

        @Test
        fun `detects GoLand from product name`() {
            setupApplicationInfo("GoLand 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.GOLAND, result)
        }

        @Test
        fun `detects GoLand from version name`() {
            setupApplicationInfo("Some Product", "GoLand")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.GOLAND, result)
        }

        @Test
        fun `detects PhpStorm from product name`() {
            setupApplicationInfo("PhpStorm 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.PHPSTORM, result)
        }

        @Test
        fun `detects RubyMine from product name`() {
            setupApplicationInfo("RubyMine 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.RUBYMINE, result)
        }

        @Test
        fun `detects CLion from product name`() {
            setupApplicationInfo("CLion 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.CLION, result)
        }

        @Test
        fun `detects Rider from product name`() {
            setupApplicationInfo("Rider 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.RIDER, result)
        }

        @Test
        fun `detects DataGrip from product name`() {
            setupApplicationInfo("DataGrip 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.DATAGRIP, result)
        }

        @Test
        fun `detects DataSpell from product name`() {
            setupApplicationInfo("DataSpell 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.DATASPELL, result)
        }

        @Test
        fun `detects Aqua from product name`() {
            setupApplicationInfo("Aqua 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.AQUA, result)
        }

        @Test
        fun `detects RustRover from product name`() {
            setupApplicationInfo("RustRover 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.RUSTROVER, result)
        }

        @Test
        fun `detects RustRover from version name`() {
            setupApplicationInfo("Some Product", "RustRover")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.RUSTROVER, result)
        }

        @Test
        fun `detects Android Studio from product name`() {
            setupApplicationInfo("Android Studio Hedgehog", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.ANDROID_STUDIO, result)
        }

        @Test
        fun `detects Android Studio from version name containing android`() {
            setupApplicationInfo("Some IDE", "Android")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.ANDROID_STUDIO, result)
        }

        @Test
        fun `Android Studio detected before generic studio match`() {
            // This test ensures Android Studio is checked first since its name contains "Studio"
            // which might conflict with other checks
            setupApplicationInfo("Android Studio", "Android")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.ANDROID_STUDIO, result)
        }

        @Test
        fun `returns UNKNOWN for unrecognized IDE`() {
            setupApplicationInfo("Totally Unknown IDE", "Something Else")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.UNKNOWN, result)
        }

        @Test
        fun `detection is case insensitive`() {
            setupApplicationInfo("WEBSTORM 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.WEBSTORM, result)
        }

        @Test
        fun `detection works with mixed case`() {
            setupApplicationInfo("wEbStOrM 2024.1", "")

            val result = IdeDetector.detect()

            assertEquals(JetBrainsIde.WEBSTORM, result)
        }
    }

    // ==================== Caching Tests ====================

    @Nested
    inner class CachingTests {

        @Test
        fun `second call returns cached result without calling ApplicationInfo again`() {
            setupApplicationInfo("IntelliJ IDEA 2024.1", "")

            // First call
            val result1 = IdeDetector.detect()

            // Verify ApplicationInfo was called
            verify(exactly = 1) { ApplicationInfo.getInstance() }

            // Second call
            val result2 = IdeDetector.detect()

            // Verify ApplicationInfo was NOT called again
            verify(exactly = 1) { ApplicationInfo.getInstance() }

            // Results should be the same
            assertEquals(result1, result2)
            assertEquals(JetBrainsIde.IDEA, result1)
        }

        @Test
        fun `clearCache allows fresh detection`() {
            // First detection - IDEA
            setupApplicationInfo("IntelliJ IDEA 2024.1", "")
            val result1 = IdeDetector.detect()
            assertEquals(JetBrainsIde.IDEA, result1)

            verify(exactly = 1) { ApplicationInfo.getInstance() }

            // Clear cache
            IdeDetector.clearCache()

            // Change mock to return different IDE
            setupApplicationInfo("WebStorm 2024.1", "")

            // Second detection - should get new value
            val result2 = IdeDetector.detect()
            assertEquals(JetBrainsIde.WEBSTORM, result2)

            // ApplicationInfo should have been called twice total
            verify(exactly = 2) { ApplicationInfo.getInstance() }
        }

        @Test
        fun `cached result persists across multiple calls`() {
            setupApplicationInfo("PyCharm 2024.1", "")

            repeat(10) {
                val result = IdeDetector.detect()
                assertEquals(JetBrainsIde.PYCHARM, result)
            }

            // ApplicationInfo should only have been called once
            verify(exactly = 1) { ApplicationInfo.getInstance() }
        }
    }

    // ==================== Convenience Methods Tests ====================

    @Nested
    inner class ConvenienceMethodsTests {

        @Test
        fun `getPort returns correct port for detected IDE`() {
            setupApplicationInfo("IntelliJ IDEA 2024.1", "")

            val port = IdeDetector.getPort()

            assertEquals(8765, port)
        }

        @Test
        fun `getPort returns correct port for different IDEs`() {
            setupApplicationInfo("WebStorm 2024.1", "")

            assertEquals(8766, IdeDetector.getPort())
        }

        @Test
        fun `getMcpServerName returns correct name`() {
            setupApplicationInfo("IntelliJ IDEA 2024.1", "")

            val name = IdeDetector.getMcpServerName()

            assertEquals("claude-idea-tools", name)
        }

        @Test
        fun `getMcpServerName returns correct name for different IDEs`() {
            setupApplicationInfo("PyCharm 2024.1", "")

            assertEquals("claude-pycharm-tools", IdeDetector.getMcpServerName())
        }

        @Test
        fun `getDisplayName returns correct name`() {
            setupApplicationInfo("IntelliJ IDEA 2024.1", "")

            val displayName = IdeDetector.getDisplayName()

            assertEquals("IntelliJ IDEA", displayName)
        }

        @Test
        fun `getDisplayName returns correct name for different IDEs`() {
            setupApplicationInfo("GoLand 2024.1", "")

            assertEquals("GoLand", IdeDetector.getDisplayName())
        }

        @Test
        fun `convenience methods use cached detection`() {
            setupApplicationInfo("Rider 2024.1", "")

            // Call all convenience methods
            IdeDetector.getPort()
            IdeDetector.getMcpServerName()
            IdeDetector.getDisplayName()
            IdeDetector.detect()

            // ApplicationInfo should only have been called once
            verify(exactly = 1) { ApplicationInfo.getInstance() }
        }

        @Test
        fun `convenience methods return consistent values`() {
            setupApplicationInfo("CLion 2024.1", "")

            val ide = IdeDetector.detect()

            assertEquals(ide.port, IdeDetector.getPort())
            assertEquals(ide.mcpServerName, IdeDetector.getMcpServerName())
            assertEquals(ide.displayName, IdeDetector.getDisplayName())
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Sets up mockApplicationInfo to return the specified product and version names
     */
    private fun setupApplicationInfo(fullApplicationName: String, versionName: String) {
        every { mockApplicationInfo.fullApplicationName } returns fullApplicationName
        every { mockApplicationInfo.versionName } returns versionName
    }
}
