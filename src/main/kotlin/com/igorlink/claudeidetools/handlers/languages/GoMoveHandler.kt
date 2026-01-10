package com.igorlink.claudeidetools.handlers.languages

import com.igorlink.claudeidetools.model.RefactoringResponse
import com.igorlink.claudeidetools.util.PluginAvailability
import com.igorlink.claudeidetools.util.PsiReflectionUtils
import com.igorlink.claudeidetools.util.RefactoringExecutor
import com.igorlink.claudeidetools.util.RefactoringTimeouts
import com.igorlink.claudeidetools.util.SupportedLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * Handler for Go move refactoring operations.
 *
 * Supports moving Go types, functions, and declarations to different packages.
 * Uses reflection to access Go plugin APIs to avoid compile-time dependency.
 *
 * ## Supported Elements
 * - Types (structs, interfaces, type aliases)
 * - Functions
 * - Methods
 * - Constants (const)
 * - Variables (var)
 *
 * ## Current Limitations
 * Full Go move refactoring requires GoLand or IDEA with Go plugin.
 * Currently, this handler identifies the declaration but cannot perform the move.
 * Users should use the IDE UI for moves.
 *
 * @see com.igorlink.claudeidetools.handlers.MoveHandler The main handler that routes to this language-specific handler
 * @see PsiReflectionUtils Reflection utilities for accessing Go PSI classes
 * @see PluginAvailability Plugin detection utility
 */
object GoMoveHandler {

    /** Base Go named element PSI class. */
    private const val GO_NAMED_ELEMENT_CLASS = "com.goide.psi.GoNamedElement"

    /** Type mappings for Go declaration types. */
    private val DECLARATION_TYPE_MAPPINGS = mapOf(
        "TypeSpec" to "type",
        "FunctionDeclaration" to "function",
        "MethodDeclaration" to "method",
        "ConstDefinition" to "const",
        "VarDefinition" to "var"
    )

    /** Class name patterns that indicate movable Go declarations. */
    private val MOVABLE_PATTERNS = listOf(
        "TypeSpec",
        "FunctionDeclaration",
        "MethodDeclaration",
        "ConstDefinition",
        "VarDefinition"
    )

    /**
     * Attempts to move a Go declaration to a different package.
     *
     * Currently identifies the declaration at the specified location but cannot
     * perform the actual move. Returns an informative error message with declaration details.
     *
     * @param project The IntelliJ project context
     * @param element The PSI element at the target location
     * @param targetPackage The target package path
     * @param searchInComments Whether to also update occurrences in comments (unused in stub)
     * @param searchInNonJavaFiles Whether to also update occurrences in non-Go files (unused in stub)
     * @return [RefactoringResponse] with success status and message
     */
    @Suppress("UNUSED_PARAMETER")
    fun move(
        project: Project,
        element: PsiElement,
        targetPackage: String,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): RefactoringResponse {
        // Find the movable Go declaration using reflection with predicate
        val declaration = PsiReflectionUtils.findAncestorOfTypeWithPredicate(
            element,
            GO_NAMED_ELEMENT_CLASS
        ) { className -> MOVABLE_PATTERNS.any { className.contains(it) } }

        if (declaration == null) {
            return RefactoringResponse(
                false,
                "No movable Go declaration found at the specified location. " +
                "Supported: type, function, const, var at package level."
            )
        }

        val declarationName = PsiReflectionUtils.getElementName(declaration)
        val declarationType = PsiReflectionUtils.getElementTypeFromClassName(
            declaration, DECLARATION_TYPE_MAPPINGS, "declaration"
        )

        return RefactoringExecutor.executeWithCallback(
            project = project,
            commandName = "MCP Move Go: $declarationName to $targetPackage",
            timeoutSeconds = RefactoringTimeouts.MOVE
        ) { callback ->
            // Go move refactoring requires GoLand or IDEA with Go plugin
            callback.failure(
                "Go move refactoring requires GoLand or IDEA with Go plugin. " +
                "Use the IDE UI for moving Go declarations. " +
                "Declaration: $declarationType '$declarationName', target: $targetPackage"
            )
        }
    }

    /**
     * Checks if the Go plugin is available in the current IDE.
     *
     * Delegates to [PluginAvailability] for centralized, cached plugin detection.
     *
     * @return `true` if Go plugin is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.GO)
}
