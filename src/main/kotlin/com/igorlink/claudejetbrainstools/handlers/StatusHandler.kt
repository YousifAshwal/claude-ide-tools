package com.igorlink.claudejetbrainstools.handlers

import com.igorlink.claudejetbrainstools.model.ProjectInfo
import com.igorlink.claudejetbrainstools.model.StatusResponse
import com.igorlink.claudejetbrainstools.util.IdeDetector
import com.igorlink.claudejetbrainstools.util.LanguageDetector
import com.igorlink.claudejetbrainstools.util.SupportedLanguage
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ProjectManager

/**
 * Handler for the `/status` endpoint providing IDE health check and information.
 *
 * This handler returns comprehensive information about the current IDE state,
 * including:
 * - Whether the IDE is operational
 * - IDE type (IntelliJ IDEA, WebStorm, PyCharm, etc.) and version
 * - HTTP server port
 * - List of open projects with their paths
 * - Whether indexing is in progress
 * - Available language plugin support
 *
 * ## Response Structure
 * The response includes:
 * - `ok`: Always `true` if the server is responding
 * - `ideType`: Human-readable IDE name (e.g., "IntelliJ IDEA")
 * - `ideVersion`: Full version string (e.g., "2024.1.2")
 * - `port`: The HTTP server port number
 * - `openProjects`: List of [ProjectInfo] with name and path
 * - `indexingInProgress`: `true` if any project is currently indexing
 * - `languagePlugins`: Map of language names to availability status
 *
 * ## Usage
 * This endpoint is typically called by MCP servers to:
 * - Verify the IDE is running and accessible
 * - Discover which projects are open for the `project` parameter
 * - Check if refactoring operations should wait for indexing to complete
 *
 * @see StatusResponse The response data class
 */
object StatusHandler {

    /**
     * Handles the status request and returns current IDE information.
     *
     * This method is safe to call at any time and does not modify IDE state.
     * It queries various IDE services to gather current status information.
     *
     * @return [StatusResponse] containing IDE status and configuration information
     */
    fun handle(): StatusResponse {
        val projects = ProjectManager.getInstance().openProjects
        val indexingInProgress = projects.any { !it.isDisposed && DumbService.getInstance(it).isDumb }
        val ide = IdeDetector.detect()

        // Check which language plugins are available
        val languagePlugins = mapOf(
            "Java" to true, // Always available
            "Kotlin" to LanguageDetector.isLanguageSupported(SupportedLanguage.KOTLIN),
            "JavaScript" to LanguageDetector.isLanguageSupported(SupportedLanguage.JAVASCRIPT),
            "TypeScript" to LanguageDetector.isLanguageSupported(SupportedLanguage.TYPESCRIPT),
            "Python" to LanguageDetector.isLanguageSupported(SupportedLanguage.PYTHON),
            "Go" to LanguageDetector.isLanguageSupported(SupportedLanguage.GO),
            "Rust" to LanguageDetector.isLanguageSupported(SupportedLanguage.RUST)
        )

        // Report which tools are actually implemented (not stubs)
        // - move: Java always, Kotlin if plugin available
        // - extract_method: Java only (others require editor context)
        val implementedTools = buildMap {
            // Move is implemented for Java and Kotlin
            val moveLanguages = mutableListOf("Java")
            if (languagePlugins["Kotlin"] == true) {
                moveLanguages.add("Kotlin")
            }
            put("move", moveLanguages.toList())

            // Extract method is only implemented for Java
            put("extract_method", listOf("Java"))
        }

        // Get project info with paths
        val projectInfos = projects
            .filter { !it.isDisposed }
            .map { project ->
                ProjectInfo(
                    name = project.name,
                    path = project.basePath?.replace("\\", "/") ?: ""
                )
            }

        return StatusResponse(
            ok = true,
            ideType = ide.displayName,
            ideVersion = ApplicationInfo.getInstance().fullVersion,
            port = ide.port,
            openProjects = projectInfos,
            indexingInProgress = indexingInProgress,
            languagePlugins = languagePlugins,
            implementedTools = implementedTools
        )
    }
}
