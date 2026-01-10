package com.igorlink.claudeidetools.util

import com.igorlink.claudeidetools.extensions.RefactoringExtensionService
import com.igorlink.claudeidetools.handlers.languages.*
import com.igorlink.claudeidetools.model.ExtractMethodRequest
import com.igorlink.claudeidetools.model.RefactoringResponse
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Centralized registry for language-specific refactoring handlers.
 *
 * This registry provides a unified API for accessing language-specific handlers.
 * It uses a hybrid approach:
 * 1. First queries extension points via [RefactoringExtensionService] (preferred)
 * 2. Falls back to legacy adapters for backward compatibility
 *
 * ## Architecture
 * The registry now prioritizes IntelliJ's extension point system:
 * - Handlers implement [com.igorlink.claudeidetools.extensions.MoveRefactoringExtension]
 *   or [com.igorlink.claudeidetools.extensions.ExtractMethodRefactoringExtension]
 * - Handlers are registered in plugin-{lang}.xml files
 * - Handlers are discovered at runtime, avoiding ClassNotFoundException
 *
 * Legacy adapters are kept for backward compatibility during migration.
 *
 * ## Extensibility
 * To add support for a new language:
 * 1. Add the language to [SupportedLanguage] enum if not already present
 * 2. Create handlers implementing the extension point interfaces
 * 3. Register handlers in the appropriate plugin-{lang}.xml file
 *
 * @see RefactoringExtensionService Service that discovers handlers via extension points
 * @see com.igorlink.claudeidetools.extensions.MoveRefactoringExtension Move handler interface
 * @see com.igorlink.claudeidetools.extensions.ExtractMethodRefactoringExtension Extract method handler interface
 * @see PluginAvailability Plugin availability checking
 */
object LanguageHandlerRegistry {

    /**
     * Contract for language-specific move refactoring handlers.
     *
     * Each language handler implements this interface to provide move functionality.
     * The registry uses this contract to invoke handlers uniformly.
     */
    interface MoveHandlerContract {
        /**
         * Checks if this handler is available (plugin installed).
         */
        fun isAvailable(): Boolean

        /**
         * Performs the move refactoring.
         *
         * @param project The IntelliJ project context
         * @param element The PSI element to move
         * @param targetPackage The target package/module path
         * @param searchInComments Whether to update occurrences in comments
         * @param searchInNonJavaFiles Whether to update occurrences in non-Java files
         * @return [RefactoringResponse] with the operation result
         */
        fun move(
            project: Project,
            element: PsiElement,
            targetPackage: String,
            searchInComments: Boolean,
            searchInNonJavaFiles: Boolean
        ): RefactoringResponse
    }

    /**
     * Contract for language-specific extract method handlers.
     *
     * Each language handler implements this interface to provide extract method functionality.
     * The registry uses this contract to invoke handlers uniformly.
     */
    interface ExtractMethodHandlerContract {
        /**
         * Checks if this handler is available (plugin installed).
         */
        fun isAvailable(): Boolean

        /**
         * Performs the extract method refactoring.
         *
         * @param project The IntelliJ project context
         * @param psiFile The file containing the code to extract
         * @param request The extraction request with range and method name
         * @return [RefactoringResponse] with the operation result
         */
        fun extractFromFile(
            project: Project,
            psiFile: PsiFile,
            request: ExtractMethodRequest
        ): RefactoringResponse
    }

    /**
     * Error messages for unavailable plugins.
     * Maps each language to its appropriate error message.
     */
    private val pluginNotAvailableMessages: Map<SupportedLanguage, String> = mapOf(
        SupportedLanguage.JAVA to "Java plugin is not available. Use IntelliJ IDEA or install Java plugin.",
        SupportedLanguage.KOTLIN to "Kotlin plugin is not available. Install Kotlin plugin to use this feature.",
        SupportedLanguage.JAVASCRIPT to "JavaScript plugin is not available. Use WebStorm or install JavaScript plugin.",
        SupportedLanguage.TYPESCRIPT to "JavaScript plugin is not available. Use WebStorm or install JavaScript plugin.",
        SupportedLanguage.PYTHON to "Python plugin is not available. Use PyCharm or install Python plugin.",
        SupportedLanguage.GO to "Go plugin is not available. Use GoLand or install Go plugin.",
        SupportedLanguage.RUST to "Rust plugin is not available. Install intellij-rust plugin."
    )

    /**
     * Wrapper for move handlers that adapts existing handler objects to the contract.
     */
    private data class MoveHandlerEntry(
        val handler: MoveHandlerContract
    )

    /**
     * Wrapper for extract method handlers that adapts existing handler objects to the contract.
     */
    private data class ExtractMethodHandlerEntry(
        val handler: ExtractMethodHandlerContract
    )

    /**
     * Registry of move handlers by language.
     * All languages including Java are now routed through this registry.
     */
    private val moveHandlers: Map<SupportedLanguage, MoveHandlerEntry> = mapOf(
        SupportedLanguage.JAVA to MoveHandlerEntry(JavaMoveHandlerAdapter),
        SupportedLanguage.KOTLIN to MoveHandlerEntry(KotlinMoveHandlerAdapter),
        SupportedLanguage.JAVASCRIPT to MoveHandlerEntry(JavaScriptMoveHandlerAdapter),
        SupportedLanguage.TYPESCRIPT to MoveHandlerEntry(JavaScriptMoveHandlerAdapter),
        SupportedLanguage.PYTHON to MoveHandlerEntry(PythonMoveHandlerAdapter),
        SupportedLanguage.GO to MoveHandlerEntry(GoMoveHandlerAdapter),
        SupportedLanguage.RUST to MoveHandlerEntry(RustMoveHandlerAdapter)
    )

    /**
     * Registry of extract method handlers by language.
     * All languages including Java are now routed through this registry.
     */
    private val extractMethodHandlers: Map<SupportedLanguage, ExtractMethodHandlerEntry> = mapOf(
        SupportedLanguage.JAVA to ExtractMethodHandlerEntry(JavaExtractMethodHandlerAdapter),
        SupportedLanguage.KOTLIN to ExtractMethodHandlerEntry(KotlinExtractMethodHandlerAdapter),
        SupportedLanguage.JAVASCRIPT to ExtractMethodHandlerEntry(JavaScriptExtractMethodHandlerAdapter),
        SupportedLanguage.TYPESCRIPT to ExtractMethodHandlerEntry(JavaScriptExtractMethodHandlerAdapter),
        SupportedLanguage.PYTHON to ExtractMethodHandlerEntry(PythonExtractMethodHandlerAdapter),
        SupportedLanguage.GO to ExtractMethodHandlerEntry(GoExtractMethodHandlerAdapter),
        SupportedLanguage.RUST to ExtractMethodHandlerEntry(RustExtractMethodHandlerAdapter)
    )

    /**
     * Routes a move refactoring request to the appropriate language-specific handler.
     *
     * Uses a hybrid approach:
     * 1. First tries extension points (preferred for avoiding ClassNotFoundException)
     * 2. Falls back to legacy adapters for backward compatibility
     *
     * Checks plugin availability before invoking the handler and returns
     * a uniform error message if the plugin is not installed.
     *
     * @param language The detected programming language
     * @param project The IntelliJ project context
     * @param element The PSI element to move
     * @param targetPackage The target package/module path
     * @param searchInComments Whether to update occurrences in comments
     * @param searchInNonJavaFiles Whether to update occurrences in non-Java files
     * @return [RefactoringResponse] with the operation result, or null if language not registered
     */
    fun move(
        language: SupportedLanguage,
        project: Project,
        element: PsiElement,
        targetPackage: String,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): RefactoringResponse? {
        // Unknown language is never supported
        if (language == SupportedLanguage.UNKNOWN) {
            return null
        }

        // Try extension points first (preferred approach)
        val extensionHandler = RefactoringExtensionService.getInstance().findMoveHandler(language)
        if (extensionHandler != null) {
            if (!extensionHandler.canHandle(element)) {
                return RefactoringResponse(
                    false,
                    "Cannot move this element type. Check that the element is a movable declaration."
                )
            }
            return extensionHandler.move(project, element, targetPackage, searchInComments, searchInNonJavaFiles)
        }

        // Fall back to legacy adapters
        val entry = moveHandlers[language] ?: run {
            // Check if plugin is available - if not, return specific message
            if (!PluginAvailability.isAvailable(language)) {
                return RefactoringResponse(
                    false,
                    pluginNotAvailableMessages[language] ?: "Plugin for $language is not available."
                )
            }
            return null
        }

        if (!entry.handler.isAvailable()) {
            return RefactoringResponse(
                false,
                pluginNotAvailableMessages[language] ?: "Plugin for $language is not available."
            )
        }

        return entry.handler.move(project, element, targetPackage, searchInComments, searchInNonJavaFiles)
    }

    /**
     * Routes an extract method refactoring request to the appropriate language-specific handler.
     *
     * Uses a hybrid approach:
     * 1. First tries extension points (preferred for avoiding ClassNotFoundException)
     * 2. Falls back to legacy adapters for backward compatibility
     *
     * Checks plugin availability before invoking the handler and returns
     * a uniform error message if the plugin is not installed.
     *
     * @param language The detected programming language
     * @param project The IntelliJ project context
     * @param psiFile The file containing the code to extract
     * @param request The extraction request with range and method name
     * @return [RefactoringResponse] with the operation result, or null if language not registered
     */
    fun extractMethod(
        language: SupportedLanguage,
        project: Project,
        psiFile: PsiFile,
        request: ExtractMethodRequest
    ): RefactoringResponse? {
        // Unknown language is never supported
        if (language == SupportedLanguage.UNKNOWN) {
            return null
        }

        // Try extension points first (preferred approach)
        val extensionHandler = RefactoringExtensionService.getInstance().findExtractMethodHandler(language)
        if (extensionHandler != null) {
            if (!extensionHandler.canHandle(psiFile)) {
                return RefactoringResponse(
                    false,
                    "Cannot extract method from this file type."
                )
            }
            return extensionHandler.extractFromFile(project, psiFile, request)
        }

        // Fall back to legacy adapters
        val entry = extractMethodHandlers[language] ?: run {
            // Check if plugin is available - if not, return specific message
            if (!PluginAvailability.isAvailable(language)) {
                return RefactoringResponse(
                    false,
                    pluginNotAvailableMessages[language] ?: "Plugin for $language is not available."
                )
            }
            return null
        }

        if (!entry.handler.isAvailable()) {
            return RefactoringResponse(
                false,
                pluginNotAvailableMessages[language] ?: "Plugin for $language is not available."
            )
        }

        return entry.handler.extractFromFile(project, psiFile, request)
    }

    /**
     * Checks if a handler is registered for the given language and operation type.
     *
     * Checks both extension points and legacy adapters.
     *
     * @param language The language to check
     * @param operationType The type of operation ("move" or "extractMethod")
     * @return true if a handler is registered
     */
    fun hasHandler(language: SupportedLanguage, operationType: String): Boolean {
        val service = RefactoringExtensionService.getInstance()

        // Check extension points first
        val hasExtensionHandler = when (operationType) {
            "move" -> service.findMoveHandler(language) != null
            "extractMethod" -> service.findExtractMethodHandler(language) != null
            else -> false
        }

        if (hasExtensionHandler) {
            return true
        }

        // Check legacy adapters
        return when (operationType) {
            "move" -> moveHandlers.containsKey(language)
            "extractMethod" -> extractMethodHandlers.containsKey(language)
            else -> false
        }
    }

    /**
     * Returns the list of supported languages for a given operation type.
     *
     * Combines languages from extension points and legacy adapters.
     *
     * @param operationType The type of operation ("move" or "extractMethod")
     * @return Set of supported languages
     */
    fun getSupportedLanguages(operationType: String): Set<SupportedLanguage> {
        val service = RefactoringExtensionService.getInstance()

        // Get languages from extension points
        val extensionLanguages = when (operationType) {
            "move" -> service.getAvailableMoveLanguages()
            "extractMethod" -> service.getAvailableExtractMethodLanguages()
            else -> emptySet()
        }

        // Get languages from legacy adapters
        val legacyLanguages = when (operationType) {
            "move" -> moveHandlers.keys
            "extractMethod" -> extractMethodHandlers.keys
            else -> emptySet()
        }

        return extensionLanguages + legacyLanguages
    }

    // ========== Adapters for existing handlers ==========

    /**
     * Adapter for JavaMoveHandler to implement MoveHandlerContract.
     */
    private object JavaMoveHandlerAdapter : MoveHandlerContract {
        override fun isAvailable(): Boolean = JavaMoveHandler.isAvailable()
        override fun move(
            project: Project,
            element: PsiElement,
            targetPackage: String,
            searchInComments: Boolean,
            searchInNonJavaFiles: Boolean
        ): RefactoringResponse = JavaMoveHandler.move(project, element, targetPackage, searchInComments, searchInNonJavaFiles)
    }

    /**
     * Adapter for JavaExtractMethodHandler to implement ExtractMethodHandlerContract.
     */
    private object JavaExtractMethodHandlerAdapter : ExtractMethodHandlerContract {
        override fun isAvailable(): Boolean = JavaExtractMethodHandler.isAvailable()
        override fun extractFromFile(
            project: Project,
            psiFile: PsiFile,
            request: ExtractMethodRequest
        ): RefactoringResponse = JavaExtractMethodHandler.extractFromFile(project, psiFile, request)
    }

    /**
     * Adapter for KotlinMoveHandler to implement MoveHandlerContract.
     */
    private object KotlinMoveHandlerAdapter : MoveHandlerContract {
        override fun isAvailable(): Boolean = KotlinMoveHandler.isAvailable()
        override fun move(
            project: Project,
            element: PsiElement,
            targetPackage: String,
            searchInComments: Boolean,
            searchInNonJavaFiles: Boolean
        ): RefactoringResponse = KotlinMoveHandler.move(project, element, targetPackage, searchInComments, searchInNonJavaFiles)
    }

    /**
     * Adapter for JavaScriptMoveHandler to implement MoveHandlerContract.
     * Used for both JavaScript and TypeScript.
     */
    private object JavaScriptMoveHandlerAdapter : MoveHandlerContract {
        override fun isAvailable(): Boolean = JavaScriptMoveHandler.isAvailable()
        override fun move(
            project: Project,
            element: PsiElement,
            targetPackage: String,
            searchInComments: Boolean,
            searchInNonJavaFiles: Boolean
        ): RefactoringResponse = JavaScriptMoveHandler.move(project, element, targetPackage, searchInComments, searchInNonJavaFiles)
    }

    /**
     * Adapter for PythonMoveHandler to implement MoveHandlerContract.
     */
    private object PythonMoveHandlerAdapter : MoveHandlerContract {
        override fun isAvailable(): Boolean = PythonMoveHandler.isAvailable()
        override fun move(
            project: Project,
            element: PsiElement,
            targetPackage: String,
            searchInComments: Boolean,
            searchInNonJavaFiles: Boolean
        ): RefactoringResponse = PythonMoveHandler.move(project, element, targetPackage, searchInComments, searchInNonJavaFiles)
    }

    /**
     * Adapter for GoMoveHandler to implement MoveHandlerContract.
     */
    private object GoMoveHandlerAdapter : MoveHandlerContract {
        override fun isAvailable(): Boolean = GoMoveHandler.isAvailable()
        override fun move(
            project: Project,
            element: PsiElement,
            targetPackage: String,
            searchInComments: Boolean,
            searchInNonJavaFiles: Boolean
        ): RefactoringResponse = GoMoveHandler.move(project, element, targetPackage, searchInComments, searchInNonJavaFiles)
    }

    /**
     * Adapter for RustMoveHandler to implement MoveHandlerContract.
     */
    private object RustMoveHandlerAdapter : MoveHandlerContract {
        override fun isAvailable(): Boolean = RustMoveHandler.isAvailable()
        override fun move(
            project: Project,
            element: PsiElement,
            targetPackage: String,
            searchInComments: Boolean,
            searchInNonJavaFiles: Boolean
        ): RefactoringResponse = RustMoveHandler.move(project, element, targetPackage, searchInComments, searchInNonJavaFiles)
    }

    /**
     * Adapter for KotlinExtractMethodHandler to implement ExtractMethodHandlerContract.
     */
    private object KotlinExtractMethodHandlerAdapter : ExtractMethodHandlerContract {
        override fun isAvailable(): Boolean = KotlinExtractMethodHandler.isAvailable()
        override fun extractFromFile(
            project: Project,
            psiFile: PsiFile,
            request: ExtractMethodRequest
        ): RefactoringResponse = KotlinExtractMethodHandler.extractFromFile(project, psiFile, request)
    }

    /**
     * Adapter for JavaScriptExtractMethodHandler to implement ExtractMethodHandlerContract.
     * Used for both JavaScript and TypeScript.
     */
    private object JavaScriptExtractMethodHandlerAdapter : ExtractMethodHandlerContract {
        override fun isAvailable(): Boolean = JavaScriptExtractMethodHandler.isAvailable()
        override fun extractFromFile(
            project: Project,
            psiFile: PsiFile,
            request: ExtractMethodRequest
        ): RefactoringResponse = JavaScriptExtractMethodHandler.extractFromFile(project, psiFile, request)
    }

    /**
     * Adapter for PythonExtractMethodHandler to implement ExtractMethodHandlerContract.
     */
    private object PythonExtractMethodHandlerAdapter : ExtractMethodHandlerContract {
        override fun isAvailable(): Boolean = PythonExtractMethodHandler.isAvailable()
        override fun extractFromFile(
            project: Project,
            psiFile: PsiFile,
            request: ExtractMethodRequest
        ): RefactoringResponse = PythonExtractMethodHandler.extractFromFile(project, psiFile, request)
    }

    /**
     * Adapter for GoExtractMethodHandler to implement ExtractMethodHandlerContract.
     */
    private object GoExtractMethodHandlerAdapter : ExtractMethodHandlerContract {
        override fun isAvailable(): Boolean = GoExtractMethodHandler.isAvailable()
        override fun extractFromFile(
            project: Project,
            psiFile: PsiFile,
            request: ExtractMethodRequest
        ): RefactoringResponse = GoExtractMethodHandler.extractFromFile(project, psiFile, request)
    }

    /**
     * Adapter for RustExtractMethodHandler to implement ExtractMethodHandlerContract.
     */
    private object RustExtractMethodHandlerAdapter : ExtractMethodHandlerContract {
        override fun isAvailable(): Boolean = RustExtractMethodHandler.isAvailable()
        override fun extractFromFile(
            project: Project,
            psiFile: PsiFile,
            request: ExtractMethodRequest
        ): RefactoringResponse = RustExtractMethodHandler.extractFromFile(project, psiFile, request)
    }
}
