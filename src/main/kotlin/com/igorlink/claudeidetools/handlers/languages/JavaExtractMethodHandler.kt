package com.igorlink.claudeidetools.handlers.languages

import com.igorlink.claudeidetools.model.ExtractMethodRequest
import com.igorlink.claudeidetools.model.RefactoringResponse
import com.igorlink.claudeidetools.util.PluginAvailability
import com.igorlink.claudeidetools.util.PsiReflectionUtils
import com.igorlink.claudeidetools.util.RefactoringExecutor
import com.igorlink.claudeidetools.util.SupportedLanguage
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Handler for Java extract method refactoring operations.
 *
 * Supports extracting selected code into a new method using IntelliJ's
 * ExtractMethodProcessor. Automatically determines parameters, return values,
 * and updates the original code to call the new method.
 *
 * ## Design Decision: Reflection-Based Access
 *
 * This handler uses reflection to access Java-specific classes (PsiJavaFile,
 * ExtractMethodProcessor, CodeInsightUtil) to avoid ClassNotFoundException
 * in IDEs without Java plugin (WebStorm, PhpStorm, etc.).
 *
 * The [isAvailable] method uses [PluginAvailability] to check at runtime before this
 * handler is invoked.
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

    private const val PSI_JAVA_FILE = "com.intellij.psi.PsiJavaFile"
    private const val CODE_INSIGHT_UTIL = "com.intellij.codeInsight.CodeInsightUtil"
    private const val EXTRACT_METHOD_PROCESSOR = "com.intellij.refactoring.extractMethod.ExtractMethodProcessor"

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
        // Check if file is a Java file via reflection
        if (!PsiReflectionUtils.isFileOfType(psiFile, PSI_JAVA_FILE)) {
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

            // Find statements in range via reflection
            val elements = findStatementsInRange(psiFile, startOffset, endOffset)

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
            val success = runExtractMethodProcessor(project, context.elements, request.methodName)
            if (success) {
                callback.success("Extracted method '${request.methodName}' in project '${project.name}'")
            } else {
                callback.failure("Cannot extract method from the selected code")
            }
        }
    }

    /**
     * Finds statements in the specified range using CodeInsightUtil via reflection.
     */
    @Suppress("UNCHECKED_CAST")
    private fun findStatementsInRange(psiFile: PsiFile, startOffset: Int, endOffset: Int): Array<PsiElement> {
        return try {
            val codeInsightClass = Class.forName(CODE_INSIGHT_UTIL)
            val method = codeInsightClass.getMethod(
                "findStatementsInRange",
                PsiFile::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val result = method.invoke(null, psiFile, startOffset, endOffset)
            result as? Array<PsiElement> ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
    }

    /**
     * Creates and runs ExtractMethodProcessor via reflection.
     */
    private fun runExtractMethodProcessor(
        project: Project,
        elements: Array<PsiElement>,
        methodName: String
    ): Boolean {
        return try {
            val processorClass = Class.forName(EXTRACT_METHOD_PROCESSOR)

            // Find constructor: (Project, Editor, PsiElement[], PsiType, String, String, String)
            // We use: (Project, null, elements, null, "MCP Extract Method", methodName, null)
            val constructor = processorClass.constructors.find { c ->
                c.parameterCount == 7 &&
                c.parameterTypes[0] == Project::class.java
            } ?: return false

            val processor = constructor.newInstance(
                project,
                null,  // editor
                elements,
                null,  // forcedReturnType
                "MCP Extract Method",
                methodName,
                null   // helpId
            )

            // Call prepare()
            val prepareMethod = processorClass.getMethod("prepare")
            val prepared = prepareMethod.invoke(processor) as Boolean

            if (!prepared) {
                return false
            }

            // Call testPrepare()
            val testPrepareMethod = processorClass.getMethod("testPrepare")
            testPrepareMethod.invoke(processor)

            // Call doExtract()
            val doExtractMethod = processorClass.getMethod("doExtract")
            doExtractMethod.invoke(processor)

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the Java plugin is available in the current IDE.
     *
     * @return `true` if Java plugin is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.JAVA)
}
