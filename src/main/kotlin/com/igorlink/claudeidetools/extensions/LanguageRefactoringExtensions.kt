package com.igorlink.claudeidetools.extensions

import com.igorlink.claudeidetools.model.ExtractMethodRequest
import com.igorlink.claudeidetools.model.RefactoringResponse
import com.igorlink.claudeidetools.util.SupportedLanguage
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Extension point interface for language-specific move refactoring handlers.
 *
 * Language plugins implement this interface to provide move functionality.
 * Implementations are registered via plugin.xml extension points and discovered
 * at runtime, ensuring handlers are only loaded when their language plugin is available.
 *
 * ## Implementation Requirements
 * - Implementations must NOT import language-specific classes at compile time if those
 *   classes may not be available (e.g., Java PSI classes in WebStorm)
 * - Use reflection via [com.igorlink.claudeidetools.util.PsiReflectionUtils] for accessing
 *   language-specific PSI classes when needed
 * - Registration should be in the appropriate plugin-{lang}.xml file
 *
 * ## Example Registration (plugin-java.xml)
 * ```xml
 * <extensions defaultExtensionNs="com.igorlink.claudeidetools">
 *     <moveHandler implementation="com.igorlink.claudeidetools.handlers.languages.java.JavaMoveHandler"/>
 * </extensions>
 * ```
 *
 * @see RefactoringExtensionService Service that discovers and routes to handlers
 * @see SupportedLanguage Enumeration of supported languages
 */
interface MoveRefactoringExtension {

    companion object {
        /**
         * Extension point name for move refactoring handlers.
         *
         * Handlers register under this extension point and are discovered
         * at runtime by [RefactoringExtensionService].
         */
        val EP_NAME: ExtensionPointName<MoveRefactoringExtension> =
            ExtensionPointName.create("com.igorlink.claudeidetools.moveHandler")
    }

    /**
     * Returns the language this handler supports.
     *
     * Used by [RefactoringExtensionService] to route requests to the appropriate handler.
     *
     * @return The supported language for this handler
     */
    fun getSupportedLanguage(): SupportedLanguage

    /**
     * Checks if this handler can process the given PSI element.
     *
     * Called before [move] to determine if this handler should be used.
     * Implementations should check element type, file type, and any other
     * language-specific criteria.
     *
     * @param element The PSI element to check
     * @return `true` if this handler can process the element, `false` otherwise
     */
    fun canHandle(element: PsiElement): Boolean

    /**
     * Performs the move refactoring.
     *
     * Moves the specified element to the target package/module and updates
     * all references throughout the project.
     *
     * @param project The IntelliJ project context
     * @param element The PSI element to move
     * @param targetPackage The target package/module path (format depends on language)
     * @param searchInComments Whether to also update occurrences in comments
     * @param searchInNonJavaFiles Whether to also update occurrences in non-code files
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
 * Extension point interface for language-specific extract method refactoring handlers.
 *
 * Language plugins implement this interface to provide extract method/function functionality.
 * Implementations are registered via plugin.xml extension points and discovered
 * at runtime, ensuring handlers are only loaded when their language plugin is available.
 *
 * ## Implementation Requirements
 * - Implementations must NOT import language-specific classes at compile time if those
 *   classes may not be available (e.g., Java PSI classes in WebStorm)
 * - Use reflection via [com.igorlink.claudeidetools.util.PsiReflectionUtils] for accessing
 *   language-specific PSI classes when needed
 * - Registration should be in the appropriate plugin-{lang}.xml file
 *
 * ## Example Registration (plugin-java.xml)
 * ```xml
 * <extensions defaultExtensionNs="com.igorlink.claudeidetools">
 *     <extractMethodHandler implementation="com.igorlink.claudeidetools.handlers.languages.java.JavaExtractMethodHandler"/>
 * </extensions>
 * ```
 *
 * @see RefactoringExtensionService Service that discovers and routes to handlers
 * @see SupportedLanguage Enumeration of supported languages
 */
interface ExtractMethodRefactoringExtension {

    companion object {
        /**
         * Extension point name for extract method refactoring handlers.
         *
         * Handlers register under this extension point and are discovered
         * at runtime by [RefactoringExtensionService].
         */
        val EP_NAME: ExtensionPointName<ExtractMethodRefactoringExtension> =
            ExtensionPointName.create("com.igorlink.claudeidetools.extractMethodHandler")
    }

    /**
     * Returns the language this handler supports.
     *
     * Used by [RefactoringExtensionService] to route requests to the appropriate handler.
     *
     * @return The supported language for this handler
     */
    fun getSupportedLanguage(): SupportedLanguage

    /**
     * Checks if this handler can process the given PSI file.
     *
     * Called before [extractFromFile] to determine if this handler should be used.
     * Implementations should check file type and any other language-specific criteria.
     *
     * @param psiFile The PSI file to check
     * @return `true` if this handler can process the file, `false` otherwise
     */
    fun canHandle(psiFile: PsiFile): Boolean

    /**
     * Performs the extract method refactoring.
     *
     * Extracts code from the specified range into a new method/function.
     * Automatically determines parameters, return values, and updates
     * the original code to call the new method.
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
