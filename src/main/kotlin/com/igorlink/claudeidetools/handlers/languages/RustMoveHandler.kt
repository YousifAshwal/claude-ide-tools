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
 * Handler for Rust move refactoring operations.
 *
 * Supports moving Rust items to different modules. Uses reflection
 * to access Rust plugin APIs to avoid compile-time dependency.
 *
 * ## Supported Items
 * - Structs
 * - Enums
 * - Functions (fn)
 * - Traits
 * - Impl blocks
 * - Constants (const)
 * - Type aliases
 * - Modules (mod)
 *
 * ## Target Format
 * The target module should be specified as a Rust path:
 * - `crate::utils` - Move to utils module in same crate
 * - `crate::utils::helpers` - Nested module
 *
 * ## Current Limitations
 * Full Rust move refactoring requires RustRover or IDEA with intellij-rust plugin.
 * Currently, this handler identifies the item but cannot perform the move.
 * Users should use the IDE UI for moves.
 *
 * @see com.igorlink.claudeidetools.handlers.MoveHandler The main handler that routes to this language-specific handler
 * @see PsiReflectionUtils Reflection utilities for accessing Rust PSI classes
 * @see PluginAvailability Plugin detection utility
 */
object RustMoveHandler {

    /** Base Rust item element PSI class. */
    private const val RS_ITEM_ELEMENT_CLASS = "org.rust.lang.core.psi.RsItemElement"

    /** Type mappings for Rust item types. */
    private val ITEM_TYPE_MAPPINGS = mapOf(
        "StructItem" to "struct",
        "EnumItem" to "enum",
        "Function" to "fn",
        "TraitItem" to "trait",
        "ImplItem" to "impl",
        "Constant" to "const",
        "TypeAlias" to "type",
        "ModItem" to "mod"
    )

    /**
     * Attempts to move a Rust item to a different module.
     *
     * Currently identifies the item at the specified location but cannot
     * perform the actual move. Returns an informative error message with item details.
     *
     * @param project The IntelliJ project context
     * @param element The PSI element at the target location
     * @param targetModule The target module path (e.g., "crate::utils::helpers")
     * @param searchInComments Whether to also update occurrences in comments (unused in stub)
     * @param searchInNonJavaFiles Whether to also update occurrences in non-Rust files (unused in stub)
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
        // Find the movable Rust item using reflection
        // RsItemElement is a base class for all movable items, no predicate needed
        val item = PsiReflectionUtils.findAncestorOfType(element, RS_ITEM_ELEMENT_CLASS)

        if (item == null) {
            return RefactoringResponse(
                false,
                "No movable Rust item found at the specified location. " +
                "Supported: struct, enum, fn, trait, impl, const, static, type alias, mod."
            )
        }

        val itemName = PsiReflectionUtils.getElementName(item)
        val itemType = PsiReflectionUtils.getElementTypeFromClassName(item, ITEM_TYPE_MAPPINGS, "item")

        return RefactoringExecutor.executeWithCallback(
            project = project,
            commandName = "MCP Move Rust: $itemName to $targetModule",
            timeoutSeconds = RefactoringTimeouts.MOVE
        ) { callback ->
            // Rust move refactoring requires intellij-rust plugin
            callback.failure(
                "Rust move refactoring requires IDEA with Rust plugin (intellij-rust). " +
                "Use the IDE UI for moving Rust items. " +
                "Item: $itemType '$itemName', target: $targetModule"
            )
        }
    }

    /**
     * Checks if the Rust plugin is available in the current IDE.
     *
     * Delegates to [PluginAvailability] for centralized, cached plugin detection.
     *
     * @return `true` if Rust plugin (intellij-rust) is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.RUST)
}
