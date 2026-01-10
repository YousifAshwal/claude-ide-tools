package com.igorlink.claudeidetools.services

import com.igorlink.claudeidetools.model.OperationError
import com.igorlink.claudeidetools.model.OperationResult
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement

/**
 * Result of a PSI element lookup operation.
 *
 * This sealed class represents either a successful lookup with the found project and element,
 * or an error with structured error information.
 *
 * @see PsiLocatorService.findElementAt
 */
sealed class PsiLookupResult {
    /**
     * Successful lookup result containing the project and PSI element.
     *
     * @property project The IntelliJ project containing the element
     * @property element The PSI element found at the specified location
     */
    data class Found(val project: Project, val element: PsiElement) : PsiLookupResult()

    /**
     * Failed lookup result with structured error information.
     *
     * @property error The operation error with code and message
     */
    data class Error(val error: OperationError) : PsiLookupResult() {
        /** Human-readable error description for backward compatibility. */
        val message: String get() = error.message

        /** Create Error with just a message (convenience constructor for migration). */
        constructor(message: String) : this(OperationError.internalError(message))
    }
}

/**
 * Result of a project lookup operation.
 *
 * This sealed class represents either a successful project resolution
 * or an error with structured error information.
 *
 * @see PsiLocatorService.findProjectForFilePath
 */
sealed class ProjectLookupResult {
    /**
     * Successful lookup result containing the found project.
     *
     * @property project The IntelliJ project that owns the specified file
     */
    data class Found(val project: Project) : ProjectLookupResult()

    /**
     * Failed lookup result with structured error information.
     *
     * @property error The operation error with code and message
     */
    data class Error(val error: OperationError) : ProjectLookupResult() {
        /** Human-readable error description for backward compatibility. */
        val message: String get() = error.message

        /** Create Error with just a message (convenience constructor for migration). */
        constructor(message: String) : this(OperationError.internalError(message))
    }
}

/**
 * Service for locating PSI (Program Structure Interface) elements by file path, line, and column.
 *
 * This service provides the core lookup functionality used by all refactoring handlers.
 * It handles the complexity of:
 * - Converting file paths to IntelliJ VirtualFile objects
 * - Resolving which project owns a given file (especially important with multiple open projects)
 * - Converting line/column coordinates to PSI element references
 * - Resolving references to their declaration elements
 *
 * ## Thread Safety
 * All PSI access is properly wrapped in [ReadAction] to ensure thread safety.
 * The service can be called from any thread.
 *
 * ## Project Disambiguation
 * When multiple projects are open, the service can auto-detect which project
 * owns a file, or accept an explicit project hint for disambiguation.
 *
 * ## Usage Example
 * ```kotlin
 * val locator = service<PsiLocatorService>()
 * when (val result = locator.findElementAt("/path/to/File.java", 10, 5)) {
 *     is PsiLookupResult.Found -> {
 *         // Use result.project and result.element
 *     }
 *     is PsiLookupResult.Error -> {
 *         // Handle error: result.message
 *     }
 * }
 * ```
 *
 * @see HandlerUtils Utility functions that wrap this service for common handler patterns
 */
@Service
class PsiLocatorService {
    private val logger = Logger.getInstance(PsiLocatorService::class.java)

    /**
     * Checks if the project is in "dumb mode" (indexing in progress).
     *
     * During indexing, many PSI operations are unavailable or unreliable.
     * Refactoring operations should not proceed while the IDE is indexing.
     *
     * @param project The project to check
     * @return Error message if the project is indexing, `null` if ready for operations
     */
    fun checkDumbMode(project: Project): String? {
        return if (DumbService.getInstance(project).isDumb) {
            OperationError.indexingInProgress().message
        } else {
            null
        }
    }

    /**
     * Checks if the project is in "dumb mode" and returns a structured error.
     *
     * @param project The project to check
     * @return [OperationError] if indexing, `null` otherwise
     */
    fun checkDumbModeError(project: Project): OperationError? {
        return if (DumbService.getInstance(project).isDumb) {
            OperationError.indexingInProgress()
        } else {
            null
        }
    }

    /**
     * Internal result type for file and project resolution.
     *
     * @property project The IntelliJ project that owns the file
     * @property virtualFile The resolved virtual file object
     */
    private data class FileProjectResolution(
        val project: Project,
        val virtualFile: VirtualFile
    )

    /**
     * Resolves a file path to a virtual file and its owning project.
     *
     * This is the core resolution logic used by both [findElementAt] and [findProjectForFilePath].
     * It handles path normalization, file existence checking, and project discovery.
     *
     * @param filePath Absolute path to the file (forward or backslashes accepted)
     * @param projectHint Optional project name or base path to disambiguate when multiple projects are open
     * @return [OperationResult] with [FileProjectResolution] on success, or [OperationError] on failure
     */
    private fun resolveFileAndProject(
        filePath: String,
        projectHint: String?
    ): OperationResult<FileProjectResolution> {
        val normalizedPath = filePath.replace("\\", "/")
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(normalizedPath)

        if (virtualFile == null) {
            logger.warn("File not found: $filePath")
            return OperationResult.failure(OperationError.fileNotFound(filePath))
        }

        val project = if (projectHint != null) {
            findProjectByHint(projectHint, virtualFile)
        } else {
            findProjectForFile(virtualFile)
        }

        if (project == null) {
            val openProjects = ProjectManager.getInstance().openProjects
                .filter { !it.isDisposed }
                .map { it.name }
            return OperationResult.failure(OperationError.fileNotInProject(filePath, openProjects))
        }

        return OperationResult.success(FileProjectResolution(project, virtualFile))
    }

    /**
     * Finds the IntelliJ project that owns a given file.
     *
     * This method is useful when you need the project context but don't need
     * to locate a specific PSI element. It's used by handlers like ExtractMethod
     * that work with file ranges rather than point locations.
     *
     * ## Project Resolution
     * 1. If [projectHint] is provided, tries to match by project name or base path
     * 2. Otherwise, searches all open projects to find one that contains the file
     * 3. Returns an error if no matching project is found
     *
     * @param filePath Absolute path to the file (forward or backslashes accepted)
     * @param projectHint Optional project name (e.g., "MyProject") or base path to disambiguate
     * @return [ProjectLookupResult.Found] with the project, or [ProjectLookupResult.Error] with message
     */
    fun findProjectForFilePath(filePath: String, projectHint: String? = null): ProjectLookupResult {
        return when (val resolution = resolveFileAndProject(filePath, projectHint)) {
            is OperationResult.Failure -> ProjectLookupResult.Error(resolution.error)
            is OperationResult.Success -> ProjectLookupResult.Found(resolution.value.project)
        }
    }

    /**
     * Finds a PSI element at the specified file location.
     *
     * This is the primary method for locating code elements for refactoring operations.
     * It handles the full process of:
     * 1. Resolving the file path to a VirtualFile
     * 2. Finding the owning project
     * 3. Converting line/column to document offset
     * 4. Finding the PSI element at that offset
     * 5. Resolving references to their declaration elements
     *
     * ## Coordinate System
     * Both line and column are 1-based (first line is 1, first column is 1),
     * matching typical editor conventions.
     *
     * ## Element Resolution
     * The method attempts to find the most appropriate element for refactoring:
     * 1. If the location contains a reference, it resolves to the declaration
     * 2. Otherwise, it finds the nearest renamable parent element
     * 3. Falls back to the raw element at the offset
     *
     * @param filePath Absolute path to the file (forward or backslashes accepted)
     * @param line Line number (1-based, first line is 1)
     * @param column Column number (1-based, first column is 1)
     * @param projectHint Optional project name or base path to disambiguate when multiple projects are open
     * @return [PsiLookupResult.Found] with project and element, or [PsiLookupResult.Error] with message
     */
    fun findElementAt(
        filePath: String,
        line: Int,
        column: Int,
        projectHint: String? = null
    ): PsiLookupResult {
        return ReadAction.compute<PsiLookupResult, Throwable> {
            val resolution = when (val res = resolveFileAndProject(filePath, projectHint)) {
                is OperationResult.Failure -> return@compute PsiLookupResult.Error(res.error)
                is OperationResult.Success -> res.value
            }

            val (project, virtualFile) = resolution

            // Verify file belongs to the selected project
            val isInProject = ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)
            if (!isInProject) {
                return@compute PsiLookupResult.Error(
                    OperationError.fileNotInProject(
                        filePath,
                        listOf(project.name)
                    )
                )
            }

            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            if (psiFile == null) {
                logger.warn("Cannot create PSI for file: $filePath")
                return@compute PsiLookupResult.Error(
                    OperationError.internalError("Cannot parse file: $filePath")
                )
            }

            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            if (document == null) {
                logger.warn("No document for file: $filePath")
                return@compute PsiLookupResult.Error(
                    OperationError.internalError("Cannot get document for file: $filePath")
                )
            }

            // Validate line bounds
            if (line < 1 || line > document.lineCount) {
                return@compute PsiLookupResult.Error(
                    OperationError.locationOutOfBounds(
                        "Line $line out of bounds (valid: 1-${document.lineCount})"
                    )
                )
            }

            val lineStartOffset = document.getLineStartOffset(line - 1)
            val lineEndOffset = document.getLineEndOffset(line - 1)
            val maxColumn = lineEndOffset - lineStartOffset + 1

            // Validate column bounds
            if (column < 1 || column > maxColumn) {
                return@compute PsiLookupResult.Error(
                    OperationError.locationOutOfBounds(
                        "Column $column out of bounds for line $line (valid: 1-$maxColumn)"
                    )
                )
            }

            val offset = lineStartOffset + (column - 1)
            val elementAtOffset = psiFile.findElementAt(offset)

            if (elementAtOffset == null) {
                return@compute PsiLookupResult.Error(
                    OperationError.elementNotFound(filePath, line, column)
                )
            }

            // Try to find reference and resolve it
            val reference = psiFile.findReferenceAt(offset)
            val resolvedElement = reference?.resolve()

            // If we resolved a reference, use that; otherwise find parent named element
            val targetElement = resolvedElement
                ?: findNearestRenamableElement(elementAtOffset)
                ?: elementAtOffset

            logger.info("Found element '${(targetElement as? PsiNamedElement)?.name ?: targetElement.text}' " +
                       "in project '${project.name}' at $filePath:$line:$column")

            PsiLookupResult.Found(project, targetElement)
        }
    }

    /**
     * Finds a project by name or base path hint.
     *
     * Tries to match the hint against:
     * 1. Project name (case-insensitive exact match)
     * 2. Project base path (exact match or ends-with match)
     *
     * Falls back to [findProjectForFile] if no exact match is found.
     *
     * @param hint The project name or base path to match
     * @param virtualFile The file to use for fallback detection
     * @return The matched project, or null if not found
     */
    private fun findProjectByHint(hint: String, virtualFile: VirtualFile): Project? {
        val projectManager = ProjectManager.getInstance()
        val normalizedHint = hint.replace("\\", "/").lowercase()

        for (project in projectManager.openProjects) {
            if (project.isDisposed) continue

            // Match by project name (case-insensitive)
            if (project.name.lowercase() == normalizedHint) {
                return project
            }

            // Match by base path
            val basePath = project.basePath?.replace("\\", "/")?.lowercase()
            if (basePath != null && (basePath == normalizedHint || basePath.endsWith("/$normalizedHint"))) {
                return project
            }
        }

        // If hint didn't match exactly, fall back to file-based detection
        logger.warn("Project hint '$hint' did not match any open project, falling back to auto-detection")
        return findProjectForFile(virtualFile)
    }

    /**
     * Finds the project that contains the given file.
     *
     * Uses two strategies in order:
     * 1. Content roots: Checks if the file is within any project's content roots (most accurate)
     * 2. Base path: Falls back to checking if file path starts with project base path
     *
     * @param virtualFile The file to find the owning project for
     * @return The project that contains the file, or null if not found in any open project
     */
    private fun findProjectForFile(virtualFile: VirtualFile): Project? {
        val projectManager = ProjectManager.getInstance()

        // First, try to find by content roots (most accurate)
        // FileIndex.isInContent requires ReadAction when called from background thread
        for (project in projectManager.openProjects) {
            if (project.isDisposed) continue
            val isInContent = ReadAction.compute<Boolean, Throwable> {
                ProjectRootManager.getInstance(project).fileIndex.isInContent(virtualFile)
            }
            if (isInContent) return project
        }

        // Fallback: try to find project by base path
        if (!virtualFile.isValid) return null
        val filePath = virtualFile.path
        for (project in projectManager.openProjects) {
            if (project.isDisposed) continue
            val basePath = project.basePath
            if (basePath != null && filePath.startsWith(basePath)) {
                return project
            }
        }

        return null
    }

    /**
     * Finds the nearest renamable element by traversing up the PSI tree.
     *
     * Starting from the given element, walks up the parent chain until it finds
     * an element that implements [PsiNameIdentifierOwner] or [PsiNamedElement],
     * which indicates it's a named code element (class, method, variable, etc.)
     * that can be renamed.
     *
     * @param element The starting element (typically a token or leaf element)
     * @return The nearest renamable parent element, or null if none found
     */
    private fun findNearestRenamableElement(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null) {
            if (current is PsiNameIdentifierOwner || current is PsiNamedElement) {
                return current
            }
            current = current.parent
        }
        return null
    }
}
