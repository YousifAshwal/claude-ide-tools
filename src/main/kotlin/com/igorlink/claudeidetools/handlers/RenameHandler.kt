package com.igorlink.claudeidetools.handlers

import com.igorlink.claudeidetools.model.RefactoringResponse
import com.igorlink.claudeidetools.model.RenameRequest
import com.igorlink.claudeidetools.util.HandlerUtils
import com.igorlink.claudeidetools.util.RefactoringExecutor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.rename.RenameProcessor

/**
 * Handler for the `/rename` endpoint performing symbol rename refactoring.
 *
 * This handler renames any named code element (class, method, variable, parameter, etc.)
 * and automatically updates all references throughout the project. It uses IntelliJ's
 * [RenameProcessor] for headless (non-UI) rename operations.
 *
 * ## Supported Elements
 * - Classes, interfaces, enums, annotations
 * - Methods and functions
 * - Fields and properties
 * - Parameters
 * - Local variables
 * - Any element implementing [PsiNamedElement]
 *
 * ## Request Parameters
 * - `file`: Absolute path to the file containing the symbol
 * - `line`: Line number (1-based) of the symbol
 * - `column`: Column number (1-based) pointing to the symbol name
 * - `newName`: The new name for the symbol
 * - `project`: Optional project name for disambiguation
 *
 * ## Usage Example
 * ```json
 * {
 *   "file": "/path/to/MyClass.java",
 *   "line": 10,
 *   "column": 14,
 *   "newName": "BetterClassName"
 * }
 * ```
 *
 * @see RenameRequest The request data class
 * @see RefactoringResponse The response data class
 */
object RenameHandler {

    /**
     * Handles the rename refactoring request.
     *
     * Locates the PSI element at the specified location and performs a rename
     * operation using IntelliJ's refactoring infrastructure. All usages of the
     * symbol are updated automatically.
     *
     * @param request The rename request containing file location and new name
     * @return [RefactoringResponse] indicating success or failure with a message
     */
    fun handle(request: RenameRequest): RefactoringResponse {
        if (request.newName.isBlank()) {
            return RefactoringResponse(false, "New name cannot be empty")
        }

        return HandlerUtils.withElementLookup(
            file = request.file,
            line = request.line,
            column = request.column,
            project = request.project,
            errorResponseFactory = { message -> RefactoringResponse(false, message) }
        ) { context ->
            val element = context.element

            if (element !is PsiNamedElement) {
                return@withElementLookup RefactoringResponse(
                    false,
                    "Element at location is not renamable (not a named element)"
                )
            }

            performRename(
                context.project,
                element,
                request.newName,
                request.searchInComments,
                request.searchTextOccurrences
            )
        }
    }

    /**
     * Performs the actual rename operation using IntelliJ's RenameProcessor.
     *
     * Executes the rename within a write command action on the EDT (Event Dispatch Thread)
     * to ensure proper undo/redo support and thread safety.
     *
     * @param project The project containing the element
     * @param element The PSI element to rename
     * @param newName The new name for the element
     * @param searchInComments Whether to also rename occurrences in comments
     * @param searchTextOccurrences Whether to also rename text occurrences in strings
     * @return [RefactoringResponse] with success status and descriptive message
     */
    private fun performRename(
        project: Project,
        element: PsiNamedElement,
        newName: String,
        searchInComments: Boolean,
        searchTextOccurrences: Boolean
    ): RefactoringResponse {
        // Access to PSI element name requires ReadAction when called from background thread
        val oldName = ReadAction.compute<String, Throwable> { element.name } ?: "unknown"

        // RenameProcessor manages its own write action internally, so we only need EDT execution
        return RefactoringExecutor.executeOnEdt {
            val processor = RenameProcessor(
                project,
                element,
                newName,
                searchInComments,
                searchTextOccurrences
            )
            processor.setPreviewUsages(false)
            processor.run()
            RefactoringResponse(
                success = true,
                message = "Renamed '$oldName' to '$newName' in project '${project.name}'"
            )
        }
    }
}
