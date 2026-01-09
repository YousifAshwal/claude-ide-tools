package com.igorlink.claudejetbrainstools.model

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
