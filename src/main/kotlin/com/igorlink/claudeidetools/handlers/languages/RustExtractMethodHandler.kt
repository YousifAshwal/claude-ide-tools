package com.igorlink.claudeidetools.handlers.languages

import com.igorlink.claudeidetools.model.ExtractMethodRequest
import com.igorlink.claudeidetools.model.RefactoringResponse
import com.igorlink.claudeidetools.util.PluginAvailability
import com.igorlink.claudeidetools.util.PsiReflectionUtils
import com.igorlink.claudeidetools.util.RefactoringExecutor
import com.igorlink.claudeidetools.util.SupportedLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Handler for Rust extract function refactoring operations.
 *
 * Supports extracting code blocks into new Rust functions. Uses reflection
 * to access Rust plugin APIs to avoid compile-time dependency.
 *
 * ## Rust-Specific Considerations
 * - Ownership and borrowing semantics
 * - Lifetime annotations
 * - Mutable vs immutable borrows
 * - Error handling with Result types
 *
 * ## Current Limitations
 * Full Rust extract function requires RustRover or IDEA with intellij-rust plugin.
 * Currently, this handler cannot perform the extraction programmatically.
 * Users should use the IDE UI for extractions.
 *
 * @see com.igorlink.claudeidetools.handlers.ExtractMethodHandler The main handler that routes to this language-specific handler
 * @see PsiReflectionUtils Reflection utilities for file type validation
 * @see PluginAvailability Plugin detection utility
 */
object RustExtractMethodHandler {

    /** Rust file PSI class for file type validation. */
    private const val RS_FILE_CLASS = "org.rust.lang.core.psi.RsFile"

    /**
     * Entry point for extracting code from a Rust file.
     *
     * Validates that the file is a valid Rust file before delegating
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
        if (!PsiReflectionUtils.isFileOfType(psiFile, RS_FILE_CLASS)) {
            return RefactoringResponse(false, "File is not a valid Rust file")
        }
        return extract(project, psiFile, request)
    }

    /**
     * Attempts to extract a code block into a new Rust function.
     *
     * Currently cannot perform the extraction due to Rust plugin API requirements.
     * Returns an informative error message with the extraction details.
     *
     * @param project The IntelliJ project context
     * @param rsFile The Rust file containing the code
     * @param request The extraction request with positions and function name
     * @return [RefactoringResponse] with failure status and informative message
     */
    private fun extract(
        project: Project,
        rsFile: PsiFile,
        request: ExtractMethodRequest
    ): RefactoringResponse {
        return RefactoringExecutor.executeWithCallback(
            project = project,
            commandName = "MCP Extract Rust Function: ${request.methodName}"
        ) { callback ->
            // Rust extract function requires intellij-rust plugin
            callback.failure(
                "Rust extract function requires IDEA with Rust plugin (intellij-rust). " +
                "Use the IDE UI for extracting Rust functions. " +
                "Function name: ${request.methodName}, " +
                "range: ${request.startLine}:${request.startColumn} - ${request.endLine}:${request.endColumn}"
            )
        }
    }

    /**
     * Checks if the Rust plugin is available in the current IDE.
     *
     * Delegates to [PluginAvailability] for centralized, cached plugin detection.
     *
     * @return `true` if Rust plugin (intellij-rust) is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.RUST)
}
