package com.igorlink.claudeidetools.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility functions for reflection-based PSI element operations.
 *
 * This object provides common reflection patterns used across language-specific
 * handlers to interact with PSI elements from optional plugins (Kotlin, JavaScript,
 * Python, Go, Rust) without compile-time dependencies.
 *
 * ## Thread Safety
 * All methods in this object are stateless and thread-safe. However, callers must ensure
 * that PSI elements are accessed within appropriate read actions when required by the
 * IntelliJ Platform threading model.
 *
 * ## Usage
 * These utilities enable handlers to work with language-specific PSI classes
 * that may not be available at compile time:
 *
 * ```kotlin
 * // Find an ancestor of a specific type
 * val declaration = PsiReflectionUtils.findAncestorOfType(
 *     element,
 *     "org.jetbrains.kotlin.psi.KtNamedDeclaration"
 * )
 *
 * // Get element name via reflection
 * val name = PsiReflectionUtils.getElementName(declaration)
 *
 * // Determine element type from class name
 * val type = PsiReflectionUtils.getElementTypeFromClassName(declaration, KOTLIN_TYPE_MAPPINGS)
 * ```
 *
 * @see com.igorlink.claudeidetools.handlers.languages.KotlinMoveHandler
 * @see com.igorlink.claudeidetools.handlers.languages.JavaScriptMoveHandler
 * @see com.igorlink.claudeidetools.handlers.languages.PythonMoveHandler
 * @see com.igorlink.claudeidetools.handlers.languages.GoMoveHandler
 * @see com.igorlink.claudeidetools.handlers.languages.RustMoveHandler
 */
object PsiReflectionUtils {

    /**
     * Cache for Class.forName results to avoid repeated reflection lookups.
     * Maps fully qualified class names to their Class objects, or null if the class is not available.
     */
    private val classCache = ConcurrentHashMap<String, Class<*>?>()

    /**
     * Gets a class by name with caching.
     * Returns null if the class is not available.
     *
     * @param className The fully qualified class name to load
     * @return The Class object, or null if the class cannot be loaded
     */
    private fun getClassCached(className: String): Class<*>? {
        return classCache.computeIfAbsent(className) { name ->
            try {
                Class.forName(name)
            } catch (e: ClassNotFoundException) {
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Internal helper for ancestor finding with configurable matching logic.
     *
     * @param element The starting PSI element
     * @param className The fully qualified class name to search for
     * @param matchPredicate Predicate that determines if the current element matches,
     *     receiving the target class and the current element being examined
     * @return The first matching ancestor, or null if not found
     */
    private fun findAncestorInternal(
        element: PsiElement,
        className: String,
        matchPredicate: (targetClass: Class<*>, current: PsiElement) -> Boolean
    ): PsiElement? {
        val targetClass = getClassCached(className) ?: return null
        var current: PsiElement? = element
        while (current != null) {
            if (matchPredicate(targetClass, current)) {
                return current
            }
            current = current.parent
        }
        return null
    }

    /**
     * Traverses up the PSI tree to find the first ancestor matching the given class name.
     *
     * Uses reflection to check if each parent element is an instance of the specified
     * class, allowing language handlers to find specific PSI types without compile-time
     * dependencies on optional plugins.
     *
     * @param element The starting PSI element
     * @param className The fully qualified class name to search for
     *     (e.g., "org.jetbrains.kotlin.psi.KtNamedDeclaration")
     * @return The first matching ancestor, or null if not found or if the class
     *     cannot be loaded (ClassNotFoundException)
     */
    fun findAncestorOfType(element: PsiElement, className: String): PsiElement? {
        return findAncestorInternal(element, className) { targetClass, current ->
            targetClass.isInstance(current)
        }
    }

    /**
     * Traverses up the PSI tree to find the first ancestor matching the given class name
     * and satisfying an additional predicate based on class name patterns.
     *
     * This is useful when you need to find not just any instance of a base class,
     * but a specific subtype identified by class name patterns (e.g., finding a
     * JSElement that is specifically a Class or Function).
     *
     * @param element The starting PSI element
     * @param className The fully qualified base class name to check against
     * @param classNamePredicate Predicate that receives the simple class name
     *     of matched elements and returns true if it matches the desired pattern
     * @return The first matching ancestor, or null if not found
     */
    fun findAncestorOfTypeWithPredicate(
        element: PsiElement,
        className: String,
        classNamePredicate: (String) -> Boolean
    ): PsiElement? {
        return findAncestorInternal(element, className) { targetClass, current ->
            targetClass.isInstance(current) && classNamePredicate(current.javaClass.simpleName)
        }
    }

    /**
     * Gets the name of a PSI element using reflection.
     *
     * Invokes the `getName()` method via reflection to retrieve the element's name.
     * This is commonly used for PSI elements from optional plugins where the
     * getName() method cannot be called directly.
     *
     * @param element The PSI element to get the name from
     * @param defaultValue The value to return if the name cannot be retrieved
     *     (default: "unknown")
     * @return The element's name, or [defaultValue] if:
     *     - The getName() method doesn't exist
     *     - The method throws an exception
     *     - The method returns null
     */
    fun getElementName(element: PsiElement, defaultValue: String = "unknown"): String {
        return try {
            val getNameMethod = element.javaClass.getMethod("getName")
            getNameMethod.invoke(element) as? String ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Determines the element type based on class name patterns.
     *
     * Examines the element's class simple name and matches it against the provided
     * type mappings to return a human-readable type description. This is useful for
     * generating informative error messages about declarations that cannot be moved.
     *
     * @param element The PSI element to determine the type for
     * @param typeMappings A map of class name substrings to type descriptions.
     *     Keys are checked in order using `contains()` on the class simple name.
     *     Example: mapOf("Class" to "class", "Function" to "function")
     * @param defaultType The type to return if no mapping matches (default: "element")
     * @return The matched type description, or [defaultType] if no pattern matches
     */
    fun getElementTypeFromClassName(
        element: PsiElement,
        typeMappings: Map<String, String>,
        defaultType: String = "element"
    ): String {
        val className = element.javaClass.simpleName
        for ((pattern, type) in typeMappings) {
            if (className.contains(pattern)) {
                return type
            }
        }
        return defaultType
    }

    /**
     * Checks if a class with the given fully qualified name can be loaded.
     *
     * This is useful for checking if a language plugin is available in the
     * current IDE before attempting to use its PSI classes.
     *
     * @param className The fully qualified class name to check
     * @return true if the class can be loaded, false otherwise
     */
    fun isClassAvailable(className: String): Boolean {
        return getClassCached(className) != null
    }

    /**
     * Checks if a PSI file is an instance of the specified class type.
     *
     * This method uses reflection to verify file types against plugin-specific
     * PSI classes without requiring compile-time dependencies.
     *
     * @param psiFile The file to check
     * @param className The fully qualified class name to check against (e.g., "com.jetbrains.python.psi.PyFile")
     * @return `true` if the file is an instance of the specified class, `false` otherwise or if the class is not available
     */
    fun isFileOfType(psiFile: PsiFile, className: String): Boolean {
        val targetClass = getClassCached(className) ?: return false
        return targetClass.isInstance(psiFile)
    }

    /**
     * Finds the PSI directory for a package using reflection to avoid compile-time
     * dependency on Java-specific classes like JavaPsiFacade.
     *
     * This method first attempts to use JavaPsiFacade via reflection if the Java plugin
     * is available. If not, it falls back to a directory-based search that finds
     * directories matching the package path in project source roots.
     *
     * ## Thread Safety
     * This method must be called within a ReadAction. It does NOT wrap itself in
     * ReadAction to allow callers to control transaction boundaries.
     *
     * @param project The IntelliJ project context
     * @param packageName The package name (e.g., "com.example.utils")
     * @param isTestSource If true, looks in test sources; otherwise in main sources
     * @return The PsiDirectory for the package, or null if not found
     */
    fun findPackageDirectory(project: Project, packageName: String, isTestSource: Boolean): PsiDirectory? {
        // First, try using JavaPsiFacade via reflection (if Java plugin is available)
        val javaPsiFacadeResult = findPackageDirectoryViaJavaPsiFacade(project, packageName, isTestSource)
        if (javaPsiFacadeResult != null) {
            return javaPsiFacadeResult
        }

        // Fallback: search for directory matching package path in source roots
        return findPackageDirectoryViaSourceRoots(project, packageName, isTestSource)
    }

    /**
     * Attempts to find package directory using JavaPsiFacade via reflection.
     *
     * @return The directory if found via JavaPsiFacade, null if Java plugin is not available or package not found
     */
    private fun findPackageDirectoryViaJavaPsiFacade(
        project: Project,
        packageName: String,
        isTestSource: Boolean
    ): PsiDirectory? {
        return try {
            // Load JavaPsiFacade class via reflection
            val javaPsiFacadeClass = getClassCached("com.intellij.psi.JavaPsiFacade") ?: return null

            // Get the getInstance(Project) method
            val getInstanceMethod = javaPsiFacadeClass.getMethod("getInstance", Project::class.java)
            val facade = getInstanceMethod.invoke(null, project) ?: return null

            // Get the findPackage(String) method
            val findPackageMethod = javaPsiFacadeClass.getMethod("findPackage", String::class.java)
            val psiPackage = findPackageMethod.invoke(facade, packageName) ?: return null

            // Get the getDirectories() method from PsiPackage
            val getDirectoriesMethod = psiPackage.javaClass.getMethod("getDirectories")
            @Suppress("UNCHECKED_CAST")
            val directories = getDirectoriesMethod.invoke(psiPackage) as? Array<PsiDirectory> ?: return null

            // Find directory matching the source root type
            val fileIndex = ProjectRootManager.getInstance(project).fileIndex
            directories.firstOrNull { dir ->
                val vFile = dir.virtualFile
                if (isTestSource) {
                    fileIndex.isInTestSourceContent(vFile)
                } else {
                    fileIndex.isInSourceContent(vFile) && !fileIndex.isInTestSourceContent(vFile)
                }
            }
        } catch (e: Exception) {
            // Java plugin not available or reflection failed - will fallback to source root search
            null
        }
    }

    /**
     * Fallback method to find package directory by searching source roots.
     *
     * This method converts the package name to a path (e.g., "com.example.utils" -> "com/example/utils")
     * and searches for a matching directory in the project's source roots.
     *
     * @return The directory if found, null otherwise
     */
    private fun findPackageDirectoryViaSourceRoots(
        project: Project,
        packageName: String,
        isTestSource: Boolean
    ): PsiDirectory? {
        val packagePath = packageName.replace('.', '/')
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        val psiManager = PsiManager.getInstance(project)

        // Get all source roots
        val sourceRoots = if (isTestSource) {
            ProjectRootManager.getInstance(project).contentSourceRoots.filter { root ->
                fileIndex.isInTestSourceContent(root)
            }
        } else {
            ProjectRootManager.getInstance(project).contentSourceRoots.filter { root ->
                fileIndex.isInSourceContent(root) && !fileIndex.isInTestSourceContent(root)
            }
        }

        // Search for the package directory in each source root
        for (sourceRoot in sourceRoots) {
            val targetDir = sourceRoot.findFileByRelativePath(packagePath)
            if (targetDir != null && targetDir.isDirectory) {
                return psiManager.findDirectory(targetDir)
            }
        }

        return null
    }
}
