package com.igorlink.claudeidetools.handlers.languages

import com.igorlink.claudeidetools.model.RefactoringResponse
import com.igorlink.claudeidetools.util.PluginAvailability
import com.igorlink.claudeidetools.util.PsiReflectionUtils
import com.igorlink.claudeidetools.util.RefactoringExecutor
import com.igorlink.claudeidetools.util.RefactoringTimeouts
import com.igorlink.claudeidetools.util.SourceRootDetector
import com.igorlink.claudeidetools.util.SourceRootType
import com.igorlink.claudeidetools.util.SupportedLanguage
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager

/**
 * Handler for Java move refactoring operations.
 *
 * Supports moving Java classes to different packages. Uses IntelliJ's
 * MoveClassesOrPackagesProcessor for reliable refactoring with automatic
 * import updates across the entire project.
 *
 * ## Design Decision: Reflection-Based Access
 *
 * This handler uses reflection to access Java-specific classes (PsiClass, JavaPsiFacade,
 * MoveClassesOrPackagesProcessor) to avoid ClassNotFoundException in IDEs without Java plugin
 * (WebStorm, PhpStorm, etc.).
 *
 * The [isAvailable] method uses [PluginAvailability] to check at runtime before this
 * handler is invoked.
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

    private const val PSI_CLASS = "com.intellij.psi.PsiClass"
    private const val MOVE_PROCESSOR = "com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor"
    private const val PACKAGE_WRAPPER = "com.intellij.refactoring.PackageWrapper"
    private const val SINGLE_SOURCE_ROOT_DESTINATION = "com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination"
    private const val MOVE_DESTINATION = "com.intellij.refactoring.MoveDestination"

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
        val psiClass = ReadAction.compute<PsiElement?, Throwable> {
            PsiReflectionUtils.findParentOfType(element, PSI_CLASS, strict = false)
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
            val className = ReadAction.compute<String, Throwable> {
                PsiReflectionUtils.getElementName(psiClass)
            }
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
     * Performs the actual Java class move using IntelliJ's MoveClassesOrPackagesProcessor via reflection.
     */
    private fun performJavaMove(
        project: Project,
        psiClass: PsiElement,
        targetPackage: String,
        isTestSource: Boolean,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): RefactoringResponse {
        val className = ReadAction.compute<String, Throwable> {
            PsiReflectionUtils.getElementName(psiClass)
        }

        return RefactoringExecutor.executeOnEdtWithCallback(
            timeoutSeconds = RefactoringTimeouts.MOVE
        ) { callback ->
            try {
                val targetDir = ReadAction.compute<PsiDirectory?, Throwable> {
                    PsiReflectionUtils.findPackageDirectory(project, targetPackage, isTestSource)
                }

                if (targetDir == null) {
                    val rootType = if (isTestSource) "test" else "main"
                    callback.failure("Cannot find target package '$targetPackage' in $rootType sources. Make sure it exists.")
                    return@executeOnEdtWithCallback
                }

                // Create PackageWrapper via reflection
                val packageWrapper = createPackageWrapper(project, targetPackage)
                if (packageWrapper == null) {
                    callback.failure("Failed to create package wrapper for '$targetPackage'")
                    return@executeOnEdtWithCallback
                }

                // Create SingleSourceRootMoveDestination via reflection
                val destination = createMoveDestination(packageWrapper, targetDir)
                if (destination == null) {
                    callback.failure("Failed to create move destination for '$targetPackage'")
                    return@executeOnEdtWithCallback
                }

                // Create and run MoveClassesOrPackagesProcessor via reflection
                val success = runMoveProcessor(project, psiClass, destination, searchInComments, searchInNonJavaFiles)

                if (success) {
                    callback.success("Moved '$className' to '$targetPackage' in project '${project.name}'")
                } else {
                    callback.failure("Failed to execute move refactoring for '$className'")
                }
            } catch (e: Exception) {
                callback.failure("Move refactoring failed: ${e.message}")
            }
        }
    }

    /**
     * Creates a PackageWrapper instance via reflection.
     */
    private fun createPackageWrapper(project: Project, packageName: String): Any? {
        return try {
            val clazz = Class.forName(PACKAGE_WRAPPER)
            val constructor = clazz.getConstructor(PsiManager::class.java, String::class.java)
            constructor.newInstance(PsiManager.getInstance(project), packageName)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates a SingleSourceRootMoveDestination instance via reflection.
     */
    private fun createMoveDestination(packageWrapper: Any, targetDir: PsiDirectory): Any? {
        return try {
            val packageWrapperClass = Class.forName(PACKAGE_WRAPPER)
            val clazz = Class.forName(SINGLE_SOURCE_ROOT_DESTINATION)
            val constructor = clazz.getConstructor(packageWrapperClass, PsiDirectory::class.java)
            constructor.newInstance(packageWrapper, targetDir)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Creates and runs MoveClassesOrPackagesProcessor via reflection.
     */
    private fun runMoveProcessor(
        project: Project,
        psiClass: PsiElement,
        destination: Any,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): Boolean {
        return try {
            val psiClassClass = Class.forName(PSI_CLASS)
            val moveDestinationClass = Class.forName(MOVE_DESTINATION)
            val moveCallbackClass = Class.forName("com.intellij.refactoring.MoveCallback")

            val processorClass = Class.forName(MOVE_PROCESSOR)

            // Create array of PsiClass
            val classArray = java.lang.reflect.Array.newInstance(psiClassClass, 1)
            java.lang.reflect.Array.set(classArray, 0, psiClass)

            // Find constructor: (Project, PsiClass[], MoveDestination, boolean, boolean, MoveCallback)
            val constructor = processorClass.constructors.find { c ->
                c.parameterCount == 6 &&
                c.parameterTypes[0] == Project::class.java &&
                c.parameterTypes[1].isArray &&
                c.parameterTypes[2] == moveDestinationClass &&
                c.parameterTypes[3] == Boolean::class.javaPrimitiveType &&
                c.parameterTypes[4] == Boolean::class.javaPrimitiveType
            } ?: return false

            val processor = constructor.newInstance(
                project,
                classArray,
                destination,
                searchInComments,
                searchInNonJavaFiles,
                null  // moveCallback
            )

            // Call setPreviewUsages(false)
            val setPreviewMethod = processorClass.getMethod("setPreviewUsages", Boolean::class.javaPrimitiveType)
            setPreviewMethod.invoke(processor, false)

            // Call run()
            val runMethod = processorClass.getMethod("run")
            runMethod.invoke(processor)

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the Java plugin is available in the current IDE.
     *
     * @return `true` if Java plugin is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.JAVA)
}
