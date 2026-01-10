package com.igorlink.claudeidetools.util

import com.igorlink.claudeidetools.services.PsiLocatorService
import com.igorlink.claudeidetools.services.PsiLookupResult
import com.igorlink.claudeidetools.services.ProjectLookupResult
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * Context object containing the results of a successful element lookup.
 *
 * Provides type-safe access to the project and PSI element after validation,
 * eliminating the need for null checks in handler code.
 *
 * @property project The IntelliJ project containing the element
 * @property element The PSI element at the specified location
 */
data class ElementLookupContext(
    val project: Project,
    val element: PsiElement
)

/**
 * Utility functions for common handler initialization patterns.
 *
 * This object eliminates duplicated boilerplate code across handlers by providing
 * reusable functions for:
 * - PSI element lookup with automatic dumb mode checking
 * - Project resolution with error handling
 * - Consistent error response generation
 *
 * ## Usage Patterns
 *
 * ### Element Lookup (for rename, move, find usages)
 * ```kotlin
 * return HandlerUtils.withElementLookup(
 *     file = request.file,
 *     line = request.line,
 *     column = request.column,
 *     project = request.project,
 *     errorResponseFactory = { RefactoringResponse(false, it) }
 * ) { context ->
 *     // Use context.project and context.element
 *     performRefactoring(context.element)
 * }
 * ```
 *
 * ### Project Lookup (for extract method with ranges)
 * ```kotlin
 * return HandlerUtils.withProjectLookup(
 *     file = request.file,
 *     projectHint = request.project,
 *     errorResponseFactory = { RefactoringResponse(false, it) }
 * ) { project ->
 *     // Work with project and file ranges
 *     extractMethod(project, request)
 * }
 * ```
 *
 * @see PsiLocatorService The underlying service for PSI/project resolution
 */
object HandlerUtils {

    /**
     * Performs project lookup with automatic dumb mode checking.
     *
     * This function encapsulates the common pattern of:
     * 1. Resolving the project for a given file path
     * 2. Checking if the IDE is in dumb mode (indexing)
     * 3. Returning an error response or executing the handler logic
     *
     * Use this for operations that work with file ranges (like extract method)
     * rather than single point element lookups.
     *
     * @param T The response type (e.g., RefactoringResponse)
     * @param file Absolute path to the file
     * @param projectHint Optional project name or base path to disambiguate
     * @param errorResponseFactory Factory function to create error response of type T
     * @param handler Lambda that receives the project and returns the response
     * @return Response of type T - either an error response or the handler's result
     */
    inline fun <T> withProjectLookup(
        file: String,
        projectHint: String?,
        errorResponseFactory: (String) -> T,
        handler: (Project) -> T
    ): T {
        val locator = service<PsiLocatorService>()

        val project = when (val result = locator.findProjectForFilePath(file, projectHint)) {
            is ProjectLookupResult.Error -> return errorResponseFactory(result.message)
            is ProjectLookupResult.Found -> result.project
        }

        // Check if project is in dumb mode (indexing)
        locator.checkDumbMode(project)?.let { errorMessage ->
            return errorResponseFactory(errorMessage)
        }

        return handler(project)
    }

    /**
     * Performs PSI element lookup with automatic dumb mode checking.
     *
     * This function encapsulates the common pattern of:
     * 1. Looking up a PSI element at the given location
     * 2. Checking if the IDE is in dumb mode (indexing)
     * 3. Returning an error response or executing the handler logic
     *
     * @param T The response type (e.g., RefactoringResponse, FindUsagesResponse)
     * @param file Absolute path to the file
     * @param line Line number (1-based)
     * @param column Column number (1-based)
     * @param project Optional project name or base path to disambiguate
     * @param errorResponseFactory Factory function to create error response of type T
     * @param handler Lambda that receives the lookup context and returns the response
     * @return Response of type T - either an error response or the handler's result
     */
    inline fun <T> withElementLookup(
        file: String,
        line: Int,
        column: Int,
        project: String?,
        errorResponseFactory: (String) -> T,
        handler: (ElementLookupContext) -> T
    ): T {
        val locator = service<PsiLocatorService>()

        return when (val result = locator.findElementAt(file, line, column, project)) {
            is PsiLookupResult.Error -> errorResponseFactory(result.message)
            is PsiLookupResult.Found -> {
                val (foundProject, element) = result

                // Check if project is in dumb mode (indexing)
                locator.checkDumbMode(foundProject)?.let { errorMessage ->
                    return errorResponseFactory(errorMessage)
                }

                handler(ElementLookupContext(foundProject, element))
            }
        }
    }
}
