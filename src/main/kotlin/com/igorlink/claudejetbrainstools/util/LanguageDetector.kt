package com.igorlink.claudejetbrainstools.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile

/**
 * Enumeration of programming languages supported for refactoring operations.
 *
 * Each language may have different refactoring capabilities depending on
 * the IDE and installed plugins. Use [LanguageDetector.isLanguageSupported]
 * to check availability at runtime.
 */
enum class SupportedLanguage {
    /** Java language - always supported in IntelliJ-based IDEs. */
    JAVA,

    /** Kotlin language - requires Kotlin plugin. */
    KOTLIN,

    /** JavaScript language - requires JavaScript plugin (included in WebStorm). */
    JAVASCRIPT,

    /** TypeScript language - requires JavaScript plugin (included in WebStorm). */
    TYPESCRIPT,

    /** Python language - requires Python plugin (included in PyCharm). */
    PYTHON,

    /** Go language - requires Go plugin (included in GoLand). */
    GO,

    /** Rust language - requires intellij-rust plugin (included in RustRover). */
    RUST,

    /** Unknown or unsupported language. */
    UNKNOWN
}

/**
 * Utility for detecting programming language from PSI files.
 *
 * This detector examines both the IntelliJ language ID and file extension
 * to determine the programming language. It's used to route refactoring
 * requests to the appropriate language-specific handlers.
 *
 * ## Detection Strategy
 * 1. Check the PSI file's language ID against known values
 * 2. For ambiguous cases (like JS vs TS), check file extension
 * 3. Fall back to extension-based detection for unknown language IDs
 *
 * ## Plugin Availability
 * Use [isLanguageSupported] to check if the required plugin is loaded
 * before attempting language-specific operations.
 *
 * @see SupportedLanguage The enumeration of supported languages
 */
object LanguageDetector {
    private val logger = Logger.getInstance(LanguageDetector::class.java)

    // IntelliJ language ID constants
    private const val LANG_JAVA = "JAVA"
    private const val LANG_KOTLIN = "kotlin"
    private const val LANG_JAVASCRIPT = "JavaScript"
    private const val LANG_TYPESCRIPT = "TypeScript"
    private const val LANG_ECMASCRIPT6 = "ECMAScript 6"
    private const val LANG_PYTHON = "Python"
    private const val LANG_GO = "go"
    private const val LANG_RUST = "Rust"

    /**
     * Detects the programming language of a PSI file.
     *
     * @param psiFile The PSI file to analyze
     * @return The detected language or UNKNOWN if not recognized
     */
    fun detect(psiFile: PsiFile): SupportedLanguage {
        val languageId = psiFile.language.id
        val fileExtension = psiFile.virtualFile?.extension?.lowercase()

        logger.debug("Detecting language: languageId=$languageId, extension=$fileExtension")

        return when {
            languageId == LANG_JAVA -> SupportedLanguage.JAVA
            languageId == LANG_KOTLIN -> SupportedLanguage.KOTLIN
            languageId == LANG_TYPESCRIPT -> SupportedLanguage.TYPESCRIPT
            languageId == LANG_JAVASCRIPT || languageId == LANG_ECMASCRIPT6 -> {
                // Distinguish TS from JS by extension when language ID is ambiguous
                if (fileExtension == "ts" || fileExtension == "tsx") {
                    SupportedLanguage.TYPESCRIPT
                } else {
                    SupportedLanguage.JAVASCRIPT
                }
            }
            languageId == LANG_PYTHON -> SupportedLanguage.PYTHON
            languageId == LANG_GO -> SupportedLanguage.GO
            languageId == LANG_RUST -> SupportedLanguage.RUST
            else -> {
                // Fallback to extension-based detection
                detectByExtension(fileExtension)
            }
        }
    }

    /**
     * Fallback detection based on file extension.
     *
     * Used when the PSI language ID doesn't match any known language,
     * which can happen with certain file types or configurations.
     *
     * @param extension The lowercase file extension (without dot)
     * @return The detected language or [SupportedLanguage.UNKNOWN]
     */
    private fun detectByExtension(extension: String?): SupportedLanguage {
        return when (extension) {
            "java" -> SupportedLanguage.JAVA
            "kt", "kts" -> SupportedLanguage.KOTLIN
            "js", "jsx", "mjs", "cjs" -> SupportedLanguage.JAVASCRIPT
            "ts", "tsx", "mts", "cts" -> SupportedLanguage.TYPESCRIPT
            "py", "pyw", "pyi" -> SupportedLanguage.PYTHON
            "go" -> SupportedLanguage.GO
            "rs" -> SupportedLanguage.RUST
            else -> SupportedLanguage.UNKNOWN
        }
    }

    /**
     * Checks if a language plugin is available in the current IDE.
     *
     * Delegates to [PluginAvailability] which provides cached, thread-safe
     * plugin availability checking.
     *
     * @param language The language to check
     * @return true if the language plugin is loaded
     * @see PluginAvailability.isAvailable
     */
    fun isLanguageSupported(language: SupportedLanguage): Boolean {
        return PluginAvailability.isAvailable(language)
    }

    /**
     * Returns a human-readable display name for the language.
     *
     * Use this for user-facing messages and error descriptions.
     *
     * @param language The language to get the display name for
     * @return A capitalized, human-readable language name (e.g., "JavaScript", "TypeScript")
     */
    fun getLanguageName(language: SupportedLanguage): String {
        return when (language) {
            SupportedLanguage.JAVA -> "Java"
            SupportedLanguage.KOTLIN -> "Kotlin"
            SupportedLanguage.JAVASCRIPT -> "JavaScript"
            SupportedLanguage.TYPESCRIPT -> "TypeScript"
            SupportedLanguage.PYTHON -> "Python"
            SupportedLanguage.GO -> "Go"
            SupportedLanguage.RUST -> "Rust"
            SupportedLanguage.UNKNOWN -> "Unknown"
        }
    }
}
