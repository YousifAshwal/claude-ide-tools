package com.igorlink.claudejetbrainstools.handlers

import com.igorlink.claudejetbrainstools.model.MoveRequest
import com.igorlink.claudejetbrainstools.model.RefactoringResponse
import com.igorlink.claudejetbrainstools.util.HandlerUtils
import com.igorlink.claudejetbrainstools.util.LanguageDetector
import com.igorlink.claudejetbrainstools.util.LanguageHandlerRegistry
import com.igorlink.claudejetbrainstools.util.RefactoringExecutor
import com.igorlink.claudejetbrainstools.util.RefactoringTimeouts
import com.igorlink.claudejetbrainstools.util.SourceRootDetector
import com.igorlink.claudejetbrainstools.util.SourceRootType
import com.igorlink.claudejetbrainstools.util.SupportedLanguage
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.refactoring.PackageWrapper

/**
 * Handler for the `/move` endpoint performing move class/symbol refactoring.
 *
 * This handler moves code elements to different packages/modules and automatically
 * updates all references throughout the project. It supports multiple programming
 * languages with automatic detection and routing to language-specific handlers.
 *
 * ## Supported Languages and Elements
 *
 * | Language | Movable Elements | Target Format |
 * |----------|------------------|---------------|
 * | Java | Classes | Package name (e.g., `com.example.newpkg`) |
 * | Kotlin | Classes, objects, functions | Package name |
 * | TypeScript/JavaScript | Classes, functions, variables | File path |
 * | Python | Classes, functions | Module path (e.g., `package.module`) |
 * | Go | Types, functions | Package path |
 * | Rust | Structs, enums, functions, traits | Module path (e.g., `crate::utils`) |
 *
 * ## Request Parameters
 * - `file`: Absolute path to the file containing the element
 * - `line`: Line number (1-based) of the element
 * - `column`: Column number (1-based) pointing to the element name
 * - `targetPackage`: Target package/module path (format depends on language)
 * - `project`: Optional project name for disambiguation
 *
 * ## Java Usage Example
 * ```json
 * {
 *   "file": "/path/to/MyClass.java",
 *   "line": 5,
 *   "column": 14,
 *   "targetPackage": "com.example.newpackage"
 * }
 * ```
 *
 * ## Important Notes
 * - The target package must already exist for Java moves
 * - Language detection is automatic based on file extension and PSI type
 * - Some language handlers may require additional IDE plugins
 *
 * @see MoveRequest The request data class
 * @see RefactoringResponse The response data class
 * @see KotlinMoveHandler Language-specific handler for Kotlin
 * @see JavaScriptMoveHandler Language-specific handler for JS/TS
 * @see PythonMoveHandler Language-specific handler for Python
 * @see GoMoveHandler Language-specific handler for Go
 * @see RustMoveHandler Language-specific handler for Rust
 */
object MoveHandler {

    /**
     * Handles the move refactoring request.
     *
     * Detects the programming language of the file and routes the request
     * to the appropriate language-specific handler. For Java, the move is
     * performed directly; for other languages, dedicated handlers are used.
     *
     * @param request The move request containing file location and target package
     * @return [RefactoringResponse] indicating success or failure with a message
     */
    fun handle(request: MoveRequest): RefactoringResponse {
        if (request.targetPackage.isBlank()) {
            return RefactoringResponse(false, "Target package/module cannot be empty")
        }

        return HandlerUtils.withElementLookup(
            file = request.file,
            line = request.line,
            column = request.column,
            project = request.project,
            errorResponseFactory = { message -> RefactoringResponse(false, message) }
        ) { context ->
            // Detect language and route to appropriate handler
            val language = LanguageDetector.detect(context.element.containingFile)

            routeToLanguageHandler(
                context.project,
                context.element,
                language,
                request.targetPackage,
                request.searchInComments,
                request.searchInNonJavaFiles
            )
        }
    }

    /**
     * Routes the move request to the appropriate language-specific handler.
     *
     * Uses [LanguageHandlerRegistry] for centralized routing to language-specific handlers.
     * Java is handled directly in this class; other languages are delegated via the registry.
     *
     * @param project The IntelliJ project context
     * @param element The PSI element to move
     * @param language The detected programming language
     * @param targetPackage The target package/module path
     * @param searchInComments Whether to also update occurrences in comments
     * @param searchInNonJavaFiles Whether to also update occurrences in non-Java files
     * @return [RefactoringResponse] with the operation result
     */
    private fun routeToLanguageHandler(
        project: Project,
        element: com.intellij.psi.PsiElement,
        language: SupportedLanguage,
        targetPackage: String,
        searchInComments: Boolean,
        searchInNonJavaFiles: Boolean
    ): RefactoringResponse {
        // Java is handled directly in this class
        if (language == SupportedLanguage.JAVA) {
            return handleJavaMove(project, element, targetPackage, searchInComments, searchInNonJavaFiles)
        }

        // Unknown language
        if (language == SupportedLanguage.UNKNOWN) {
            return RefactoringResponse(
                false,
                "Unsupported language for move refactoring. " +
                "Supported: Java, Kotlin, TypeScript, JavaScript, Python, Go, Rust."
            )
        }

        // Delegate to LanguageHandlerRegistry for other languages
        return LanguageHandlerRegistry.move(
            language, project, element, targetPackage, searchInComments, searchInNonJavaFiles
        ) ?: RefactoringResponse(
            false,
            "No handler registered for language: ${LanguageDetector.getLanguageName(language)}"
        )
    }

    /**
     * Handles Java class move refactoring.
     *
     * Finds the enclosing Java class for the given element and prepares it
     * for moving to the target package.
     *
     * @param project The IntelliJ project context
     * @param element The PSI element (class or element within a class)
     * @param targetPackage The fully qualified target package name
     * @param searchInComments Whether to also update occurrences in comments
     * @param searchInNonJavaFiles Whether to also update occurrences in non-Java files
     * @return [RefactoringResponse] with the operation result
     */
    private fun handleJavaMove(
        project: Project,
        element: com.intellij.psi.PsiElement,
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
        val virtualFile = ReadAction.compute<com.intellij.openapi.vfs.VirtualFile?, Throwable> {
            psiClass.containingFile?.virtualFile
        }
        val sourceRootType = if (virtualFile != null) {
            SourceRootDetector.determineSourceRootType(project, virtualFile)
        } else {
            SourceRootType.NONE
        }

        if (sourceRootType == SourceRootType.NONE) {
            val className = ReadAction.compute<String, Throwable> { psiClass.name } ?: "unknown"
            return RefactoringResponse(
                false,
                "Class '$className' is not in project source roots. " +
                "Only files in src/main or src/test can be moved."
            )
        }

        return performJavaMove(project, psiClass, targetPackage, sourceRootType == SourceRootType.TEST, searchInComments, searchInNonJavaFiles)
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
}
