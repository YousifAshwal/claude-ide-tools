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
 * Handler for Python extract function refactoring operations.
 *
 * Supports extracting code blocks into new Python functions. Uses reflection
 * to access Python plugin APIs to avoid compile-time dependency.
 *
 * ## Function Types
 * - Regular functions (def)
 * - Methods within classes
 * - Async functions (async def)
 *
 * ## Python-Specific Considerations
 * - Proper indentation handling
 * - Multiple return value support (tuples)
 * - Decorator preservation
 *
 * ## Current Limitations
 * Full Python extract function requires PyCharm or IDEA with Python plugin.
 * Currently, this handler cannot perform the extraction programmatically.
 * Users should use the IDE UI for extractions.
 *
 * @see com.igorlink.claudejetbrainstools.handlers.ExtractMethodHandler The main handler that routes to this language-specific handler
 * @see PsiReflectionUtils Reflection utilities for file type validation
 * @see PluginAvailability Plugin detection utility
 */
object PythonExtractMethodHandler {

    /** Python file PSI class for file type validation. */
    private const val PY_FILE_CLASS = "com.jetbrains.python.psi.PyFile"

    /**
     * Entry point for extracting code from a Python file.
     *
     * Validates that the file is a valid Python file before delegating
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
        if (!PsiReflectionUtils.isFileOfType(psiFile, PY_FILE_CLASS)) {
            return RefactoringResponse(false, "File is not a valid Python file")
        }
        return extract(project, psiFile, request)
    }

    /**
     * Attempts to extract a code block into a new Python function.
     *
     * Currently cannot perform the extraction due to Python plugin API requirements.
     * Returns an informative error message with the extraction details.
     *
     * @param project The IntelliJ project context
     * @param pyFile The Python file containing the code
     * @param request The extraction request with positions and function name
     * @return [RefactoringResponse] with failure status and informative message
     */
    private fun extract(
        project: Project,
        pyFile: PsiFile,
        request: ExtractMethodRequest
    ): RefactoringResponse {
        return RefactoringExecutor.executeWithCallback(
            project = project,
            commandName = "MCP Extract Python Function: ${request.methodName}"
        ) { callback ->
            // Python extract method requires PyCharm or IDEA with Python plugin
            callback.failure(
                "Python extract function requires PyCharm or IDEA with Python plugin. " +
                "Use the IDE UI for extracting Python functions. " +
                "Function name: ${request.methodName}, " +
                "range: ${request.startLine}:${request.startColumn} - ${request.endLine}:${request.endColumn}"
            )
        }
    }

    /**
     * Checks if the Python plugin is available in the current IDE.
     *
     * Delegates to [PluginAvailability] for centralized, cached plugin detection.
     *
     * @return `true` if Python plugin is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.PYTHON)
}
