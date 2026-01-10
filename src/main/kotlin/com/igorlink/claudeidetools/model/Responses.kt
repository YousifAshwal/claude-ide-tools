package com.igorlink.claudeidetools.model

import kotlinx.serialization.Serializable

/**
 * Information about an open project in the IDE.
 *
 * Returned as part of [StatusResponse] to help clients identify which project
 * to target for refactoring operations.
 *
 * @property name The project name (as shown in IDE title bar)
 * @property path Absolute path to the project root directory (forward slashes)
 */
@Serializable
data class ProjectInfo(
    val name: String,
    val path: String
)

/**
 * Response DTO for the `/status` health check endpoint.
 *
 * Provides comprehensive information about the IDE state including
 * open projects, indexing status, and available language support.
 *
 * ## Example Response
 * ```json
 * {
 *   "ok": true,
 *   "ideType": "IntelliJ IDEA",
 *   "ideVersion": "2024.1.2",
 *   "port": 8765,
 *   "openProjects": [
 *     {"name": "MyProject", "path": "/path/to/project"}
 *   ],
 *   "indexingInProgress": false,
 *   "languagePlugins": {
 *     "Java": true,
 *     "Kotlin": true,
 *     "Python": false
 *   }
 * }
 * ```
 *
 * @property ok Always `true` if the server responds (indicates IDE is running)
 * @property ideType Human-readable IDE name (e.g., "IntelliJ IDEA", "WebStorm")
 * @property ideVersion Full IDE version string (e.g., "2024.1.2")
 * @property port The HTTP server port number
 * @property openProjects List of currently open projects with names and paths
 * @property indexingInProgress `true` if any project is currently being indexed
 * @property languagePlugins Map of language names to their availability status
 */
@Serializable
data class StatusResponse(
    val ok: Boolean,
    val ideType: String = "IntelliJ IDEA",
    val ideVersion: String,
    val port: Int = 8765,
    val openProjects: List<ProjectInfo> = emptyList(),
    val indexingInProgress: Boolean = false,
    val languagePlugins: Map<String, Boolean> = emptyMap(),
    val implementedTools: Map<String, List<String>> = emptyMap()
)

/**
 * Response DTO for refactoring operations (rename, move, extract method).
 *
 * Indicates whether the operation succeeded and provides a descriptive message.
 * Optionally includes a list of files that were modified.
 *
 * ## Success Example
 * ```json
 * {
 *   "success": true,
 *   "message": "Renamed 'oldName' to 'newName' in project 'MyProject'",
 *   "affectedFiles": []
 * }
 * ```
 *
 * ## Failure Example
 * ```json
 * {
 *   "success": false,
 *   "message": "Element at location is not renamable"
 * }
 * ```
 *
 * @property success `true` if the refactoring completed successfully
 * @property message Human-readable description of the result or error
 * @property affectedFiles List of absolute paths to files modified (may be empty)
 */
@Serializable
data class RefactoringResponse(
    val success: Boolean,
    val message: String,
    val affectedFiles: List<String> = emptyList()
)

/**
 * Represents a single usage/reference found by the find usages operation.
 *
 * Each usage includes the location and a preview of the containing line.
 *
 * ## Example
 * ```json
 * {
 *   "file": "/path/to/OtherClass.java",
 *   "line": 25,
 *   "column": 10,
 *   "preview": "MyClass instance = new MyClass();"
 * }
 * ```
 *
 * @property file Absolute path to the file containing the usage
 * @property line Line number (1-based) of the usage
 * @property column Column number (1-based) of the reference start
 * @property preview Trimmed text of the line containing the usage
 */
@Serializable
data class Usage(
    val file: String,
    val line: Int,
    val column: Int,
    val preview: String
)

/**
 * Response DTO for the `/findUsages` endpoint.
 *
 * Contains a list of all references to the specified symbol found in the project.
 *
 * ## Success Example
 * ```json
 * {
 *   "success": true,
 *   "message": "Found 5 usage(s) in project 'MyProject'",
 *   "usages": [
 *     {"file": "/path/to/File.java", "line": 10, "column": 5, "preview": "..."}
 *   ]
 * }
 * ```
 *
 * @property success `true` if the search completed successfully
 * @property message Human-readable summary of the results
 * @property usages List of [Usage] objects, one per reference found
 */
@Serializable
data class FindUsagesResponse(
    val success: Boolean,
    val message: String,
    val usages: List<Usage> = emptyList()
)

/**
 * Response DTO for error conditions.
 *
 * Returned when a request cannot be processed due to invalid input,
 * missing parameters, or other error conditions.
 *
 * ## Example
 * ```json
 * {
 *   "success": false,
 *   "error": "File not found: /path/to/missing.java",
 *   "code": "FILE_NOT_FOUND"
 * }
 * ```
 *
 * @property success Always `false` for error responses
 * @property error Human-readable error description
 * @property code Machine-readable error code for programmatic handling
 */
@Serializable
data class ErrorResponse(
    val success: Boolean = false,
    val error: String,
    val code: String = "UNKNOWN_ERROR"
)

/**
 * Severity levels for code diagnostics.
 *
 * Maps to IntelliJ's HighlightSeverity levels.
 * Ordered from most severe to least severe.
 */
enum class DiagnosticSeverity {
    /** Compilation errors that prevent code from running */
    ERROR,
    /** Potential issues that should be addressed */
    WARNING,
    /** Minor issues or style suggestions */
    WEAK_WARNING,
    /** Informational messages */
    INFO,
    /** Subtle hints for code improvement */
    HINT;

    companion object {
        /**
         * Parses a severity string to enum, case-insensitive.
         * Returns null if the string doesn't match any severity.
         */
        fun fromString(value: String): DiagnosticSeverity? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}

/**
 * Represents a quick fix action available for a diagnostic.
 *
 * Quick fixes are suggested code modifications that can resolve the diagnostic issue.
 * Use the apply_fix endpoint to execute a fix.
 *
 * ## Example
 * ```json
 * {
 *   "id": 0,
 *   "name": "Add explicit 'this'",
 *   "familyName": "Add explicit 'this'",
 *   "description": "Adds explicit 'this.' qualifier"
 * }
 * ```
 *
 * @property id Index of this fix in the list (used when applying)
 * @property name Display name of the fix action
 * @property familyName Family/category of fixes this belongs to
 * @property description Optional longer description of what the fix does
 */
@Serializable
data class QuickFix(
    val id: Int,
    val name: String,
    val familyName: String? = null,
    val description: String? = null
)

/**
 * Represents a single diagnostic (error, warning, etc.) found by code analysis.
 *
 * Contains location information, severity, the diagnostic message, and available quick fixes.
 *
 * ## Example
 * ```json
 * {
 *   "file": "/path/to/MyClass.java",
 *   "line": 15,
 *   "column": 10,
 *   "endLine": 15,
 *   "endColumn": 15,
 *   "severity": "ERROR",
 *   "message": "Cannot resolve symbol 'foo'",
 *   "source": "Java",
 *   "fixes": [
 *     {"id": 0, "name": "Import class", "familyName": "Import"}
 *   ]
 * }
 * ```
 *
 * @property file Absolute path to the file containing the diagnostic
 * @property line Starting line number (1-based)
 * @property column Starting column number (1-based)
 * @property endLine Ending line number (1-based)
 * @property endColumn Ending column number (1-based)
 * @property severity Severity level (ERROR, WARNING, WEAK_WARNING, INFO, HINT)
 * @property message Human-readable description of the issue
 * @property source Origin of the diagnostic (e.g., "Java", "ESLint", "TypeScript")
 * @property fixes List of available quick fix actions for this diagnostic
 */
@Serializable
data class Diagnostic(
    val file: String,
    val line: Int,
    val column: Int,
    val endLine: Int,
    val endColumn: Int,
    val severity: String,
    val message: String,
    val source: String? = null,
    val fixes: List<QuickFix> = emptyList()
)

/**
 * Response DTO for the `/diagnostics` endpoint.
 *
 * Contains diagnostics found by code analysis, along with metadata
 * about the results.
 *
 * ## Success Example
 * ```json
 * {
 *   "success": true,
 *   "message": "Found 5 diagnostic(s) in file 'MyClass.java'",
 *   "diagnostics": [...],
 *   "totalCount": 5,
 *   "truncated": false
 * }
 * ```
 *
 * @property success `true` if the analysis completed successfully
 * @property message Human-readable summary of the results
 * @property diagnostics List of [Diagnostic] objects found
 * @property totalCount Total number of diagnostics before applying limit
 * @property truncated `true` if results were truncated due to limit
 */
@Serializable
data class DiagnosticsResponse(
    val success: Boolean,
    val message: String,
    val diagnostics: List<Diagnostic> = emptyList(),
    val totalCount: Int = 0,
    val truncated: Boolean = false
)

/**
 * Response DTO for the `/applyFix` endpoint.
 *
 * Indicates whether the quick fix was successfully applied.
 *
 * ## Success Example
 * ```json
 * {
 *   "success": true,
 *   "message": "Applied fix 'Add explicit this'",
 *   "fixName": "Add explicit 'this'",
 *   "affectedFiles": ["/path/to/MyClass.java"]
 * }
 * ```
 *
 * ## Failure Example
 * ```json
 * {
 *   "success": false,
 *   "message": "No diagnostic found at the specified location"
 * }
 * ```
 *
 * @property success `true` if the fix was applied successfully
 * @property message Human-readable description of the result or error
 * @property fixName Name of the applied fix (only on success)
 * @property affectedFiles List of files modified by the fix (may be empty)
 */
@Serializable
data class ApplyFixResponse(
    val success: Boolean,
    val message: String,
    val fixName: String? = null,
    val affectedFiles: List<String> = emptyList()
)
