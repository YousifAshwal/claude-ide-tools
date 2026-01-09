package com.igorlink.claudejetbrainstools.handlers

import com.igorlink.claudejetbrainstools.model.ExtractMethodRequest
import com.igorlink.claudejetbrainstools.model.RefactoringResponse
import com.igorlink.claudejetbrainstools.util.HandlerUtils
import com.igorlink.claudejetbrainstools.util.LanguageDetector
import com.igorlink.claudejetbrainstools.util.LanguageHandlerRegistry
import com.igorlink.claudejetbrainstools.util.RefactoringExecutor
import com.igorlink.claudejetbrainstools.util.SupportedLanguage
import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor

/**
 * Handler for the `/extractMethod` endpoint performing extract method/function refactoring.
 *
 * This handler extracts a selected range of code into a new method or function,
 * automatically determining parameters, return values, and updating the original
 * code to call the new method.
 *
 * ## Supported Languages and Features
 *
 * | Language | Function Type | Special Features |
 * |----------|---------------|------------------|
 * | Java | Instance/static method | Automatic parameter/return inference |
 * | Kotlin | Top-level or member function | Expression body support |
 * | TypeScript/JavaScript | Function or arrow function | Modern ES6+ syntax |
 * | Python | Function | Proper indentation handling |
 * | Go | Function | Multiple return value support |
 * | Rust | Function | Ownership and lifetime handling |
 *
 * ## Request Parameters
 * - `file`: Absolute path to the file containing the code
 * - `startLine`: Starting line number (1-based) of the code range
 * - `startColumn`: Starting column number (1-based)
 * - `endLine`: Ending line number (1-based) of the code range
 * - `endColumn`: Ending column number (1-based)
 * - `methodName`: Name for the new method/function
 * - `project`: Optional project name for disambiguation
 *
 * ## Usage Example
 * ```json
 * {
 *   "file": "/path/to/MyClass.java",
 *   "startLine": 15,
 *   "startColumn": 9,
 *   "endLine": 20,
 *   "endColumn": 10,
 *   "methodName": "calculateTotal"
 * }
 * ```
 *
 * ## Important Notes
 * - The selected range must contain complete, extractable statements
 * - Variable dependencies are automatically converted to parameters
 * - Return values are inferred from the extracted code
 * - Some languages may require additional IDE plugins
 *
 * @see ExtractMethodRequest The request data class
 * @see RefactoringResponse The response data class
 * @see KotlinExtractMethodHandler Language-specific handler for Kotlin
 * @see JavaScriptExtractMethodHandler Language-specific handler for JS/TS
 * @see PythonExtractMethodHandler Language-specific handler for Python
 * @see GoExtractMethodHandler Language-specific handler for Go
 * @see RustExtractMethodHandler Language-specific handler for Rust
 */
object ExtractMethodHandler {

    /**
     * Internal context holding pre-computed values for extract method refactoring.
     *
     * @property elements Array of PSI elements (statements) to be extracted
     */
    private data class ExtractMethodContext(
        val elements: Array<PsiElement>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ExtractMethodContext

            if (!elements.contentEquals(other.elements)) return false

            return true
        }

        override fun hashCode(): Int {
            return elements.contentHashCode()
        }
    }

    /**
     * Handles the extract method refactoring request.
     *
     * Parses the file, identifies statements in the specified range, detects the
     * programming language, and routes to the appropriate language-specific handler.
     *
     * @param request The extract method request containing file, range, and method name
     * @return [RefactoringResponse] indicating success or failure with a message
     */
    fun handle(request: ExtractMethodRequest): RefactoringResponse {
        if (request.methodName.isBlank()) {
            return RefactoringResponse(false, "Method name cannot be empty")
        }

        return HandlerUtils.withProjectLookup(
            file = request.file,
            projectHint = request.project,
            errorResponseFactory = { message -> RefactoringResponse(false, message) }
        ) { project ->
            val normalizedPath = request.file.replace("\\", "/")
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(normalizedPath)
                ?: return@withProjectLookup RefactoringResponse(false, "File not found: ${request.file}")

            val psiFile = ReadAction.compute<PsiFile?, Throwable> {
                PsiManager.getInstance(project).findFile(virtualFile)
            }

            if (psiFile == null) {
                return@withProjectLookup RefactoringResponse(false, "Cannot parse file: ${request.file}")
            }

            // Detect language and route to appropriate handler
            val language = LanguageDetector.detect(psiFile)
            routeToLanguageHandler(project, psiFile, language, request)
        }
    }

    /**
     * Routes the extract method request to the appropriate language-specific handler.
     *
     * Uses [LanguageHandlerRegistry] for centralized routing to language-specific handlers.
     * Java is handled directly in this class; other languages are delegated via the registry.
     *
     * @param project The IntelliJ project context
     * @param psiFile The file containing the code to extract
     * @param language The detected programming language
     * @param request The original extract method request
     * @return [RefactoringResponse] with the operation result
     */
    private fun routeToLanguageHandler(
        project: Project,
        psiFile: PsiFile,
        language: SupportedLanguage,
        request: ExtractMethodRequest
    ): RefactoringResponse {
        // Java is handled directly in this class
        if (language == SupportedLanguage.JAVA) {
            return if (psiFile is PsiJavaFile) {
                performExtractMethod(project, psiFile, request)
            } else {
                RefactoringResponse(false, "File is not a valid Java file")
            }
        }

        // Unknown language
        if (language == SupportedLanguage.UNKNOWN) {
            return RefactoringResponse(
                false,
                "Unsupported language for extract method refactoring. " +
                "Supported: Java, Kotlin, TypeScript, JavaScript, Python, Go, Rust."
            )
        }

        // Delegate to LanguageHandlerRegistry for other languages
        return LanguageHandlerRegistry.extractMethod(language, project, psiFile, request)
            ?: RefactoringResponse(
                false,
                "No handler registered for language: ${LanguageDetector.getLanguageName(language)}"
            )
    }

    /**
     * Performs Java extract method refactoring using IntelliJ's ExtractMethodProcessor.
     *
     * Finds statements in the specified range, validates they can be extracted,
     * and performs the extraction with automatic parameter and return value inference.
     *
     * @param project The IntelliJ project context
     * @param psiFile The Java file containing the code
     * @param request The extract method request with range and method name
     * @return [RefactoringResponse] indicating success or failure
     */
    private fun performExtractMethod(
        project: Project,
        psiFile: PsiJavaFile,
        request: ExtractMethodRequest
    ): RefactoringResponse {
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
}
