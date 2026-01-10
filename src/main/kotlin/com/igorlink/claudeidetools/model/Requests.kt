package com.igorlink.claudeidetools.model

import kotlinx.serialization.Serializable

/**
 * Common interface for requests that locate an element by file, line, and column.
 *
 * Implemented by request types that need to identify a specific code element
 * for refactoring operations (rename, move, find usages).
 *
 * @property file Absolute path to the source file
 * @property line Line number (1-based, first line is 1)
 * @property column Column number (1-based, first column is 1)
 * @property project Optional project name or base path for disambiguation when multiple projects are open
 */
interface LocatorRequest {
    val file: String
    val line: Int
    val column: Int
    val project: String?
}

/**
 * Request DTO for the `/rename` refactoring endpoint.
 *
 * Identifies a symbol by its location and specifies the new name to apply.
 * All references to the symbol throughout the project will be updated.
 *
 * ## Example JSON
 * ```json
 * {
 *   "file": "/path/to/MyClass.java",
 *   "line": 10,
 *   "column": 14,
 *   "newName": "BetterName",
 *   "searchInComments": true,
 *   "searchTextOccurrences": false
 * }
 * ```
 *
 * @property file Absolute path to the source file containing the symbol
 * @property line Line number (1-based) where the symbol is located
 * @property column Column number (1-based) pointing to the symbol name
 * @property newName The new name to apply to the symbol (must not be blank)
 * @property searchInComments Whether to also rename occurrences in comments (default: false)
 * @property searchTextOccurrences Whether to also rename text occurrences in strings (default: false)
 * @property project Optional project name or base path for disambiguation
 */
@Serializable
data class RenameRequest(
    override val file: String,
    override val line: Int,
    override val column: Int,
    val newName: String,
    val searchInComments: Boolean = false,
    val searchTextOccurrences: Boolean = false,
    override val project: String? = null
) : LocatorRequest

/**
 * Request DTO for the `/findUsages` endpoint.
 *
 * Identifies a symbol by its location to find all references to it throughout the project.
 *
 * ## Example JSON
 * ```json
 * {
 *   "file": "/path/to/MyClass.java",
 *   "line": 10,
 *   "column": 14
 * }
 * ```
 *
 * @property file Absolute path to the source file containing the symbol
 * @property line Line number (1-based) where the symbol is located
 * @property column Column number (1-based) pointing to the symbol name
 * @property project Optional project name or base path for disambiguation
 */
@Serializable
data class FindUsagesRequest(
    override val file: String,
    override val line: Int,
    override val column: Int,
    override val project: String? = null
) : LocatorRequest

/**
 * Request DTO for the `/move` refactoring endpoint.
 *
 * Identifies a class/symbol by its location and specifies the target package/module.
 * All imports and references will be updated automatically.
 *
 * ## Target Package Format
 * - **Java/Kotlin**: Fully qualified package name (e.g., `com.example.utils`)
 * - **Python**: Module path (e.g., `package.subpackage.module`)
 * - **JavaScript/TypeScript**: File path (e.g., `./utils/helpers`)
 * - **Go**: Package path (e.g., `internal/utils`)
 * - **Rust**: Module path (e.g., `crate::utils::helpers`)
 *
 * ## Example JSON
 * ```json
 * {
 *   "file": "/path/to/MyClass.java",
 *   "line": 5,
 *   "column": 14,
 *   "targetPackage": "com.example.newpackage"
 * }
 * ```
 *
 * @property file Absolute path to the source file containing the element
 * @property line Line number (1-based) where the element is located
 * @property column Column number (1-based) pointing to the element name
 * @property targetPackage Target package/module path (format depends on language)
 * @property searchInComments Whether to also update occurrences in comments (default: false)
 * @property searchInNonJavaFiles Whether to also update occurrences in non-Java files like XML, properties (default: false)
 * @property project Optional project name or base path for disambiguation
 */
@Serializable
data class MoveRequest(
    override val file: String,
    override val line: Int,
    override val column: Int,
    val targetPackage: String,
    val searchInComments: Boolean = false,
    val searchInNonJavaFiles: Boolean = false,
    override val project: String? = null
) : LocatorRequest

/**
 * Request DTO for the `/extractMethod` refactoring endpoint.
 *
 * Specifies a range of code to extract into a new method/function.
 * The selected range must contain complete, extractable statements.
 *
 * ## Coordinate System
 * All line and column values are 1-based (first line/column is 1).
 * The range is inclusive on both ends.
 *
 * ## Example JSON
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
 * @property file Absolute path to the source file containing the code
 * @property startLine Starting line number (1-based) of the code range
 * @property startColumn Starting column number (1-based)
 * @property endLine Ending line number (1-based) of the code range
 * @property endColumn Ending column number (1-based)
 * @property methodName Name for the new method/function (must not be blank)
 * @property project Optional project name or base path for disambiguation
 */
@Serializable
data class ExtractMethodRequest(
    val file: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val methodName: String,
    val project: String? = null
)

/**
 * Request DTO for the `/diagnostics` endpoint.
 *
 * Retrieves code analysis diagnostics (errors, warnings, etc.) from the IDE.
 * All parameters are optional, allowing flexible querying.
 *
 * ## Query Modes
 * - **Single file**: Provide `file` to get diagnostics for one file
 * - **Project-wide**: Omit `file` to get all diagnostics in the project
 *
 * ## Analysis Modes
 * - **Cached (default)**: Uses IDE's cached highlights from background analysis.
 *   Fast but only returns diagnostics for files already analyzed by the daemon.
 * - **Inspections**: Set `runInspections=true` to run inspections programmatically.
 *   Slower but provides comprehensive analysis of all files.
 *
 * ## Example JSON
 * ```json
 * {
 *   "file": "/path/to/MyClass.java",
 *   "severity": ["ERROR", "WARNING"],
 *   "limit": 50,
 *   "runInspections": true
 * }
 * ```
 *
 * @property file Optional absolute path to analyze a specific file
 * @property project Optional project name or base path for disambiguation
 * @property severity Optional list of severity levels to include (default: all).
 *                    Valid values: ERROR, WARNING, WEAK_WARNING, INFO, HINT
 * @property limit Maximum number of diagnostics to return (default: 100, must be positive)
 * @property runInspections When true, runs inspections programmatically instead of using
 *                          cached highlights. Slower but more comprehensive. (default: false)
 */
@Serializable
data class DiagnosticsRequest(
    val file: String? = null,
    val project: String? = null,
    val severity: List<String>? = null,
    val limit: Int = 100,
    val runInspections: Boolean = false
)

/**
 * Request DTO for the `/applyFix` endpoint.
 *
 * Applies a quick fix action to resolve a diagnostic issue.
 * The fix is identified by file location and fix index from a previous diagnostics response.
 *
 * ## Analysis Modes
 * - **Cached (default)**: Uses IDE's cached highlights. Only works for files currently open
 *   in the editor with active analysis.
 * - **Inspections**: Set `runInspections=true` to run inspections programmatically.
 *   Works for any file, even if not open in the editor.
 *
 * ## Example JSON
 * ```json
 * {
 *   "file": "/path/to/MyClass.java",
 *   "line": 15,
 *   "column": 10,
 *   "fixId": 0,
 *   "diagnosticMessage": "Cannot resolve symbol 'foo'",
 *   "runInspections": true
 * }
 * ```
 *
 * @property file Absolute path to the file containing the diagnostic
 * @property line Line number (1-based) of the diagnostic
 * @property column Column number (1-based) of the diagnostic
 * @property fixId Index of the fix to apply (from the `fixes` array in diagnostics)
 * @property diagnosticMessage The diagnostic message to match (for verification)
 * @property project Optional project name or base path for disambiguation
 * @property runInspections When true, runs inspections to find the diagnostic instead of
 *                          using cached highlights. Use this when the file is not open in editor.
 */
@Serializable
data class ApplyFixRequest(
    val file: String,
    val line: Int,
    val column: Int,
    val fixId: Int,
    val diagnosticMessage: String? = null,
    val project: String? = null,
    val runInspections: Boolean = false
)
