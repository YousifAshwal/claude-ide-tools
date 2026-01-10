package com.igorlink.claudejetbrainstools.util

import com.intellij.openapi.diagnostic.Logger
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for PluginAvailability.
 * Tests the plugin availability checking functionality including:
 * - isAvailable() method for different language types
 * - Caching behavior
 * - Class path mappings for all supported languages
 *
 * Uses [ClassLoadingStrategy] injection for reliable testing without
 * mocking core JDK classes.
 */
class PluginAvailabilityTest {

    private lateinit var mockLogger: Logger
    private lateinit var mockClassLoadingStrategy: ClassLoadingStrategy

    @BeforeEach
    fun setUp() {
        // Clear cache before each test to ensure isolated state
        PluginAvailability.clearCache()

        mockLogger = mockk(relaxed = true)

        // Mock Logger to prevent IntelliJ TestLoggerFactory from failing tests on logged messages
        mockkStatic(Logger::class)
        every { Logger.getInstance(any<Class<*>>()) } returns mockLogger
        every { Logger.getInstance(any<String>()) } returns mockLogger

        // Create mock class loading strategy
        mockClassLoadingStrategy = mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        // Clear cache after each test for clean state (also resets strategy)
        PluginAvailability.clearCache()
    }

    // ==================== isAvailable() Tests ====================

    @Nested
    inner class IsAvailableTests {

        @Test
        fun `isAvailable returns true for JAVA without checking class (always available)`() {
            // JAVA is always available in IntelliJ-based IDEs - no class loading needed
            val result = PluginAvailability.isAvailable(SupportedLanguage.JAVA)

            assertTrue(result, "JAVA should always be available")
        }

        @Test
        fun `isAvailable returns false for UNKNOWN language`() {
            // UNKNOWN is never supported
            val result = PluginAvailability.isAvailable(SupportedLanguage.UNKNOWN)

            assertFalse(result, "UNKNOWN language should never be available")
        }

        @Test
        fun `isAvailable returns true when plugin class exists`() {
            // Arrange - mock strategy to return successfully for Kotlin
            every { mockClassLoadingStrategy.loadClass("org.jetbrains.kotlin.psi.KtFile") } returns Any::class.java
            PluginAvailability.setClassLoadingStrategy(mockClassLoadingStrategy)

            // Act
            val result = PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)

            // Assert
            assertTrue(result, "Should return true when plugin class exists")

            // Verify loadClass was called
            verify { mockClassLoadingStrategy.loadClass("org.jetbrains.kotlin.psi.KtFile") }
        }

        @Test
        fun `isAvailable returns false when plugin class does not exist`() {
            // Arrange - mock strategy to throw ClassNotFoundException
            every { mockClassLoadingStrategy.loadClass("org.jetbrains.kotlin.psi.KtFile") } throws ClassNotFoundException("Class not found")
            PluginAvailability.setClassLoadingStrategy(mockClassLoadingStrategy)

            // Act
            val result = PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)

            // Assert
            assertFalse(result, "Should return false when plugin class is not found")
        }

        @Test
        fun `isAvailable returns false when loadClass throws generic exception`() {
            // Arrange - mock strategy to throw a generic exception
            every { mockClassLoadingStrategy.loadClass("org.jetbrains.kotlin.psi.KtFile") } throws RuntimeException("Unexpected error")
            PluginAvailability.setClassLoadingStrategy(mockClassLoadingStrategy)

            // Act
            val result = PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)

            // Assert
            assertFalse(result, "Should return false when any exception occurs")
        }
    }

    // ==================== Caching Tests ====================

    @Nested
    inner class CachingTests {

        @Test
        fun `isAvailable caches results - second call uses cache`() {
            // Arrange
            every { mockClassLoadingStrategy.loadClass("org.jetbrains.kotlin.psi.KtFile") } returns Any::class.java
            PluginAvailability.setClassLoadingStrategy(mockClassLoadingStrategy)

            // Act - call twice
            val result1 = PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)
            val result2 = PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)

            // Assert - both calls should succeed
            assertTrue(result1)
            assertTrue(result2)

            // Verify loadClass was called only once (second call used cache)
            verify(exactly = 1) { mockClassLoadingStrategy.loadClass("org.jetbrains.kotlin.psi.KtFile") }
        }

        @Test
        fun `isAvailable caches false results too`() {
            // Arrange
            every { mockClassLoadingStrategy.loadClass("com.jetbrains.python.psi.PyFile") } throws ClassNotFoundException("Not found")
            PluginAvailability.setClassLoadingStrategy(mockClassLoadingStrategy)

            // Act - call twice
            val result1 = PluginAvailability.isAvailable(SupportedLanguage.PYTHON)
            val result2 = PluginAvailability.isAvailable(SupportedLanguage.PYTHON)

            // Assert
            assertFalse(result1)
            assertFalse(result2)

            // Verify loadClass was called only once
            verify(exactly = 1) { mockClassLoadingStrategy.loadClass("com.jetbrains.python.psi.PyFile") }
        }

        @Test
        fun `clearCache clears the cache allowing fresh lookup`() {
            // Arrange - first strategy
            val firstStrategy: ClassLoadingStrategy = mockk()
            every { firstStrategy.loadClass("org.jetbrains.kotlin.psi.KtFile") } returns Any::class.java
            PluginAvailability.setClassLoadingStrategy(firstStrategy)

            // Act - call first time
            PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)

            // Clear cache (this also resets strategy to default)
            PluginAvailability.clearCache()

            // Set up new strategy
            val secondStrategy: ClassLoadingStrategy = mockk()
            every { secondStrategy.loadClass("org.jetbrains.kotlin.psi.KtFile") } returns Any::class.java
            PluginAvailability.setClassLoadingStrategy(secondStrategy)

            // Call again
            PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)

            // Verify both strategies were called once each
            verify(exactly = 1) { firstStrategy.loadClass("org.jetbrains.kotlin.psi.KtFile") }
            verify(exactly = 1) { secondStrategy.loadClass("org.jetbrains.kotlin.psi.KtFile") }
        }

        @Test
        fun `JAVA and UNKNOWN do not use cache (direct return)`() {
            // Arrange - set a mock that would fail if called
            every { mockClassLoadingStrategy.loadClass(any()) } throws RuntimeException("Should not be called")
            PluginAvailability.setClassLoadingStrategy(mockClassLoadingStrategy)

            // Act - call multiple times
            repeat(3) {
                PluginAvailability.isAvailable(SupportedLanguage.JAVA)
                PluginAvailability.isAvailable(SupportedLanguage.UNKNOWN)
            }

            // Verify loadClass was never called for JAVA or UNKNOWN
            verify(exactly = 0) { mockClassLoadingStrategy.loadClass(any()) }
        }
    }

    // ==================== Class Path Mapping Tests ====================

    @Nested
    inner class ClassPathMappingTests {

        @Test
        fun `getClassPath returns correct path for KOTLIN`() {
            val classPath = PluginAvailability.getClassPath(SupportedLanguage.KOTLIN)

            assertEquals("org.jetbrains.kotlin.psi.KtFile", classPath)
        }

        @Test
        fun `getClassPath returns correct path for JAVASCRIPT`() {
            val classPath = PluginAvailability.getClassPath(SupportedLanguage.JAVASCRIPT)

            assertEquals("com.intellij.lang.javascript.psi.JSFile", classPath)
        }

        @Test
        fun `getClassPath returns correct path for TYPESCRIPT`() {
            val classPath = PluginAvailability.getClassPath(SupportedLanguage.TYPESCRIPT)

            // TypeScript uses the same class as JavaScript (same plugin)
            assertEquals("com.intellij.lang.javascript.psi.JSFile", classPath)
        }

        @Test
        fun `getClassPath returns correct path for PYTHON`() {
            val classPath = PluginAvailability.getClassPath(SupportedLanguage.PYTHON)

            assertEquals("com.jetbrains.python.psi.PyFile", classPath)
        }

        @Test
        fun `getClassPath returns correct path for GO`() {
            val classPath = PluginAvailability.getClassPath(SupportedLanguage.GO)

            assertEquals("com.goide.psi.GoFile", classPath)
        }

        @Test
        fun `getClassPath returns correct path for RUST`() {
            val classPath = PluginAvailability.getClassPath(SupportedLanguage.RUST)

            assertEquals("org.rust.lang.core.psi.RsFile", classPath)
        }

        @Test
        fun `getClassPath returns null for JAVA (no class path needed)`() {
            val classPath = PluginAvailability.getClassPath(SupportedLanguage.JAVA)

            assertNull(classPath, "JAVA should not have a class path (always available)")
        }

        @Test
        fun `getClassPath returns null for UNKNOWN`() {
            val classPath = PluginAvailability.getClassPath(SupportedLanguage.UNKNOWN)

            assertNull(classPath, "UNKNOWN should not have a class path")
        }

        @Test
        fun `all languages with class paths have corresponding availability check`() {
            // Arrange - mock loadClass to return successfully for any class
            every { mockClassLoadingStrategy.loadClass(any()) } returns Any::class.java
            PluginAvailability.setClassLoadingStrategy(mockClassLoadingStrategy)

            // Test that all languages with class paths can be checked
            val languagesWithClassPaths = listOf(
                SupportedLanguage.KOTLIN,
                SupportedLanguage.JAVASCRIPT,
                SupportedLanguage.TYPESCRIPT,
                SupportedLanguage.PYTHON,
                SupportedLanguage.GO,
                SupportedLanguage.RUST
            )

            for (language in languagesWithClassPaths) {
                // Clear cache between checks
                PluginAvailability.clearCache()
                PluginAvailability.setClassLoadingStrategy(mockClassLoadingStrategy)

                val classPath = PluginAvailability.getClassPath(language)
                assertNotNull(classPath, "Language $language should have a class path")

                val available = PluginAvailability.isAvailable(language)
                assertTrue(available, "Language $language should be available when mocked")
            }
        }
    }

    // ==================== Integration-Style Tests ====================

    @Nested
    inner class IntegrationTests {

        @Test
        fun `isAvailable checks different languages independently`() {
            // Arrange - mock different results for different classes
            every { mockClassLoadingStrategy.loadClass("org.jetbrains.kotlin.psi.KtFile") } returns Any::class.java
            every { mockClassLoadingStrategy.loadClass("com.jetbrains.python.psi.PyFile") } throws ClassNotFoundException()
            every { mockClassLoadingStrategy.loadClass("com.goide.psi.GoFile") } returns Any::class.java
            PluginAvailability.setClassLoadingStrategy(mockClassLoadingStrategy)

            // Act & Assert
            assertTrue(PluginAvailability.isAvailable(SupportedLanguage.KOTLIN))
            assertFalse(PluginAvailability.isAvailable(SupportedLanguage.PYTHON))
            assertTrue(PluginAvailability.isAvailable(SupportedLanguage.GO))

            // JAVA is always true, UNKNOWN is always false
            assertTrue(PluginAvailability.isAvailable(SupportedLanguage.JAVA))
            assertFalse(PluginAvailability.isAvailable(SupportedLanguage.UNKNOWN))
        }

        @Test
        fun `multiple threads can safely check availability concurrently`() {
            // Arrange
            every { mockClassLoadingStrategy.loadClass(any()) } returns Any::class.java
            PluginAvailability.setClassLoadingStrategy(mockClassLoadingStrategy)

            // Act - run concurrent checks
            val threads = mutableListOf<Thread>()
            val results = mutableListOf<Boolean>()

            repeat(10) {
                val thread = Thread {
                    // Each thread checks multiple languages
                    synchronized(results) {
                        results.add(PluginAvailability.isAvailable(SupportedLanguage.KOTLIN))
                        results.add(PluginAvailability.isAvailable(SupportedLanguage.PYTHON))
                        results.add(PluginAvailability.isAvailable(SupportedLanguage.GO))
                    }
                }
                threads.add(thread)
                thread.start()
            }

            // Wait for all threads
            threads.forEach { it.join() }

            // Assert - all results should be true (30 total: 10 threads * 3 languages)
            assertEquals(30, results.size)
            assertTrue(results.all { it }, "All concurrent checks should succeed")
        }
    }

    // ==================== Edge Case Tests ====================

    @Nested
    inner class EdgeCaseTests {

        @Test
        fun `isAvailable handles LinkageError gracefully`() {
            // Arrange - mock to throw LinkageError (extends Error, not Exception)
            every { mockClassLoadingStrategy.loadClass("org.jetbrains.kotlin.psi.KtFile") } throws NoClassDefFoundError("Linkage error")
            PluginAvailability.setClassLoadingStrategy(mockClassLoadingStrategy)

            // Act
            val result = PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)

            // Assert - should return false and not crash
            assertFalse(result)
        }

        @Test
        fun `clearCache can be called multiple times safely`() {
            // Act - should not throw
            assertDoesNotThrow {
                repeat(5) {
                    PluginAvailability.clearCache()
                }
            }
        }

        @Test
        fun `clearCache on empty cache does not throw`() {
            // Fresh state after setUp's clearCache()
            assertDoesNotThrow {
                PluginAvailability.clearCache()
            }
        }

        @Test
        fun `clearCache resets class loading strategy to default`() {
            // Arrange - set a custom strategy
            every { mockClassLoadingStrategy.loadClass(any()) } returns Any::class.java
            PluginAvailability.setClassLoadingStrategy(mockClassLoadingStrategy)

            // Verify custom strategy is being used
            PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)
            verify(exactly = 1) { mockClassLoadingStrategy.loadClass(any()) }

            // Clear cache (resets to default strategy)
            PluginAvailability.clearCache()

            // Now availability check should use the default strategy (Class.forName)
            // Since Kotlin plugin class likely doesn't exist in test environment,
            // we just verify our mock is no longer called
            clearMocks(mockClassLoadingStrategy)

            // This will use the default strategy - don't fail if class not found
            try {
                PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)
            } catch (e: Exception) {
                // OK - default strategy might throw, but our mock should not be called
            }

            // Verify our mock was NOT called after clearCache
            verify(exactly = 0) { mockClassLoadingStrategy.loadClass(any()) }
        }

        @Test
        fun `setClassLoadingStrategy replaces previous strategy`() {
            // Arrange - first strategy returns true
            val firstStrategy: ClassLoadingStrategy = mockk()
            every { firstStrategy.loadClass(any()) } returns Any::class.java

            // Second strategy throws (not found)
            val secondStrategy: ClassLoadingStrategy = mockk()
            every { secondStrategy.loadClass(any()) } throws ClassNotFoundException()

            // Act - use first strategy
            PluginAvailability.setClassLoadingStrategy(firstStrategy)
            val firstResult = PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)

            // Clear cache to allow re-check
            PluginAvailability.clearCache()

            // Use second strategy
            PluginAvailability.setClassLoadingStrategy(secondStrategy)
            val secondResult = PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)

            // Assert
            assertTrue(firstResult, "First strategy should return true")
            assertFalse(secondResult, "Second strategy should return false")
        }
    }

    // ==================== DefaultClassLoadingStrategy Tests ====================

    @Nested
    inner class DefaultClassLoadingStrategyTests {

        @Test
        fun `DefaultClassLoadingStrategy loads existing class successfully`() {
            // Act - try to load a class that definitely exists
            val loadedClass = DefaultClassLoadingStrategy.loadClass("java.lang.String")

            // Assert
            assertEquals(String::class.java, loadedClass)
        }

        @Test
        fun `DefaultClassLoadingStrategy throws ClassNotFoundException for missing class`() {
            // Act & Assert
            assertThrows<ClassNotFoundException> {
                DefaultClassLoadingStrategy.loadClass("com.nonexistent.FakeClass")
            }
        }
    }
}
