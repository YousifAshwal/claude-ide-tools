package com.igorlink.claudejetbrainstools.handlers.languages

import com.igorlink.claudejetbrainstools.model.ExtractMethodRequest
import com.igorlink.claudejetbrainstools.model.RefactoringResponse
import com.igorlink.claudejetbrainstools.util.PluginAvailability
import com.igorlink.claudejetbrainstools.util.SupportedLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Handler for Kotlin extract function refactoring operations.
 *
 * Supports extracting code blocks into new Kotlin functions, either as
 * top-level functions or as member functions within a class. Uses reflection
 * to access Kotlin plugin APIs to avoid compile-time dependency.
 *
 * ## Function Types
 * - Top-level functions
 * - Member functions (methods)
 * - Local functions (nested)
 * - Extension functions (when appropriate)
 *
 * ## Current Limitations
 * Kotlin extract function requires Editor context (text selection in the IDE).
 * The extraction engine cannot be invoked programmatically without an editor.
 * Users should use the IDE UI for Kotlin function extraction.
 *
 * @see com.igorlink.claudejetbrainstools.handlers.ExtractMethodHandler The main handler that routes to this language-specific handler
 * @see PluginAvailability Plugin detection utility
 */
object KotlinExtractMethodHandler {

    /**
     * Entry point for extracting code from a Kotlin file.
     *
     * Currently returns an informative error as Kotlin extraction
     * requires editor context that cannot be provided programmatically.
     *
     * @param project The IntelliJ project context
     * @param psiFile The file containing the code to extract
     * @param request The extraction request with range and function name
     * @return [RefactoringResponse] with failure status and informative message
     */
    fun extractFromFile(
        project: Project,
        psiFile: PsiFile,
        request: ExtractMethodRequest
    ): RefactoringResponse {
        return RefactoringResponse(
            false,
            "Kotlin extract function requires editor context and cannot be performed programmatically. " +
            "Use IntelliJ IDEA UI (Ctrl+Alt+M / Cmd+Alt+M) for extracting Kotlin functions. " +
            "Function name: ${request.methodName}, " +
            "range: ${request.startLine}:${request.startColumn} - ${request.endLine}:${request.endColumn}"
        )
    }

    /**
     * Checks if the Kotlin plugin is available in the current IDE.
     *
     * Delegates to [PluginAvailability] for centralized, cached plugin detection.
     *
     * @return `true` if Kotlin plugin is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)
}
