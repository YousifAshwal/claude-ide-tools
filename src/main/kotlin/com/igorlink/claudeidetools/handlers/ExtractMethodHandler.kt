package com.igorlink.claudeidetools.handlers

import com.igorlink.claudeidetools.model.ExtractMethodRequest
import com.igorlink.claudeidetools.model.RefactoringResponse
import com.igorlink.claudeidetools.util.HandlerUtils
import com.igorlink.claudeidetools.util.LanguageDetector
import com.igorlink.claudeidetools.util.LanguageHandlerRegistry
import com.igorlink.claudeidetools.util.SupportedLanguage
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

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
 * @see JavaExtractMethodHandler Language-specific handler for Java
 * @see KotlinExtractMethodHandler Language-specific handler for Kotlin
 * @see JavaScriptExtractMethodHandler Language-specific handler for JS/TS
 * @see PythonExtractMethodHandler Language-specific handler for Python
 * @see GoExtractMethodHandler Language-specific handler for Go
 * @see RustExtractMethodHandler Language-specific handler for Rust
 */
object ExtractMethodHandler {

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
     * Uses [LanguageHandlerRegistry] for centralized routing to all language-specific handlers
     * including Java.
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
        // Unknown language
        if (language == SupportedLanguage.UNKNOWN) {
            return RefactoringResponse(
                false,
                "Unsupported language for extract method refactoring. " +
                "Supported: Java, Kotlin, TypeScript, JavaScript, Python, Go, Rust."
            )
        }

        // Delegate to LanguageHandlerRegistry for all languages including Java
        return LanguageHandlerRegistry.extractMethod(language, project, psiFile, request)
            ?: RefactoringResponse(
                false,
                "No handler registered for language: ${LanguageDetector.getLanguageName(language)}"
            )
    }
}
