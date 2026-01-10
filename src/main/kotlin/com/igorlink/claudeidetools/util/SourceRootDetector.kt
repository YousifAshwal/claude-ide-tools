package com.igorlink.claudeidetools.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Enumeration representing the type of source root a file belongs to.
 *
 * Used to distinguish between main source files, test source files,
 * and files outside of project source roots.
 */
enum class SourceRootType {
    /** File is in main source content (e.g., src/main). */
    MAIN,

    /** File is in test source content (e.g., src/test). */
    TEST,

    /** File is not in any recognized source root. */
    NONE
}

/**
 * Utility for detecting the source root type of files in a project.
 *
 * This detector examines the project's file index to determine whether
 * a file belongs to main sources, test sources, or is outside source roots.
 * It handles ReadAction wrapping internally for thread safety.
 *
 * ## Thread Safety
 * All methods in this object are thread-safe and can be called from any thread.
 * Read access to the file index is properly wrapped in [ReadAction].
 *
 * ## Usage Example
 * ```kotlin
 * val sourceRootType = SourceRootDetector.determineSourceRootType(project, virtualFile)
 * when (sourceRootType) {
 *     SourceRootType.MAIN -> // handle main source
 *     SourceRootType.TEST -> // handle test source
 *     SourceRootType.NONE -> // file not in source roots
 * }
 * ```
 *
 * @see SourceRootType The enumeration of source root types
 */
object SourceRootDetector {

    /**
     * Determines the source root type for a given virtual file.
     *
     * Wraps the file index query in a ReadAction for thread safety,
     * allowing this method to be called from any thread.
     *
     * @param project The IntelliJ project context
     * @param virtualFile The virtual file to analyze
     * @return The detected [SourceRootType] (MAIN, TEST, or NONE)
     */
    fun determineSourceRootType(project: Project, virtualFile: VirtualFile): SourceRootType {
        return ReadAction.compute<SourceRootType, Throwable> {
            val fileIndex = ProjectRootManager.getInstance(project).fileIndex
            when {
                fileIndex.isInTestSourceContent(virtualFile) -> SourceRootType.TEST
                fileIndex.isInSourceContent(virtualFile) -> SourceRootType.MAIN
                else -> SourceRootType.NONE
            }
        }
    }

    /**
     * Determines the source root type for a PSI element's containing file.
     *
     * Convenience method that extracts the virtual file from the element's
     * containing file and delegates to [determineSourceRootType].
     *
     * @param project The IntelliJ project context
     * @param element The PSI element to analyze
     * @return The detected [SourceRootType] (MAIN, TEST, or NONE)
     */
    fun determineSourceRootType(project: Project, element: com.intellij.psi.PsiElement): SourceRootType {
        val virtualFile = ReadAction.compute<VirtualFile?, Throwable> {
            element.containingFile?.virtualFile
        }
        return if (virtualFile != null) {
            determineSourceRootType(project, virtualFile)
        } else {
            SourceRootType.NONE
        }
    }
}
