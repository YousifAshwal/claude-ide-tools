package com.igorlink.claudeidetools.extensions

import com.igorlink.claudeidetools.model.ExtractMethodRequest
import com.igorlink.claudeidetools.model.RefactoringResponse
import com.igorlink.claudeidetools.util.LanguageDetector
import com.igorlink.claudeidetools.util.SupportedLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Service that discovers and routes refactoring requests to language-specific handlers.
 *
 * This service uses IntelliJ's extension point mechanism to discover handlers
 * that are registered via plugin.xml files. Handlers are only loaded when their
 * corresponding language plugin is available, preventing ClassNotFoundException
 * in IDEs without certain language plugins (e.g., Java in WebStorm).
 *
 * ## Extension Point Discovery
 * Handlers register via extension points defined in plugin.xml:
 * - `com.igorlink.claudeidetools.moveHandler` for [MoveRefactoringExtension]
 * - `com.igorlink.claudeidetools.extractMethodHandler` for [ExtractMethodRefactoringExtension]
 *
 * ## Handler Resolution Strategy
 * 1. Detect language from PSI file/element
 * 2. Find all registered handlers for that language
 * 3. Use the first handler that reports `canHandle() == true`
 * 4. Return error if no suitable handler found
 *
 * ## Thread Safety
 * This service is thread-safe. Extension point queries are handled by IntelliJ's
 * extension system, and handler lookups use immutable snapshots.
 *
 * @see MoveRefactoringExtension Extension point interface for move handlers
 * @see ExtractMethodRefactoringExtension Extension point interface for extract method handlers
 */
@Service(Service.Level.APP)
class RefactoringExtensionService {

    private val logger = Logger.getInstance(RefactoringExtensionService::class.java)

    companion object {
        /**
         * Gets the singleton instance of this service.
         *
         * @return The application-level service instance
         */
        @JvmStatic
        fun getInstance(): RefactoringExtensionService {
            return ApplicationManager.getApplication().getService(RefactoringExtensionService::class.java)
        }
    }

    /**
     * Finds a move handler for the given language.
     *
     * Queries the extension point for all registered move handlers and returns
     * the first one that supports the specified language.
     *
     * @param language The programming language to find a handler for
     * @return The handler, or null if no handler is registered for the language
     */
    fun findMoveHandler(language: SupportedLanguage): MoveRefactoringExtension? {
        return MoveRefactoringExtension.EP_NAME.extensionList
            .firstOrNull { it.getSupportedLanguage() == language }
    }

    /**
     * Finds a move handler that can process the given PSI element.
     *
     * Detects the language from the element's containing file, then finds
     * a handler that both supports the language and can handle the specific element.
     *
     * @param element The PSI element to find a handler for
     * @return The handler, or null if no suitable handler found
     */
    fun findMoveHandlerForElement(element: PsiElement): MoveRefactoringExtension? {
        val file = element.containingFile ?: return null
        val language = LanguageDetector.detect(file)

        return MoveRefactoringExtension.EP_NAME.extensionList
            .filter { it.getSupportedLanguage() == language }
            .firstOrNull { handler ->
                try {
                    handler.canHandle(element)
                } catch (e: Exception) {
                    logger.warn("Handler canHandle() threw exception for ${handler.getSupportedLanguage()}: ${e.message}")
                    false
                }
            }
    }

    /**
     * Finds an extract method handler for the given language.
     *
     * Queries the extension point for all registered extract method handlers
     * and returns the first one that supports the specified language.
     *
     * @param language The programming language to find a handler for
     * @return The handler, or null if no handler is registered for the language
     */
    fun findExtractMethodHandler(language: SupportedLanguage): ExtractMethodRefactoringExtension? {
        return ExtractMethodRefactoringExtension.EP_NAME.extensionList
            .firstOrNull { it.getSupportedLanguage() == language }
    }

    /**
     * Finds an extract method handler that can process the given PSI file.
     *
     * Detects the language from the file, then finds a handler that both
     * supports the language and can handle the specific file type.
     *
     * @param psiFile The PSI file to find a handler for
     * @return The handler, or null if no suitable handler found
     */
    fun findExtractMethodHandlerForFile(psiFile: PsiFile): ExtractMethodRefactoringExtension? {
        val language = LanguageDetector.detect(psiFile)

        return ExtractMethodRefactoringExtension.EP_NAME.extensionList
            .filter { it.getSupportedLanguage() == language }
            .firstOrNull { handler ->
                try {
                    handler.canHandle(psiFile)
                } catch (e: Exception) {
                    logger.warn("Handler canHandle() threw exception for ${handler.getSupportedLanguage()}: ${e.message}")
                    false
                }
            }
    }

    /**
     * Performs move refactoring using the extension point system.
     *
     * Finds the appropriate handler for the element and delegates the move operation.
     *
     * @param project The IntelliJ project context
     * @param element The PSI element to move
     * @param targetPackage The target package/module path
     * @param searchInComments Whether to also update occurrences in comments
     * @param searchInNonJavaFiles Whether to also update occurrences in non-code files
     * @return [RefactoringResponse] with the operation result, or null if no handler found
     */
    fun move(
        project: Project,
        element: PsiElement,
        targetPackage: String,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): RefactoringResponse? {
        val handler = findMoveHandlerForElement(element)
        if (handler == null) {
            logger.debug("No move handler found for element: ${element.javaClass.name}")
            return null
        }

        logger.debug("Using move handler for ${handler.getSupportedLanguage()}")
        return handler.move(project, element, targetPackage, searchInComments, searchInNonJavaFiles)
    }

    /**
     * Performs extract method refactoring using the extension point system.
     *
     * Finds the appropriate handler for the file and delegates the extraction.
     *
     * @param project The IntelliJ project context
     * @param psiFile The file containing the code to extract
     * @param request The extraction request with range and method name
     * @return [RefactoringResponse] with the operation result, or null if no handler found
     */
    fun extractMethod(
        project: Project,
        psiFile: PsiFile,
        request: ExtractMethodRequest
    ): RefactoringResponse? {
        val handler = findExtractMethodHandlerForFile(psiFile)
        if (handler == null) {
            logger.debug("No extract method handler found for file: ${psiFile.name}")
            return null
        }

        logger.debug("Using extract method handler for ${handler.getSupportedLanguage()}")
        return handler.extractFromFile(project, psiFile, request)
    }

    /**
     * Returns the set of languages that have registered move handlers.
     *
     * Useful for status reporting and error messages.
     *
     * @return Set of languages with available move handlers
     */
    fun getAvailableMoveLanguages(): Set<SupportedLanguage> {
        return MoveRefactoringExtension.EP_NAME.extensionList
            .map { it.getSupportedLanguage() }
            .toSet()
    }

    /**
     * Returns the set of languages that have registered extract method handlers.
     *
     * Useful for status reporting and error messages.
     *
     * @return Set of languages with available extract method handlers
     */
    fun getAvailableExtractMethodLanguages(): Set<SupportedLanguage> {
        return ExtractMethodRefactoringExtension.EP_NAME.extensionList
            .map { it.getSupportedLanguage() }
            .toSet()
    }

    /**
     * Checks if any handler is available for the given language and operation.
     *
     * @param language The language to check
     * @param operationType Either "move" or "extractMethod"
     * @return true if a handler is registered for the language and operation
     */
    fun hasHandler(language: SupportedLanguage, operationType: String): Boolean {
        return when (operationType) {
            "move" -> findMoveHandler(language) != null
            "extractMethod" -> findExtractMethodHandler(language) != null
            else -> false
        }
    }
}
