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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor

/**
 * Handler for Kotlin move refactoring operations.
 *
 * Supports moving Kotlin declarations (classes, objects, interfaces, functions,
 * properties) to different packages. Uses reflection to access Kotlin plugin APIs
 * to avoid compile-time dependency on the optional Kotlin plugin.
 *
 * ## Supported Declarations
 * - Classes and interfaces
 * - Objects (including companion objects)
 * - Top-level functions
 * - Top-level properties
 * - Type aliases
 *
 * ## Current Limitations
 * Full Kotlin move refactoring requires editor context and the Kotlin IDE plugin's
 * complex refactoring infrastructure. Currently, this handler identifies the declaration
 * but cannot perform the move programmatically. Users should use the IDE UI for moves.
 *
 * @see com.igorlink.claudeidetools.handlers.MoveHandler The main handler that routes to this language-specific handler
 * @see PsiReflectionUtils Reflection utilities for accessing Kotlin PSI classes
 * @see PluginAvailability Plugin detection utility
 */
object KotlinMoveHandler {

    /** Base class for named Kotlin declarations. */
    private const val KT_NAMED_DECLARATION_CLASS = "org.jetbrains.kotlin.psi.KtNamedDeclaration"

    /** Base class for Kotlin classes and objects (has toLightClass()). */
    private const val KT_CLASS_OR_OBJECT_CLASS = "org.jetbrains.kotlin.psi.KtClassOrObject"

    /** Kotlin move processor class. */
    private const val MOVE_KOTLIN_DECLARATIONS_PROCESSOR =
        "org.jetbrains.kotlin.idea.refactoring.move.moveDeclarations.MoveKotlinDeclarationsProcessor"

    /** Kotlin move descriptor class. */
    private const val MOVE_DECLARATIONS_DESCRIPTOR =
        "org.jetbrains.kotlin.idea.refactoring.move.MoveDeclarationsDescriptor"

    /** Type mappings for Kotlin declaration types. */
    private val DECLARATION_TYPE_MAPPINGS = mapOf(
        "Class" to "class",
        "Object" to "object",
        "Function" to "function",
        "Property" to "property",
        "TypeAlias" to "typealias"
    )

    /**
     * Attempts to move a Kotlin declaration to a different package.
     *
     * Currently identifies the declaration at the specified location but cannot
     * perform the actual move due to Kotlin plugin API complexity. Returns an
     * informative error message with declaration details.
     *
     * @param project The IntelliJ project context
     * @param element The PSI element at the target location
     * @param targetPackage The target package name (e.g., "com.example.utils")
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
        // Find KtClassOrObject (class, object, interface)
        val classOrObject = PsiReflectionUtils.findAncestorOfType(element, KT_CLASS_OR_OBJECT_CLASS)

        if (classOrObject == null) {
            // Try to find any named declaration for better error message
            val declaration = PsiReflectionUtils.findAncestorOfType(element, KT_NAMED_DECLARATION_CLASS)
            if (declaration != null) {
                val name = ReadAction.compute<String, Throwable> {
                    PsiReflectionUtils.getElementName(declaration)
                }
                val type = ReadAction.compute<String, Throwable> {
                    PsiReflectionUtils.getElementTypeFromClassName(
                        declaration, DECLARATION_TYPE_MAPPINGS, "declaration"
                    )
                }
                return RefactoringResponse(
                    false,
                    "Cannot move $type '$name': only classes, objects, and interfaces " +
                    "can be moved programmatically. For top-level functions/properties, use IDE UI."
                )
            }
            return RefactoringResponse(
                false,
                "No movable Kotlin declaration found at the specified location. " +
                "Supported: class, object, interface."
            )
        }

        // Get file and declaration info
        val (psiFile, declarationName) = ReadAction.compute<Pair<PsiFile?, String>, Throwable> {
            Pair(classOrObject.containingFile, PsiReflectionUtils.getElementName(classOrObject))
        }

        if (psiFile == null) {
            return RefactoringResponse(false, "Cannot find containing file for '$declarationName'")
        }

        // Determine source root type (main vs test)
        val sourceRootType = SourceRootDetector.determineSourceRootType(project, psiFile)

        if (sourceRootType == SourceRootType.NONE) {
            return RefactoringResponse(
                false,
                "File '$declarationName' is not in project source roots. " +
                "Only files in src/main or src/test can be moved."
            )
        }

        // Move the file to the target package directory (same root type as source)
        return performMoveFile(project, psiFile, declarationName, targetPackage, sourceRootType == SourceRootType.TEST, searchInComments, searchInNonJavaFiles)
    }

    /**
     * Performs the move by moving the Kotlin file to target package directory.
     * MoveFilesOrDirectoriesProcessor updates package declarations and imports automatically.
     *
     * @param isTestSource If true, looks for target in test sources; otherwise in main sources
     * @param searchInComments Whether to also update occurrences in comments
     * @param searchInNonJavaFiles Whether to also update occurrences in non-Java files
     */
    private fun performMoveFile(
        project: Project,
        psiFile: PsiFile,
        className: String,
        targetPackage: String,
        isTestSource: Boolean,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): RefactoringResponse {
        return RefactoringExecutor.executeOnEdtWithCallback(
            timeoutSeconds = RefactoringTimeouts.MOVE
        ) { callback ->
            val targetDir = findPackageDirectory(project, targetPackage, isTestSource)
            if (targetDir == null) {
                val rootType = if (isTestSource) "test" else "main"
                callback.failure("Cannot find target package '$targetPackage' in $rootType sources. Make sure it exists.")
                return@executeOnEdtWithCallback
            }

            val processor = MoveFilesOrDirectoriesProcessor(
                project,
                arrayOf(psiFile),
                targetDir,
                true,  // searchForReferences - update all imports and usages
                searchInComments,
                searchInNonJavaFiles,
                null,  // moveCallback
                null   // prepareSuccessfulCallback
            )
            processor.setPreviewUsages(false)
            processor.run()

            callback.success("Moved '$className' to '$targetPackage' in project '${project.name}'")
        }
    }

    /**
     * Finds the PSI directory for a package in the appropriate source root type.
     *
     * Uses [PsiReflectionUtils.findPackageDirectory] which first attempts to find the package
     * via JavaPsiFacade (if Java plugin is available) and falls back to searching source roots
     * directly. This avoids compile-time dependency on Java-specific classes.
     *
     * @param isTestSource If true, looks in test sources; otherwise in main sources
     */
    private fun findPackageDirectory(project: Project, packageName: String, isTestSource: Boolean): com.intellij.psi.PsiDirectory? {
        return ReadAction.compute<com.intellij.psi.PsiDirectory?, Throwable> {
            PsiReflectionUtils.findPackageDirectory(project, packageName, isTestSource)
        }
    }

    /**
     * Checks if the Kotlin plugin is available in the current IDE.
     *
     * Delegates to [PluginAvailability] for centralized, cached plugin detection.
     *
     * @return `true` if Kotlin plugin is available, `false` otherwise
     */
    fun isAvailable(): Boolean = PluginAvailability.isAvailable(SupportedLanguage.KOTLIN)
}
