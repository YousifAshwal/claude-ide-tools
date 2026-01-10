package com.igorlink.claudejetbrainstools.util

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Functional interface for loading classes by name.
 *
 * This abstraction allows the plugin availability checker to be tested
 * without mocking core JDK classes like Class.forName().
 */
fun interface ClassLoadingStrategy {
    /**
     * Attempts to load a class by its fully qualified name.
     *
     * @param className The fully qualified class name
     * @return The loaded Class object
     * @throws ClassNotFoundException if the class is not found
     */
    @Throws(ClassNotFoundException::class)
    fun loadClass(className: String): Class<*>
}

/**
 * Default class loading strategy that uses the standard Class.forName().
 */
object DefaultClassLoadingStrategy : ClassLoadingStrategy {
    override fun loadClass(className: String): Class<*> = Class.forName(className)
}

/**
 * Utility for checking the availability of language plugins in the current IDE.
 *
 * This singleton centralizes all plugin availability checks across language handlers,
 * eliminating duplicated Class.forName() calls and caching results for efficiency.
 *
 * ## Supported Languages
 * - **Kotlin**: Requires the Kotlin plugin (included in IntelliJ IDEA)
 * - **JavaScript/TypeScript**: Requires the JavaScript plugin (included in WebStorm, IDEA Ultimate)
 * - **Python**: Requires the Python plugin (included in PyCharm, IDEA with Python plugin)
 * - **Go**: Requires the Go plugin (included in GoLand, IDEA with Go plugin)
 * - **Rust**: Requires the intellij-rust plugin (included in RustRover)
 *
 * ## Usage
 * ```kotlin
 * if (PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)) {
 *     KotlinMoveHandler.move(...)
 * }
 * ```
 *
 * ## Caching Behavior
 * Results are cached permanently since plugin availability does not change during runtime.
 * The cache uses a thread-safe ConcurrentHashMap for safe concurrent access.
 *
 * ## Testing
 * Use [setClassLoadingStrategy] to inject a test double for unit testing.
 *
 * @see SupportedLanguage The enumeration of supported languages
 * @see ClassLoadingStrategy For testing with a custom class loader
 */
object PluginAvailability {

    private val logger = Logger.getInstance(PluginAvailability::class.java)

    /**
     * The strategy used to load classes for plugin detection.
     * Can be overridden for testing purposes via [setClassLoadingStrategy].
     */
    @Volatile
    private var classLoadingStrategy: ClassLoadingStrategy = DefaultClassLoadingStrategy

    /**
     * Map of language to the fully qualified class name used for plugin detection.
     *
     * These PSI file classes are the canonical markers for plugin presence:
     * - If the class can be loaded, the plugin is available
     * - If ClassNotFoundException is thrown, the plugin is not installed
     */
    private val languageClassPaths: Map<SupportedLanguage, String> = mapOf(
        SupportedLanguage.KOTLIN to "org.jetbrains.kotlin.psi.KtFile",
        SupportedLanguage.JAVASCRIPT to "com.intellij.lang.javascript.psi.JSFile",
        SupportedLanguage.TYPESCRIPT to "com.intellij.lang.javascript.psi.JSFile",
        SupportedLanguage.PYTHON to "com.jetbrains.python.psi.PyFile",
        SupportedLanguage.GO to "com.goide.psi.GoFile",
        SupportedLanguage.RUST to "org.rust.lang.core.psi.RsFile"
    )

    /**
     * Cache for plugin availability results.
     *
     * Key: SupportedLanguage
     * Value: Boolean indicating whether the plugin is available
     *
     * Uses ConcurrentHashMap for thread-safe access from multiple handlers.
     */
    private val availabilityCache = ConcurrentHashMap<SupportedLanguage, Boolean>()

    /**
     * Checks if a language plugin is available in the current IDE.
     *
     * Results are cached after the first check for each language to avoid
     * repeated Class.forName() calls. The cache is thread-safe and uses
     * computeIfAbsent for atomic check-and-cache operations.
     *
     * @param language The language to check for plugin availability
     * @return `true` if the language plugin is available, `false` otherwise
     */
    fun isAvailable(language: SupportedLanguage): Boolean {
        // Java is always available in IntelliJ-based IDEs (required dependency)
        if (language == SupportedLanguage.JAVA) {
            return true
        }

        // UNKNOWN language is never supported
        if (language == SupportedLanguage.UNKNOWN) {
            return false
        }

        // Use computeIfAbsent for atomic check-and-cache
        return availabilityCache.computeIfAbsent(language) { lang ->
            checkPluginAvailability(lang)
        }
    }

    /**
     * Performs the actual plugin availability check using the configured class loading strategy.
     *
     * This method is called once per language and the result is cached.
     * It attempts to load the PSI file class for the language to determine
     * if the corresponding plugin is installed.
     *
     * @param language The language to check
     * @return `true` if the plugin class can be loaded, `false` otherwise
     */
    private fun checkPluginAvailability(language: SupportedLanguage): Boolean {
        val className = languageClassPaths[language]

        if (className == null) {
            logger.debug("No class path defined for language: $language")
            return false
        }

        return try {
            classLoadingStrategy.loadClass(className)
            logger.debug("Plugin available for $language (found $className)")
            true
        } catch (e: ClassNotFoundException) {
            logger.debug("Plugin not available for $language ($className not found)")
            false
        } catch (e: LinkageError) {
            // Handles NoClassDefFoundError, UnsatisfiedLinkError, etc.
            logger.debug("Plugin not available for $language (linkage error: ${e.message})")
            false
        } catch (e: Exception) {
            logger.warn("Error checking plugin availability for $language: ${e.message}")
            false
        }
    }

    /**
     * Clears the availability cache and resets the class loading strategy.
     *
     * This is primarily useful for testing purposes. In production,
     * plugin availability does not change during runtime, so cache
     * invalidation is not necessary.
     */
    fun clearCache() {
        availabilityCache.clear()
        classLoadingStrategy = DefaultClassLoadingStrategy
    }

    /**
     * Sets a custom class loading strategy.
     *
     * This method is intended for testing purposes to inject a mock
     * class loader without needing to mock the JDK's Class.forName().
     *
     * @param strategy The class loading strategy to use
     */
    fun setClassLoadingStrategy(strategy: ClassLoadingStrategy) {
        classLoadingStrategy = strategy
    }

    /**
     * Returns the class path used for detecting the given language's plugin.
     *
     * Useful for debugging and logging purposes.
     *
     * @param language The language to get the class path for
     * @return The fully qualified class name, or null if not defined
     */
    fun getClassPath(language: SupportedLanguage): String? {
        return languageClassPaths[language]
    }
}
