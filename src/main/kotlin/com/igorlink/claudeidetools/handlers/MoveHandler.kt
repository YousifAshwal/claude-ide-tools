package com.igorlink.claudeidetools.handlers

import com.igorlink.claudeidetools.model.MoveRequest
import com.igorlink.claudeidetools.model.RefactoringResponse
import com.igorlink.claudeidetools.util.HandlerUtils
import com.igorlink.claudeidetools.util.LanguageDetector
import com.igorlink.claudeidetools.util.LanguageHandlerRegistry
import com.igorlink.claudeidetools.util.SupportedLanguage
import com.intellij.openapi.project.Project

/**
 * Handler for the `/move` endpoint performing move class/symbol refactoring.
 *
 * This handler moves code elements to different packages/modules and automatically
 * updates all references throughout the project. It supports multiple programming
 * languages with automatic detection and routing to language-specific handlers.
 *
 * ## Supported Languages and Elements
 *
 * | Language | Movable Elements | Target Format |
 * |----------|------------------|---------------|
 * | Java | Classes | Package name (e.g., `com.example.newpkg`) |
 * | Kotlin | Classes, objects, functions | Package name |
 * | TypeScript/JavaScript | Classes, functions, variables | File path |
 * | Python | Classes, functions | Module path (e.g., `package.module`) |
 * | Go | Types, functions | Package path |
 * | Rust | Structs, enums, functions, traits | Module path (e.g., `crate::utils`) |
 *
 * ## Request Parameters
 * - `file`: Absolute path to the file containing the element
 * - `line`: Line number (1-based) of the element
 * - `column`: Column number (1-based) pointing to the element name
 * - `targetPackage`: Target package/module path (format depends on language)
 * - `project`: Optional project name for disambiguation
 *
 * ## Java Usage Example
 * ```json
 * {
 *   "file": "/path/to/MyClass.java",
 *   "line": 5,
 *   "column": 14,
 *   "targetPackage": "com.example.newpackage"
 * }
 * ```
 *
 * ## Important Notes
 * - The target package must already exist for Java moves
 * - Language detection is automatic based on file extension and PSI type
 * - Some language handlers may require additional IDE plugins
 *
 * @see MoveRequest The request data class
 * @see RefactoringResponse The response data class
 * @see JavaMoveHandler Language-specific handler for Java
 * @see KotlinMoveHandler Language-specific handler for Kotlin
 * @see JavaScriptMoveHandler Language-specific handler for JS/TS
 * @see PythonMoveHandler Language-specific handler for Python
 * @see GoMoveHandler Language-specific handler for Go
 * @see RustMoveHandler Language-specific handler for Rust
 */
object MoveHandler {

    /**
     * Handles the move refactoring request.
     *
     * Detects the programming language of the file and routes the request
     * to the appropriate language-specific handler via [LanguageHandlerRegistry].
     *
     * @param request The move request containing file location and target package
     * @return [RefactoringResponse] indicating success or failure with a message
     */
    fun handle(request: MoveRequest): RefactoringResponse {
        if (request.targetPackage.isBlank()) {
            return RefactoringResponse(false, "Target package/module cannot be empty")
        }

        return HandlerUtils.withElementLookup(
            file = request.file,
            line = request.line,
            column = request.column,
            project = request.project,
            errorResponseFactory = { message -> RefactoringResponse(false, message) }
        ) { context ->
            // Detect language and route to appropriate handler
            val language = LanguageDetector.detect(context.element.containingFile)

            routeToLanguageHandler(
                context.project,
                context.element,
                language,
                request.targetPackage,
                request.searchInComments,
                request.searchInNonJavaFiles
            )
        }
    }

    /**
     * Routes the move request to the appropriate language-specific handler.
     *
     * Uses [LanguageHandlerRegistry] for centralized routing to all language-specific handlers.
     *
     * @param project The IntelliJ project context
     * @param element The PSI element to move
     * @param language The detected programming language
     * @param targetPackage The target package/module path
     * @param searchInComments Whether to also update occurrences in comments
     * @param searchInNonJavaFiles Whether to also update occurrences in non-Java files
     * @return [RefactoringResponse] with the operation result
     */
    private fun routeToLanguageHandler(
        project: Project,
        element: com.intellij.psi.PsiElement,
        language: SupportedLanguage,
        targetPackage: String,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): RefactoringResponse {
        // Unknown language
        if (language == SupportedLanguage.UNKNOWN) {
            return RefactoringResponse(
                false,
                "Unsupported language for move refactoring. " +
                "Supported: Java, Kotlin, TypeScript, JavaScript, Python, Go, Rust."
            )
        }

        // Delegate to LanguageHandlerRegistry for all languages
        return LanguageHandlerRegistry.move(
            language, project, element, targetPackage, searchInComments, searchInNonJavaFiles
        ) ?: RefactoringResponse(
            false,
            "No handler registered for language: ${LanguageDetector.getLanguageName(language)}"
        )
    }
}
