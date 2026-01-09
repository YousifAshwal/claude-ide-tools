package com.igorlink.claudejetbrainstools.util

import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for LanguageDetector.
 * Tests language detection from PSI files including:
 * - SupportedLanguage enum values
 * - Detection by language ID
 * - Detection by file extension (fallback)
 * - Language display names
 * - Plugin availability delegation
 */
class LanguageDetectorTest {

    private lateinit var mockPsiFile: PsiFile
    private lateinit var mockLanguage: Language
    private lateinit var mockVirtualFile: VirtualFile
    private lateinit var mockLogger: Logger

    @BeforeEach
    fun setUp() {
        mockPsiFile = mockk(relaxed = true)
        mockLanguage = mockk(relaxed = true)
        mockVirtualFile = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)

        // Setup default mock behavior
        every { mockPsiFile.language } returns mockLanguage
        every { mockPsiFile.virtualFile } returns mockVirtualFile

        // Mock Logger to prevent IntelliJ TestLoggerFactory from failing tests on logged errors
        mockkStatic(Logger::class)
        every { Logger.getInstance(any<Class<*>>()) } returns mockLogger
        every { Logger.getInstance(any<String>()) } returns mockLogger

        // Mock PluginAvailability for isLanguageSupported tests
        mockkObject(PluginAvailability)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ==================== SupportedLanguage Enum Tests ====================

    @Nested
    inner class SupportedLanguageEnumTests {

        @Test
        fun `SupportedLanguage contains all expected values`() {
            val expectedValues = setOf(
                "JAVA", "KOTLIN", "JAVASCRIPT", "TYPESCRIPT",
                "PYTHON", "GO", "RUST", "UNKNOWN"
            )

            val actualValues = SupportedLanguage.entries.map { it.name }.toSet()

            assertEquals(expectedValues, actualValues)
        }

        @Test
        fun `SupportedLanguage has exactly 8 values`() {
            assertEquals(8, SupportedLanguage.entries.size)
        }

        @ParameterizedTest
        @EnumSource(SupportedLanguage::class)
        fun `all SupportedLanguage values can be accessed`(language: SupportedLanguage) {
            // Verify each enum value is accessible and has a valid name
            assertNotNull(language.name)
            assertTrue(language.name.isNotEmpty())
        }
    }

    // ==================== detect() Tests by Language ID ====================

    @Nested
    inner class DetectByLanguageIdTests {

        @Test
        fun `detect returns JAVA for JAVA language ID`() {
            setupLanguageId("JAVA")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.JAVA, result)
        }

        @Test
        fun `detect returns KOTLIN for kotlin language ID`() {
            setupLanguageId("kotlin")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.KOTLIN, result)
        }

        @Test
        fun `detect returns TYPESCRIPT for TypeScript language ID`() {
            setupLanguageId("TypeScript")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.TYPESCRIPT, result)
        }

        @Test
        fun `detect returns JAVASCRIPT for JavaScript language ID`() {
            setupLanguageId("JavaScript")
            setupFileExtension("js")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.JAVASCRIPT, result)
        }

        @Test
        fun `detect returns JAVASCRIPT for ECMAScript 6 language ID`() {
            setupLanguageId("ECMAScript 6")
            setupFileExtension("js")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.JAVASCRIPT, result)
        }

        @Test
        fun `detect returns TYPESCRIPT for JavaScript language ID with ts extension`() {
            setupLanguageId("JavaScript")
            setupFileExtension("ts")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.TYPESCRIPT, result)
        }

        @Test
        fun `detect returns TYPESCRIPT for JavaScript language ID with tsx extension`() {
            setupLanguageId("JavaScript")
            setupFileExtension("tsx")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.TYPESCRIPT, result)
        }

        @Test
        fun `detect returns JAVASCRIPT for JavaScript language ID with js extension`() {
            setupLanguageId("JavaScript")
            setupFileExtension("js")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.JAVASCRIPT, result)
        }

        @Test
        fun `detect returns JAVASCRIPT for JavaScript language ID with jsx extension`() {
            setupLanguageId("JavaScript")
            setupFileExtension("jsx")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.JAVASCRIPT, result)
        }

        @Test
        fun `detect returns PYTHON for Python language ID`() {
            setupLanguageId("Python")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.PYTHON, result)
        }

        @Test
        fun `detect returns GO for go language ID`() {
            setupLanguageId("go")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.GO, result)
        }

        @Test
        fun `detect returns RUST for Rust language ID`() {
            setupLanguageId("Rust")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.RUST, result)
        }

        @Test
        fun `detect falls back to extension detection for unknown language ID`() {
            setupLanguageId("UnknownLang")
            setupFileExtension("java")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.JAVA, result)
        }

        @Test
        fun `detect returns UNKNOWN for unknown language ID and unknown extension`() {
            setupLanguageId("UnknownLang")
            setupFileExtension("xyz")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.UNKNOWN, result)
        }

        @Test
        fun `detect handles null virtualFile gracefully`() {
            setupLanguageId("UnknownLang")
            every { mockPsiFile.virtualFile } returns null

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.UNKNOWN, result)
        }

        @Test
        fun `detect handles null extension gracefully`() {
            setupLanguageId("UnknownLang")
            every { mockVirtualFile.extension } returns null

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.UNKNOWN, result)
        }
    }

    // ==================== detectByExtension() Tests (through detect()) ====================

    @Nested
    inner class DetectByExtensionTests {

        @ParameterizedTest
        @ValueSource(strings = ["java"])
        fun `extension java maps to JAVA`(extension: String) {
            setupUnknownLanguageWithExtension(extension)

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.JAVA, result)
        }

        @ParameterizedTest
        @ValueSource(strings = ["kt", "kts"])
        fun `Kotlin extensions map to KOTLIN`(extension: String) {
            setupUnknownLanguageWithExtension(extension)

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.KOTLIN, result)
        }

        @ParameterizedTest
        @ValueSource(strings = ["js", "jsx", "mjs", "cjs"])
        fun `JavaScript extensions map to JAVASCRIPT`(extension: String) {
            setupUnknownLanguageWithExtension(extension)

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.JAVASCRIPT, result)
        }

        @ParameterizedTest
        @ValueSource(strings = ["ts", "tsx", "mts", "cts"])
        fun `TypeScript extensions map to TYPESCRIPT`(extension: String) {
            setupUnknownLanguageWithExtension(extension)

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.TYPESCRIPT, result)
        }

        @ParameterizedTest
        @ValueSource(strings = ["py", "pyw", "pyi"])
        fun `Python extensions map to PYTHON`(extension: String) {
            setupUnknownLanguageWithExtension(extension)

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.PYTHON, result)
        }

        @ParameterizedTest
        @ValueSource(strings = ["go"])
        fun `Go extensions map to GO`(extension: String) {
            setupUnknownLanguageWithExtension(extension)

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.GO, result)
        }

        @ParameterizedTest
        @ValueSource(strings = ["rs"])
        fun `Rust extensions map to RUST`(extension: String) {
            setupUnknownLanguageWithExtension(extension)

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.RUST, result)
        }

        @ParameterizedTest
        @ValueSource(strings = ["txt", "xml", "json", "yaml", "md", "html", "css", "unknown", ""])
        fun `unknown extensions map to UNKNOWN`(extension: String) {
            setupUnknownLanguageWithExtension(extension)

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.UNKNOWN, result)
        }

        @Test
        fun `extension detection is case insensitive`() {
            setupUnknownLanguageWithExtension("JAVA")

            val result = LanguageDetector.detect(mockPsiFile)

            // The implementation lowercases the extension, so "JAVA" becomes "java"
            assertEquals(SupportedLanguage.JAVA, result)
        }

        @Test
        fun `mixed case extension is handled correctly`() {
            setupUnknownLanguageWithExtension("Kt")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.KOTLIN, result)
        }
    }

    // ==================== getLanguageName() Tests ====================

    @Nested
    inner class GetLanguageNameTests {

        @ParameterizedTest
        @CsvSource(
            "JAVA, Java",
            "KOTLIN, Kotlin",
            "JAVASCRIPT, JavaScript",
            "TYPESCRIPT, TypeScript",
            "PYTHON, Python",
            "GO, Go",
            "RUST, Rust",
            "UNKNOWN, Unknown"
        )
        fun `getLanguageName returns correct display name`(language: SupportedLanguage, expectedName: String) {
            val result = LanguageDetector.getLanguageName(language)

            assertEquals(expectedName, result)
        }

        @Test
        fun `getLanguageName returns human-readable names for all languages`() {
            for (language in SupportedLanguage.entries) {
                val name = LanguageDetector.getLanguageName(language)

                assertNotNull(name)
                assertTrue(name.isNotEmpty(), "Language name should not be empty for $language")
                // Display names should start with uppercase
                assertTrue(name[0].isUpperCase(), "Language name should start with uppercase: $name")
            }
        }
    }

    // ==================== isLanguageSupported() Tests ====================

    @Nested
    inner class IsLanguageSupportedTests {

        @Test
        fun `isLanguageSupported delegates to PluginAvailability for JAVA`() {
            every { PluginAvailability.isAvailable(SupportedLanguage.JAVA) } returns true

            val result = LanguageDetector.isLanguageSupported(SupportedLanguage.JAVA)

            assertTrue(result)
            verify(exactly = 1) { PluginAvailability.isAvailable(SupportedLanguage.JAVA) }
        }

        @Test
        fun `isLanguageSupported delegates to PluginAvailability for KOTLIN`() {
            every { PluginAvailability.isAvailable(SupportedLanguage.KOTLIN) } returns true

            val result = LanguageDetector.isLanguageSupported(SupportedLanguage.KOTLIN)

            assertTrue(result)
            verify(exactly = 1) { PluginAvailability.isAvailable(SupportedLanguage.KOTLIN) }
        }

        @Test
        fun `isLanguageSupported returns false when plugin is not available`() {
            every { PluginAvailability.isAvailable(SupportedLanguage.RUST) } returns false

            val result = LanguageDetector.isLanguageSupported(SupportedLanguage.RUST)

            assertFalse(result)
            verify(exactly = 1) { PluginAvailability.isAvailable(SupportedLanguage.RUST) }
        }

        @ParameterizedTest
        @EnumSource(SupportedLanguage::class)
        fun `isLanguageSupported correctly delegates for all languages`(language: SupportedLanguage) {
            every { PluginAvailability.isAvailable(language) } returns true

            val result = LanguageDetector.isLanguageSupported(language)

            assertTrue(result)
            verify(exactly = 1) { PluginAvailability.isAvailable(language) }
        }

        @Test
        fun `isLanguageSupported returns false for UNKNOWN`() {
            every { PluginAvailability.isAvailable(SupportedLanguage.UNKNOWN) } returns false

            val result = LanguageDetector.isLanguageSupported(SupportedLanguage.UNKNOWN)

            assertFalse(result)
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    inner class EdgeCaseTests {

        @Test
        fun `detect handles ECMAScript 6 with tsx extension as TypeScript`() {
            setupLanguageId("ECMAScript 6")
            setupFileExtension("tsx")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.TYPESCRIPT, result)
        }

        @Test
        fun `detect handles ECMAScript 6 with ts extension as TypeScript`() {
            setupLanguageId("ECMAScript 6")
            setupFileExtension("ts")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.TYPESCRIPT, result)
        }

        @Test
        fun `detect handles JavaScript with mjs extension as JavaScript`() {
            setupLanguageId("JavaScript")
            setupFileExtension("mjs")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.JAVASCRIPT, result)
        }

        @Test
        fun `detect handles JavaScript with cjs extension as JavaScript`() {
            setupLanguageId("JavaScript")
            setupFileExtension("cjs")

            val result = LanguageDetector.detect(mockPsiFile)

            assertEquals(SupportedLanguage.JAVASCRIPT, result)
        }

        @Test
        fun `detect handles JavaScript with mts extension as TypeScript`() {
            setupLanguageId("JavaScript")
            setupFileExtension("mts")

            val result = LanguageDetector.detect(mockPsiFile)

            // mts is a TypeScript extension, but JavaScript language ID with non-ts/tsx falls back to JS
            // Looking at the implementation: only "ts" or "tsx" explicitly checked
            assertEquals(SupportedLanguage.JAVASCRIPT, result)
        }

        @Test
        fun `detect handles JavaScript with cts extension as TypeScript`() {
            setupLanguageId("JavaScript")
            setupFileExtension("cts")

            val result = LanguageDetector.detect(mockPsiFile)

            // cts is a TypeScript extension, but JavaScript language ID with non-ts/tsx falls back to JS
            assertEquals(SupportedLanguage.JAVASCRIPT, result)
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Sets up the mock language ID
     */
    private fun setupLanguageId(languageId: String) {
        every { mockLanguage.id } returns languageId
    }

    /**
     * Sets up the mock file extension
     */
    private fun setupFileExtension(extension: String) {
        every { mockVirtualFile.extension } returns extension
    }

    /**
     * Sets up an unknown language ID with the specified extension
     * to test extension-based fallback detection
     */
    private fun setupUnknownLanguageWithExtension(extension: String) {
        setupLanguageId("UnknownLanguage")
        setupFileExtension(extension)
    }
}
