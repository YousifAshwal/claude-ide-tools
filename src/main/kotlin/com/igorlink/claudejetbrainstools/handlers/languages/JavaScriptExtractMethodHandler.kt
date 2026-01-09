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
 * Handler for JavaScript/TypeScript extract function refactoring operations.
 *
 * Supports extracting code blocks into new functions (regular or arrow functions).
 * Uses reflection to access JavaScript plugin APIs to avoid compile-time dependency.
 *
 * ## Function Types
 * - Regular functions
 * - Arrow functions
 * - Methods within classes
 * - Async functions
 *
 * ## Current Limitations
 * Full JS/TS extract function requires WebStorm or IDEA Ultimate with editor context.
 * Currently, this handler cannot perform the extraction programmatically.
 * Users should use the IDE UI for extractions.
 *
 * @see com.igorlink.claudejetbrainstools.handlers.ExtractMethodHandler The main handler that routes to this language-specific handler
 * @see PsiReflectionUtils Reflection utilities for file type validation
 * @see PluginAvailability Plugin detection utility
 */
object JavaScriptExtractMethodHandler {

    /** JavaScript file PSI class for file type validation. */
    private const val JS_FILE_CLASS = "com.intellij.lang.javascript.psi.JSFile"

    /**
     * Entry point for extracting code from a JavaScript/TypeScript file.
     *
     * Validates that the file is a valid JS/TS file before delegating
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
        if (!PsiReflectionUtils.isFileOfType(psiFile, JS_FILE_CLASS)) {
            return RefactoringResponse(false, "File is not a valid JavaScript/TypeScript file")
        }
        return extract(project, psiFile, request)
    }

    /**
     * Attempts to extract a code block into a new JavaScript/TypeScript function.
     *
     * Currently cannot perform the extraction due to JavaScript plugin API requirements.
     * Returns an informative error message with the extraction details.
     *
     * @param project The IntelliJ project context
     * @param jsFile The JS/TS file containing the code
     * @param request The extraction request with positions and function name
     * @return [RefactoringResponse] with failure status and informative message
     */
    private fun extract(
        project: Project,
        jsFile: PsiFile,
        request: ExtractMethodRequest
    ): RefactoringResponse {
        return RefactoringExecutor.executeWithCallback(
            project = project,
            commandName = "MCP Extract JS/TS Function: ${request.methodName}"
        ) { callback ->
            // JavaScript extract method requires WebStorm or IDEA Ultimate
            callback.failure(
                "JavaScript/TypeScript extract function requires WebStorm or IDEA Ultimate with JavaScript plugin. " +
                "Use the IDE UI for extracting JS/TS functions. " +
                "Function name: ${request.methodName}, " +
                "range: ${request.startLine}:${request.startColumn} - ${request.endLine}:${request.endColumn}"
            )
        }
    }

    /**
     * Checks if the JavaScript plugin is available in the current IDE.
     *
     * Delegates to [PluginAvailability] for centralized, cached plugin detection.
     *
     * @return `true` if JavaScript plugin is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.JAVASCRIPT)
}
