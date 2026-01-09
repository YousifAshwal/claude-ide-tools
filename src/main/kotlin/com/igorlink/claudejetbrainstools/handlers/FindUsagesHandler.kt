package com.igorlink.claudejetbrainstools.handlers

import com.igorlink.claudejetbrainstools.model.FindUsagesRequest
import com.igorlink.claudejetbrainstools.model.FindUsagesResponse
import com.igorlink.claudejetbrainstools.model.Usage
import com.igorlink.claudejetbrainstools.util.HandlerUtils
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.searches.ReferencesSearch

/**
 * Handler for the `/findUsages` endpoint to locate all usages of a symbol.
 *
 * This handler finds all references to a symbol (class, method, variable, etc.)
 * throughout the project. It uses IntelliJ's [ReferencesSearch] API to perform
 * a comprehensive search across all project files.
 *
 * ## Returned Information
 * For each usage found, the response includes:
 * - `file`: Absolute path to the file containing the usage
 * - `line`: Line number (1-based)
 * - `column`: Column number (1-based)
 * - `preview`: The trimmed line of code containing the usage
 *
 * ## Request Parameters
 * - `file`: Absolute path to the file containing the symbol
 * - `line`: Line number (1-based) of the symbol
 * - `column`: Column number (1-based) pointing to the symbol name
 * - `project`: Optional project name for disambiguation
 *
 * ## Usage Example
 * ```json
 * {
 *   "file": "/path/to/MyClass.java",
 *   "line": 10,
 *   "column": 14
 * }
 * ```
 *
 * Response:
 * ```json
 * {
 *   "success": true,
 *   "message": "Found 5 usage(s) in project 'MyProject'",
 *   "usages": [
 *     {"file": "/path/to/Other.java", "line": 25, "column": 10, "preview": "MyClass instance = new MyClass();"}
 *   ]
 * }
 * ```
 *
 * @see FindUsagesRequest The request data class
 * @see FindUsagesResponse The response data class
 * @see Usage The individual usage data class
 */
object FindUsagesHandler {
    private val logger = Logger.getInstance(FindUsagesHandler::class.java)

    /**
     * Handles the find usages request.
     *
     * Locates the PSI element at the specified location and searches for all
     * references to it throughout the project. The search is performed within
     * a read action to ensure thread safety.
     *
     * @param request The find usages request containing file location
     * @return [FindUsagesResponse] with success status, message, and list of usages
     */
    fun handle(request: FindUsagesRequest): FindUsagesResponse {
        return HandlerUtils.withElementLookup(
            file = request.file,
            line = request.line,
            column = request.column,
            project = request.project,
            errorResponseFactory = { message -> FindUsagesResponse(false, message) }
        ) { context ->
            val usages = mutableListOf<Usage>()

            try {
                ReadAction.run<Throwable> {
                    val references = ReferencesSearch.search(context.element).findAll()

                    for (ref in references) {
                        val refElement = ref.element ?: continue
                        val psiFile = refElement.containingFile ?: continue
                        val virtualFile = psiFile.virtualFile ?: continue

                        val document = PsiDocumentManager.getInstance(context.project)
                            .getDocument(psiFile) ?: continue

                        val lineNumber = document.getLineNumber(refElement.textOffset)
                        val lineStart = document.getLineStartOffset(lineNumber)
                        val lineEnd = document.getLineEndOffset(lineNumber)
                        val lineText = document.getText(TextRange(lineStart, lineEnd))

                        usages.add(
                            Usage(
                                file = virtualFile.path,
                                line = lineNumber + 1, // Convert to 1-based
                                column = refElement.textOffset - lineStart + 1, // Convert to 1-based
                                preview = lineText.trim()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error("Find usages failed", e)
                return@withElementLookup FindUsagesResponse(false, "Find usages failed: ${e.message}")
            }

            FindUsagesResponse(
                success = true,
                message = "Found ${usages.size} usage(s) in project '${context.project.name}'",
                usages = usages
            )
        }
    }
}
