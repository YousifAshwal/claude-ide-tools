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
 * Handler for Python move refactoring operations.
 *
 * Supports moving Python classes, functions, and variables to different modules.
 * Uses reflection to access Python plugin APIs to avoid compile-time dependency.
 *
 * ## Supported Elements
 * - Classes
 * - Functions (def)
 * - Module-level variables
 *
 * ## Target Format
 * The target module should be specified as a dotted path, e.g.:
 * - `mypackage.utils` - Move to utils.py in mypackage
 * - `mypackage.subpackage.helpers` - Nested package
 *
 * ## Current Limitations
 * Full Python move refactoring requires PyCharm or IDEA with Python plugin.
 * Currently, this handler identifies the element but cannot perform the move.
 * Users should use the IDE UI for moves.
 *
 * @see com.igorlink.claudeidetools.handlers.MoveHandler The main handler that routes to this language-specific handler
 * @see PsiReflectionUtils Reflection utilities for accessing Python PSI classes
 * @see PluginAvailability Plugin detection utility
 */
object PythonMoveHandler {

    /** Base Python element PSI class. */
    private const val PY_ELEMENT_CLASS = "com.jetbrains.python.psi.PyElement"

    /** Type mappings for Python declaration types. */
    private val DECLARATION_TYPE_MAPPINGS = mapOf(
        "Class" to "class",
        "Function" to "function",
        "TargetExpression" to "variable"
    )

    /** Class name patterns that indicate movable Python declarations. */
    private val MOVABLE_PATTERNS = listOf("Class", "Function", "TargetExpression")

    /**
     * Attempts to move a Python symbol to a different module.
     *
     * Currently identifies the element at the specified location but cannot
     * perform the actual move. Returns an informative error message with element details.
     *
     * @param project The IntelliJ project context
     * @param element The PSI element at the target location
     * @param targetModule The target module path (e.g., "package.subpackage.module")
     * @param searchInComments Whether to also update occurrences in comments (unused in stub)
     * @param searchInNonJavaFiles Whether to also update occurrences in non-Java files (unused in stub)
     * @return [RefactoringResponse] with success status and message
     */
    @Suppress("UNUSED_PARAMETER")
    fun move(
        project: Project,
        element: PsiElement,
        targetModule: String,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): RefactoringResponse {
        // Find the movable Python declaration using reflection with predicate
        val declaration = PsiReflectionUtils.findAncestorOfTypeWithPredicate(
            element,
            PY_ELEMENT_CLASS
        ) { className -> MOVABLE_PATTERNS.any { className.contains(it) } }

        if (declaration == null) {
            return RefactoringResponse(
                false,
                "No movable Python declaration found at the specified location. " +
                "Supported: class, function, variable at module level."
            )
        }

        val declarationName = PsiReflectionUtils.getElementName(declaration)
        val declarationType = PsiReflectionUtils.getElementTypeFromClassName(
            declaration, DECLARATION_TYPE_MAPPINGS, "symbol"
        )

        return RefactoringExecutor.executeWithCallback(
            project = project,
            commandName = "MCP Move Python: $declarationName to $targetModule",
            timeoutSeconds = RefactoringTimeouts.MOVE
        ) { callback ->
            // Python move refactoring requires PyCharm or IDEA with Python plugin
            callback.failure(
                "Python move refactoring requires PyCharm or IDEA with Python plugin. " +
                "Use the IDE UI for moving Python declarations. " +
                "Declaration: $declarationType '$declarationName', target: $targetModule"
            )
        }
    }

    /**
     * Checks if the Python plugin is available in the current IDE.
     *
     * Delegates to [PluginAvailability] for centralized, cached plugin detection.
     *
     * @return `true` if Python plugin is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.PYTHON)
}
