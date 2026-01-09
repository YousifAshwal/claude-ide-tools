package com.igorlink.claudejetbrainstools.handlers.languages

import com.igorlink.claudejetbrainstools.model.ExtractMethodRequest
import com.igorlink.claudejetbrainstools.model.RefactoringResponse
import com.igorlink.claudejetbrainstools.util.PluginAvailability
import com.igorlink.claudejetbrainstools.util.PsiReflectionUtils
import com.igorlink.claudejetbrainstools.util.RefactoringExecutor
import com.igorlink.claudejetbrainstools.util.SupportedLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Handler for Go extract function refactoring operations.
 *
 * Supports extracting code blocks into new Go functions. Uses reflection
 * to access Go plugin APIs to avoid compile-time dependency.
 *
 * ## Go-Specific Features
 * - Multiple return value support
 * - Named return values
 * - Error handling patterns (error as last return)
 * - Receiver methods (for methods within types)
 *
 * ## Current Limitations
 * Full Go extract function requires GoLand or IDEA with Go plugin.
 * Currently, this handler cannot perform the extraction programmatically.
 * Users should use the IDE UI for extractions.
 *
 * @see com.igorlink.claudejetbrainstools.handlers.ExtractMethodHandler The main handler that routes to this language-specific handler
 * @see PsiReflectionUtils Reflection utilities for file type validation
 * @see PluginAvailability Plugin detection utility
 */
object GoExtractMethodHandler {

    /** Go file PSI class for file type validation. */
    private const val GO_FILE_CLASS = "com.goide.psi.GoFile"

    /**
     * Entry point for extracting code from a Go file.
     *
     * Validates that the file is a valid Go file before delegating
     * to the actual extraction logic.
     *
     * @param project The IntelliJ project context
     * @param psiFile The file containing the code to extract
     * @param request The extraction request with range and function name
     * @return [RefactoringResponse] with success status and message
     */
    fun extractFromFile(
        project: Project,
        psiFile: PsiFile,
        request: ExtractMethodRequest
    ): RefactoringResponse {
        if (!PsiReflectionUtils.isFileOfType(psiFile, GO_FILE_CLASS)) {
            return RefactoringResponse(false, "File is not a valid Go file")
        }
        return extract(project, psiFile, request)
    }

    /**
     * Attempts to extract a code block into a new Go function.
     *
     * Currently cannot perform the extraction due to Go plugin API requirements.
     * Returns an informative error message with the extraction details.
     *
     * @param project The IntelliJ project context
     * @param goFile The Go file containing the code
     * @param request The extraction request with positions and function name
     * @return [RefactoringResponse] with failure status and informative message
     */
    private fun extract(
        project: Project,
        goFile: PsiFile,
        request: ExtractMethodRequest
    ): RefactoringResponse {
        return RefactoringExecutor.executeWithCallback(
            project = project,
            commandName = "MCP Extract Go Function: ${request.methodName}"
        ) { callback ->
            // Go extract function requires GoLand or IDEA with Go plugin
            callback.failure(
                "Go extract function requires GoLand or IDEA with Go plugin. " +
                "Use the IDE UI for extracting Go functions. " +
                "Function name: ${request.methodName}, " +
                "range: ${request.startLine}:${request.startColumn} - ${request.endLine}:${request.endColumn}"
            )
        }
    }

    /**
     * Checks if the Go plugin is available in the current IDE.
     *
     * Delegates to [PluginAvailability] for centralized, cached plugin detection.
     *
     * @return `true` if Go plugin is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.GO)
}
