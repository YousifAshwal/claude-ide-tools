package com.igorlink.claudeidetools.handlers.languages

import com.igorlink.claudeidetools.model.RefactoringResponse
import com.igorlink.claudeidetools.util.PluginAvailability
import com.igorlink.claudeidetools.util.RefactoringExecutor
import com.igorlink.claudeidetools.util.RefactoringTimeouts
import com.igorlink.claudeidetools.util.SourceRootDetector
import com.igorlink.claudeidetools.util.SourceRootType
import com.igorlink.claudeidetools.util.SupportedLanguage
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.refactoring.PackageWrapper

/**
 * Handler for Java move refactoring operations.
 *
 * Supports moving Java classes to different packages. Uses IntelliJ's
 * MoveClassesOrPackagesProcessor for reliable refactoring with automatic
 * import updates across the entire project.
 *
 * ## Design Decision: Direct Imports vs Reflection
 *
 * Unlike other language handlers (Kotlin, Python, Go, etc.) that use reflection via
 * [com.igorlink.claudeidetools.util.PsiReflectionUtils], this handler uses direct imports
 * for Java-specific classes ([PsiClass], [JavaPsiFacade], [MoveClassesOrPackagesProcessor]).
 *
 * This approach is intentional:
 * 1. **Compile-time safety**: The Java plugin is declared as an optional dependency in
 *    `plugin.xml`. The ClassLoader will only load this file when the Java plugin is present.
 * 2. **Type safety**: Direct imports provide better IDE support, compile-time checks,
 *    and avoid runtime reflection errors.
 * 3. **Stability**: Java API classes are core IntelliJ Platform classes that rarely change.
 *
 * The [isAvailable] method uses [PluginAvailability] to check at runtime before this
 * handler is invoked, ensuring ClassNotFoundException is never thrown in production.
 *
 * ## Supported Elements
 * - Classes (including inner classes)
 * - Interfaces
 * - Enums
 * - Records
 *
 * ## Target Format
 * The target package should be a fully qualified package name, e.g.:
 * - `com.example.utils` - Move to utils package
 * - `com.example.model.dto` - Nested package
 *
 * ## Requirements
 * - The target package directory must already exist
 * - The file must be in project source roots (src/main or src/test)
 * - Main source files can only be moved to main sources, test files to test sources
 *
 * @see com.igorlink.claudeidetools.handlers.MoveHandler The main handler that routes to this language-specific handler
 * @see PluginAvailability Plugin detection utility
 */
object JavaMoveHandler {

    /**
     * Attempts to move a Java class to a different package.
     *
     * Finds the enclosing Java class for the given element and moves it
     * to the target package using IntelliJ's MoveClassesOrPackagesProcessor.
     *
     * @param project The IntelliJ project context
     * @param element The PSI element at the target location (class or element within a class)
     * @param targetPackage The fully qualified target package name (e.g., "com.example.utils")
     * @param searchInComments Whether to also update occurrences in comments
     * @param searchInNonJavaFiles Whether to also update occurrences in non-Java files
     * @return [RefactoringResponse] with success status and message
     */
    fun move(
        project: Project,
        element: PsiElement,
        targetPackage: String,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): RefactoringResponse {
        val psiClass = ReadAction.compute<PsiClass?, Throwable> {
            PsiTreeUtil.getParentOfType(element, PsiClass::class.java, false)
                ?: element as? PsiClass
        }

        if (psiClass == null) {
            return RefactoringResponse(
                false,
                "No Java class found at the specified location"
            )
        }

        // Determine source root type (main vs test)
        val sourceRootType = SourceRootDetector.determineSourceRootType(project, psiClass)

        if (sourceRootType == SourceRootType.NONE) {
            val className = ReadAction.compute<String, Throwable> { psiClass.name } ?: "unknown"
            return RefactoringResponse(
                false,
                "Class '$className' is not in project source roots. " +
                "Only files in src/main or src/test can be moved."
            )
        }

        return performJavaMove(
            project,
            psiClass,
            targetPackage,
            sourceRootType == SourceRootType.TEST,
            searchInComments,
            searchInNonJavaFiles
        )
    }

    /**
     * Performs the actual Java class move using IntelliJ's MoveClassesOrPackagesProcessor.
     *
     * Executes the move within a write command action on the EDT. The target package
     * directory must already exist - this method does not create new packages.
     *
     * @param project The IntelliJ project context
     * @param psiClass The Java class to move
     * @param targetPackage The fully qualified target package name
     * @param isTestSource If true, looks for target in test sources; otherwise in main sources
     * @param searchInComments Whether to also update occurrences in comments
     * @param searchInNonJavaFiles Whether to also update occurrences in non-Java files
     * @return [RefactoringResponse] indicating success or failure
     */
    private fun performJavaMove(
        project: Project,
        psiClass: PsiClass,
        targetPackage: String,
        isTestSource: Boolean,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): RefactoringResponse {
        // Access to PSI element name requires ReadAction when called from background thread
        val className = ReadAction.compute<String, Throwable> { psiClass.name } ?: "unknown"

        // MoveClassesOrPackagesProcessor manages its own write action internally
        return RefactoringExecutor.executeOnEdtWithCallback(
            timeoutSeconds = RefactoringTimeouts.MOVE
        ) { callback ->
            val targetDir = findPackageDirectory(project, targetPackage, isTestSource)
            if (targetDir == null) {
                val rootType = if (isTestSource) "test" else "main"
                callback.failure("Cannot find target package '$targetPackage' in $rootType sources. Make sure it exists.")
                return@executeOnEdtWithCallback
            }

            val packageWrapper = PackageWrapper(PsiManager.getInstance(project), targetPackage)
            val destination = SingleSourceRootMoveDestination(packageWrapper, targetDir)

            val processor = MoveClassesOrPackagesProcessor(
                project,
                arrayOf(psiClass),
                destination,
                searchInComments,
                searchInNonJavaFiles,
                null   // moveCallback
            )
            processor.setPreviewUsages(false)
            processor.run()

            callback.success("Moved '$className' to '$targetPackage' in project '${project.name}'")
        }
    }

    /**
     * Finds the PSI directory for a package in the appropriate source root type.
     *
     * @param project The IntelliJ project context
     * @param packageName The fully qualified package name (e.g., "com.example.utils")
     * @param isTestSource If true, looks in test sources; otherwise in main sources
     * @return The [PsiDirectory] for the package, or null if not found
     */
    private fun findPackageDirectory(project: Project, packageName: String, isTestSource: Boolean): PsiDirectory? {
        return ReadAction.compute<PsiDirectory?, Throwable> {
            val existingPackage = JavaPsiFacade.getInstance(project).findPackage(packageName)
            val directories = existingPackage?.directories ?: return@compute null

            val fileIndex = ProjectRootManager.getInstance(project).fileIndex

            // Find directory matching the same source root type as the original file
            directories.firstOrNull { dir ->
                val vFile = dir.virtualFile
                if (isTestSource) {
                    fileIndex.isInTestSourceContent(vFile)
                } else {
                    fileIndex.isInSourceContent(vFile) && !fileIndex.isInTestSourceContent(vFile)
                }
            }
        }
    }

    /**
     * Checks if the Java plugin is available in the current IDE.
     *
     * Java is typically always available in IntelliJ-based IDEs, but this method
     * is provided for consistency with other language handlers and to support
     * IDEs like WebStorm that may not have Java support.
     *
     * Delegates to [PluginAvailability] for centralized, cached plugin detection.
     *
     * @return `true` if Java plugin is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.JAVA)
}
