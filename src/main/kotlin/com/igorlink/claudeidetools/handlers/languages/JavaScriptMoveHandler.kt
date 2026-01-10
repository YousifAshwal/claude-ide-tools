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
 * Handler for JavaScript/TypeScript move refactoring operations.
 *
 * Supports moving ES6 modules, classes, functions, and variables to different files.
 * Uses reflection to access JavaScript plugin APIs to avoid compile-time dependency.
 *
 * ## Supported Elements
 * - ES6 classes and interfaces (TypeScript)
 * - Functions and arrow functions
 * - Variables and constants (let, const, var)
 * - Type definitions (TypeScript)
 *
 * ## Current Limitations
 * Full JS/TS move refactoring requires WebStorm or IDEA Ultimate with editor context.
 * Currently, this handler identifies the element but cannot perform the move.
 * Users should use the IDE UI for moves.
 *
 * @see com.igorlink.claudeidetools.handlers.MoveHandler The main handler that routes to this language-specific handler
 * @see PsiReflectionUtils Reflection utilities for accessing JavaScript PSI classes
 * @see PluginAvailability Plugin detection utility
 */
object JavaScriptMoveHandler {

    /** Base JavaScript element PSI class. */
    private const val JS_ELEMENT_CLASS = "com.intellij.lang.javascript.psi.JSElement"

    /** Type mappings for JavaScript/TypeScript declaration types. */
    private val DECLARATION_TYPE_MAPPINGS = mapOf(
        "Class" to "class",
        "Function" to "function",
        "Variable" to "variable",
        "VarStatement" to "declaration"
    )

    /** Class name patterns that indicate movable JS/TS declarations. */
    private val MOVABLE_PATTERNS = listOf("Class", "Function", "Variable", "VarStatement")

    /**
     * Attempts to move a JavaScript/TypeScript symbol to a different file.
     *
     * Currently identifies the element at the specified location but cannot
     * perform the actual move. Returns an informative error message with element details.
     *
     * @param project The IntelliJ project context
     * @param element The PSI element at the target location
     * @param targetPath The target file path (relative or absolute)
     * @param searchInComments Whether to also update occurrences in comments (unused in stub)
     * @param searchInNonJavaFiles Whether to also update occurrences in non-Java files (unused in stub)
     * @return [RefactoringResponse] with success status and message
     */
    @Suppress("UNUSED_PARAMETER")
    fun move(
        project: Project,
        element: PsiElement,
        targetPath: String,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): RefactoringResponse {
        // Find the movable JS/TS declaration using reflection with predicate
        val declaration = PsiReflectionUtils.findAncestorOfTypeWithPredicate(
            element,
            JS_ELEMENT_CLASS
        ) { className -> MOVABLE_PATTERNS.any { className.contains(it) } }

        if (declaration == null) {
            return RefactoringResponse(
                false,
                "No movable JavaScript/TypeScript declaration found at the specified location. " +
                "Supported: class, function, variable, type alias."
            )
        }

        val declarationName = PsiReflectionUtils.getElementName(declaration)
        val declarationType = PsiReflectionUtils.getElementTypeFromClassName(
            declaration, DECLARATION_TYPE_MAPPINGS, "symbol"
        )

        return RefactoringExecutor.executeWithCallback(
            project = project,
            commandName = "MCP Move JS/TS: $declarationName to $targetPath",
            timeoutSeconds = RefactoringTimeouts.MOVE
        ) { callback ->
            // JavaScript move refactoring requires WebStorm or IDEA Ultimate
            callback.failure(
                "JavaScript/TypeScript move refactoring requires WebStorm or IDEA Ultimate with JavaScript plugin. " +
                "Use the IDE UI for moving JS/TS declarations. " +
                "Declaration: $declarationType '$declarationName', target: $targetPath"
            )
        }
    }

    /**
     * Checks if the JavaScript plugin is available in the current IDE.
     *
     * Delegates to [PluginAvailability] for centralized, cached plugin detection.
     *
     * @return `true` if JavaScript plugin is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.JAVASCRIPT)
}
