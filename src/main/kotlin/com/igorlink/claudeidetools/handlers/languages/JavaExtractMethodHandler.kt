package com.igorlink.claudeidetools.handlers.languages

import com.igorlink.claudeidetools.model.ExtractMethodRequest
import com.igorlink.claudeidetools.model.RefactoringResponse
import com.igorlink.claudeidetools.util.PluginAvailability
import com.igorlink.claudeidetools.util.RefactoringExecutor
import com.igorlink.claudeidetools.util.SupportedLanguage
import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor

/**
 * Handler for Java extract method refactoring operations.
 *
 * Supports extracting selected code into a new method using IntelliJ's
 * ExtractMethodProcessor. Automatically determines parameters, return values,
 * and updates the original code to call the new method.
 *
 * ## Design Decision: Direct Imports vs Reflection
 *
 * Unlike other language handlers (Kotlin, Python, Go, etc.) that use reflection via
 * [com.igorlink.claudeidetools.util.PsiReflectionUtils], this handler uses direct imports
 * for Java-specific classes ([PsiJavaFile], [ExtractMethodProcessor], [CodeInsightUtil]).
 *
 * This approach is intentional:
 * 1. **Compile-time safety**: The Java plugin is declared as an optional dependency in
 *    `plugin.xml`. The ClassLoader will only load this file when the Java plugin is present.
 * 2. **Type safety**: Direct imports provide better IDE support, compile-time checks,
 *    and avoid runtime reflection errors.
 * 3. **Stability**: Java API classes are core IntelliJ Platform classes that rarely change.
 *
 * The [isAvailable] method uses [PluginAvailability] to check at runtime before this
 * handler is invoked, ensuring ClassNotFoundException is never thrown in production.
 *
 * ## Features
 * - Automatic parameter inference from variables used in selection
 * - Automatic return type detection
 * - Handles local variables that need to be returned
 * - Updates original code to call the extracted method
 *
 * ## Requirements
 * - The selected range must contain complete, extractable statements
 * - The code must be valid Java code
 * - Selection must be within a method or initializer
 *
 * @see com.igorlink.claudeidetools.handlers.ExtractMethodHandler The main handler that routes to this language-specific handler
 * @see PluginAvailability Plugin detection utility
 */
object JavaExtractMethodHandler {

    /**
     * Internal context holding pre-computed values for extract method refactoring.
     */
    private data class ExtractMethodContext(
        val elements: Array<PsiElement>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ExtractMethodContext
            return elements.contentEquals(other.elements)
        }

        override fun hashCode(): Int = elements.contentHashCode()
    }

    /**
     * Performs Java extract method refactoring.
     *
     * Finds statements in the specified range, validates they can be extracted,
     * and performs the extraction with automatic parameter and return value inference.
     *
     * @param project The IntelliJ project context
     * @param psiFile The Java file containing the code
     * @param request The extract method request with range and method name
     * @return [RefactoringResponse] indicating success or failure
     */
    fun extractFromFile(
        project: Project,
        psiFile: PsiFile,
        request: ExtractMethodRequest
    ): RefactoringResponse {
        if (psiFile !is PsiJavaFile) {
            return RefactoringResponse(false, "File is not a valid Java file")
        }

        // Consolidate all read actions into a single block for better performance
        val contextResult = ReadAction.compute<Result<ExtractMethodContext>, Throwable> {
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@compute Result.failure(IllegalStateException("Cannot get document for file"))

            // Validate line bounds
            if (request.startLine < 1 || request.startLine > document.lineCount) {
                return@compute Result.failure(IllegalArgumentException(
                    "Start line ${request.startLine} is out of bounds (1-${document.lineCount})"
                ))
            }
            if (request.endLine < 1 || request.endLine > document.lineCount) {
                return@compute Result.failure(IllegalArgumentException(
                    "End line ${request.endLine} is out of bounds (1-${document.lineCount})"
                ))
            }
            if (request.startLine > request.endLine) {
                return@compute Result.failure(IllegalArgumentException(
                    "Start line (${request.startLine}) must be <= end line (${request.endLine})"
                ))
            }

            // Validate column bounds
            val startLineLength = document.getLineEndOffset(request.startLine - 1) -
                document.getLineStartOffset(request.startLine - 1)
            if (request.startColumn < 1 || request.startColumn > startLineLength + 1) {
                return@compute Result.failure(IllegalArgumentException(
                    "Start column ${request.startColumn} is out of bounds for line ${request.startLine} (1-${startLineLength + 1})"
                ))
            }
            val endLineLength = document.getLineEndOffset(request.endLine - 1) -
                document.getLineStartOffset(request.endLine - 1)
            if (request.endColumn < 1 || request.endColumn > endLineLength + 1) {
                return@compute Result.failure(IllegalArgumentException(
                    "End column ${request.endColumn} is out of bounds for line ${request.endLine} (1-${endLineLength + 1})"
                ))
            }

            val startOffset = document.getLineStartOffset(request.startLine - 1) + (request.startColumn - 1)
            val endOffset = document.getLineStartOffset(request.endLine - 1) + (request.endColumn - 1)
            val elements = CodeInsightUtil.findStatementsInRange(psiFile, startOffset, endOffset)

            if (elements.isEmpty()) {
                return@compute Result.failure(IllegalStateException("No statements found in the specified range"))
            }

            Result.success(ExtractMethodContext(elements))
        }

        val context = contextResult.getOrElse { error ->
            return RefactoringResponse(false, error.message ?: "Unknown error during preparation")
        }

        return RefactoringExecutor.executeWithCallback(
            project = project,
            commandName = "MCP Extract Method: ${request.methodName}"
        ) { callback ->
            val processor = ExtractMethodProcessor(
                project,
                null, // editor
                context.elements,
                null, // forcedReturnType
                "MCP Extract Method",
                request.methodName,
                null // helpId
            )

            if (processor.prepare()) {
                processor.testPrepare()
                processor.doExtract()
                callback.success("Extracted method '${request.methodName}' in project '${project.name}'")
            } else {
                callback.failure("Cannot extract method from the selected code")
            }
        }
    }

    /**
     * Checks if the Java plugin is available in the current IDE.
     *
     * @return `true` if Java plugin is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.JAVA)
}
