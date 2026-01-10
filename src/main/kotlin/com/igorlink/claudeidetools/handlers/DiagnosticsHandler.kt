package com.igorlink.claudeidetools.handlers

import com.igorlink.claudeidetools.diagnostics.CachedHighlightsProvider
import com.igorlink.claudeidetools.diagnostics.DiagnosticsProvider
import com.igorlink.claudeidetools.diagnostics.DiagnosticsUtils
import com.igorlink.claudeidetools.diagnostics.InspectionRunnerProvider
import com.igorlink.claudeidetools.model.Diagnostic
import com.igorlink.claudeidetools.model.DiagnosticsRequest
import com.igorlink.claudeidetools.model.DiagnosticsResponse
import com.igorlink.claudeidetools.services.PsiLocatorService
import com.igorlink.claudeidetools.util.HandlerUtils
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor

/**
 * Handler for the `/diagnostics` endpoint to retrieve code analysis results.
 *
 * This handler provides access to IDE's code analysis diagnostics (errors, warnings,
 * etc.) for a specific file or across the entire project. It supports two modes:
 * - **Cached mode** (default): Uses [CachedHighlightsProvider] for fast retrieval of
 *   already-computed highlights from the IDE's background daemon.
 * - **Inspection mode**: Uses [InspectionRunnerProvider] to run inspections
 *   programmatically for comprehensive analysis.
 *
 * ## Query Modes
 * - **Single file**: When `file` is provided, returns diagnostics only for that file
 * - **Project-wide**: When `file` is omitted, scans all source files in the project
 *
 * ## Filtering
 * - **Severity filter**: Use `severity` to include only specific levels (ERROR, WARNING, etc.)
 * - **Limit**: Use `limit` to cap the number of returned diagnostics
 *
 * ## Architecture
 * Follows SOLID principles with [DiagnosticsProvider] abstraction for different
 * collection strategies. The handler orchestrates file collection, delegates to
 * the appropriate provider, and handles response building.
 *
 * @see DiagnosticsRequest The request data class
 * @see DiagnosticsResponse The response data class
 * @see DiagnosticsProvider Interface for diagnostic collection strategies
 */
object DiagnosticsHandler {
    private val logger = Logger.getInstance(DiagnosticsHandler::class.java)

    /**
     * Multiplier for early exit threshold during collection.
     * We collect up to limit * EARLY_EXIT_MULTIPLIER before stopping file iteration.
     * This ensures we have enough for sorting by severity while not collecting everything.
     */
    private const val EARLY_EXIT_MULTIPLIER = 2

    /**
     * Handles the diagnostics request.
     *
     * Selects the appropriate [DiagnosticsProvider] based on request parameters
     * and retrieves code analysis diagnostics.
     *
     * @param request The diagnostics request with optional file, severity filter, limit, and mode
     * @return [DiagnosticsResponse] with success status and list of diagnostics
     */
    fun handle(request: DiagnosticsRequest): DiagnosticsResponse {
        val effectiveLimit = request.limit.coerceAtLeast(1)

        // Select provider based on runInspections flag (DIP - depend on abstraction)
        val provider: DiagnosticsProvider = if (request.runInspections) {
            InspectionRunnerProvider
        } else {
            CachedHighlightsProvider
        }

        return when {
            request.file != null -> handleSingleFile(request, effectiveLimit, provider)
            else -> handleProjectWide(request, effectiveLimit, provider)
        }
    }

    /**
     * Handles diagnostics request for a single file or directory.
     * Uses [HandlerUtils.withProjectLookup] for consistent error handling.
     * If path points to a directory, collects all files within it recursively.
     */
    private fun handleSingleFile(
        request: DiagnosticsRequest,
        limit: Int,
        provider: DiagnosticsProvider
    ): DiagnosticsResponse {
        return HandlerUtils.withProjectLookup(
            file = request.file!!,
            projectHint = request.project,
            errorResponseFactory = { message -> DiagnosticsResponse(false, message) }
        ) { project ->
            val virtualFile = findVirtualFile(request.file)
            if (virtualFile == null) {
                return@withProjectLookup DiagnosticsResponse(
                    success = false,
                    message = "Path not found: ${request.file}"
                )
            }

            val (files, scopeDescription) = if (virtualFile.isDirectory) {
                val dirFiles = collectFilesFromDirectory(virtualFile, limit * EARLY_EXIT_MULTIPLIER)
                dirFiles to "directory '${virtualFile.name}'"
            } else {
                listOf(virtualFile) to "file '${virtualFile.name}'"
            }

            collectAndBuildResponse(
                project = project,
                files = files,
                severityFilter = DiagnosticsUtils.parseSeverityFilter(request.severity),
                limit = limit,
                scopeDescription = scopeDescription,
                provider = provider
            )
        }
    }

    /**
     * Recursively collects files from a directory.
     *
     * @param directory The directory to collect files from
     * @param maxFiles Maximum number of files to collect
     * @return List of files in the directory (up to maxFiles)
     */
    private fun collectFilesFromDirectory(directory: VirtualFile, maxFiles: Int): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()

        VfsUtilCore.visitChildrenRecursively(directory, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (files.size >= maxFiles) {
                    return false
                }
                if (!file.isDirectory) {
                    files.add(file)
                }
                return files.size < maxFiles
            }
        })

        return files
    }

    /**
     * Handles diagnostics request for an entire project.
     * Resolves project from hint or uses first available.
     */
    private fun handleProjectWide(
        request: DiagnosticsRequest,
        limit: Int,
        provider: DiagnosticsProvider
    ): DiagnosticsResponse {
        val project = resolveProjectFromHint(request.project)
            ?: return DiagnosticsResponse(
                success = false,
                message = "No project found. Provide a file path or ensure a project is open."
            )

        // Check dumb mode manually since we don't go through HandlerUtils
        val locator = service<PsiLocatorService>()
        locator.checkDumbMode(project)?.let { errorMessage ->
            return DiagnosticsResponse(success = false, message = errorMessage)
        }

        return try {
            val files = collectProjectSourceFilesLazily(project, limit * EARLY_EXIT_MULTIPLIER)
            collectAndBuildResponse(
                project = project,
                files = files,
                severityFilter = DiagnosticsUtils.parseSeverityFilter(request.severity),
                limit = limit,
                scopeDescription = "project '${project.name}'",
                provider = provider
            )
        } catch (e: Exception) {
            logger.error("Failed to collect diagnostics", e)
            DiagnosticsResponse(
                success = false,
                message = "Failed to collect diagnostics: ${e.message}"
            )
        }
    }

    /**
     * Resolves project from a name/path hint without requiring a file.
     *
     * @param hint Optional project name or base path
     * @return The resolved project, or first open project if hint is null
     */
    private fun resolveProjectFromHint(hint: String?): Project? {
        val openProjects = ProjectManager.getInstance().openProjects.filter { !it.isDisposed }

        if (openProjects.isEmpty()) return null

        if (hint == null) {
            return openProjects.firstOrNull()
        }

        val normalizedHint = hint.replace("\\", "/").lowercase()
        return openProjects.find { project ->
            project.name.lowercase() == normalizedHint ||
                project.basePath?.replace("\\", "/")?.lowercase()?.endsWith("/$normalizedHint") == true ||
                project.basePath?.replace("\\", "/")?.lowercase() == normalizedHint
        } ?: openProjects.firstOrNull()
    }

    /**
     * Collects diagnostics from files using the specified provider and builds the response.
     *
     * Orchestrates the collection process: iterates over files, delegates to provider,
     * applies sorting and limiting, and constructs the response.
     *
     * @param project The target project
     * @param files Files to analyze
     * @param severityFilter Set of severity levels to include (empty = all)
     * @param limit Maximum diagnostics to return
     * @param scopeDescription Human-readable description of the scope for messages
     * @param provider The diagnostics provider to use for collection
     */
    private fun collectAndBuildResponse(
        project: Project,
        files: List<VirtualFile>,
        severityFilter: Set<com.igorlink.claudeidetools.model.DiagnosticSeverity>,
        limit: Int,
        scopeDescription: String,
        provider: DiagnosticsProvider
    ): DiagnosticsResponse {
        val allDiagnostics = mutableListOf<Diagnostic>()

        try {
            // Each provider manages its own threading (ReadAction, ProgressManager, etc.)
            for (virtualFile in files) {
                val fileDiagnostics = provider.collectFromFile(project, virtualFile, severityFilter)
                allDiagnostics.addAll(fileDiagnostics)

                // Early exit if we've collected enough for sorting
                if (allDiagnostics.size >= limit * EARLY_EXIT_MULTIPLIER) break
            }
        } catch (e: Exception) {
            logger.error("Failed to collect diagnostics using ${provider.providerName}", e)
            return DiagnosticsResponse(
                success = false,
                message = "Failed to collect diagnostics: ${e.message}"
            )
        }

        // Sort by severity (errors first), then by file and line
        val sortedDiagnostics = allDiagnostics.sortedWith(
            compareBy(
                { DiagnosticsUtils.severityOrder(it.severity) },
                { it.file },
                { it.line },
                { it.column }
            )
        )

        val totalCount = sortedDiagnostics.size
        val truncated = totalCount > limit
        val limitedDiagnostics = sortedDiagnostics.take(limit)

        val message = DiagnosticsUtils.buildResultMessage(
            scopeDescription,
            limitedDiagnostics.size,
            totalCount,
            truncated
        )

        return DiagnosticsResponse(
            success = true,
            message = message,
            diagnostics = limitedDiagnostics,
            totalCount = totalCount,
            truncated = truncated
        )
    }

    /**
     * Finds a VirtualFile by path.
     */
    private fun findVirtualFile(path: String): VirtualFile? {
        val normalizedPath = path.replace("\\", "/")
        return LocalFileSystem.getInstance().findFileByPath(normalizedPath)
    }

    /**
     * Collects project files lazily with early termination.
     *
     * Includes both source and resource files to ensure comprehensive diagnostics coverage.
     * Stops collecting when [maxFiles] is reached to avoid memory issues on large projects.
     *
     * @param project The target project
     * @param maxFiles Maximum number of files to collect
     * @return List of project files (up to maxFiles)
     */
    private fun collectProjectSourceFilesLazily(project: Project, maxFiles: Int): List<VirtualFile> {
        return ReadAction.compute<List<VirtualFile>, Throwable> {
            val files = mutableListOf<VirtualFile>()
            val fileIndex = ProjectRootManager.getInstance(project).fileIndex
            val contentRoots = ProjectRootManager.getInstance(project).contentRoots

            for (root in contentRoots) {
                VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (files.size >= maxFiles) {
                            return false // Stop visiting
                        }
                        // Use isInContent to include both source and resource files
                        if (!file.isDirectory && fileIndex.isInContent(file)) {
                            files.add(file)
                        }
                        return files.size < maxFiles
                    }
                })
                if (files.size >= maxFiles) break
            }

            files
        }
    }
}
