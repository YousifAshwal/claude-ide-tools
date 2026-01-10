package com.igorlink.claudeidetools.util

import com.igorlink.claudeidetools.handlers.languages.*
import com.igorlink.claudeidetools.model.ExtractMethodRequest
import com.igorlink.claudeidetools.model.RefactoringResponse
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Centralized registry for language-specific refactoring handlers.
 *
 * This registry eliminates duplicated routing logic by providing a single place
 * to register and lookup language-specific handlers for both move and extract method
 * operations. It handles plugin availability checks and provides uniform error messages.
 *
 * ## Design Principles
 * - Single Responsibility: Each handler focuses on its language-specific logic
 * - Open/Closed: New languages can be added by implementing handler interfaces and registering them
 * - Dependency Inversion: Handlers depend on abstractions, not concrete implementations
 *
 * ## Extensibility
 * To add support for a new language:
 * 1. Add the language to [SupportedLanguage] enum
 * 2. Create language-specific handlers implementing [MoveHandlerContract] and [ExtractMethodHandlerContract]
 * 3. Register the handlers in this registry
 *
 * @see MoveHandlerContract Contract for move refactoring handlers
 * @see ExtractMethodHandlerContract Contract for extract method handlers
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
     * Java is handled specially in the main handler, so not included here.
     */
    private val moveHandlers: Map<SupportedLanguage, MoveHandlerEntry> = mapOf(
        SupportedLanguage.KOTLIN to MoveHandlerEntry(KotlinMoveHandlerAdapter),
        SupportedLanguage.JAVASCRIPT to MoveHandlerEntry(JavaScriptMoveHandlerAdapter),
        SupportedLanguage.TYPESCRIPT to MoveHandlerEntry(JavaScriptMoveHandlerAdapter),
        SupportedLanguage.PYTHON to MoveHandlerEntry(PythonMoveHandlerAdapter),
        SupportedLanguage.GO to MoveHandlerEntry(GoMoveHandlerAdapter),
        SupportedLanguage.RUST to MoveHandlerEntry(RustMoveHandlerAdapter)
    )

    /**
     * Registry of extract method handlers by language.
     * Java is handled specially in the main handler, so not included here.
     */
    private val extractMethodHandlers: Map<SupportedLanguage, ExtractMethodHandlerEntry> = mapOf(
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
        val entry = moveHandlers[language] ?: return null

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
        val entry = extractMethodHandlers[language] ?: return null

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
     * @param language The language to check
     * @param operationType The type of operation ("move" or "extractMethod")
     * @return true if a handler is registered
     */
    fun hasHandler(language: SupportedLanguage, operationType: String): Boolean {
        return when (operationType) {
            "move" -> moveHandlers.containsKey(language)
            "extractMethod" -> extractMethodHandlers.containsKey(language)
            else -> false
        }
    }

    /**
     * Returns the list of supported languages for a given operation type.
     *
     * @param operationType The type of operation ("move" or "extractMethod")
     * @return Set of supported languages (always includes JAVA which is handled specially)
     */
    fun getSupportedLanguages(operationType: String): Set<SupportedLanguage> {
        val registered = when (operationType) {
            "move" -> moveHandlers.keys
            "extractMethod" -> extractMethodHandlers.keys
            else -> emptySet()
        }
        // Java is always supported (handled in main handlers)
        return registered + SupportedLanguage.JAVA
    }

    // ========== Adapters for existing handlers ==========

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
