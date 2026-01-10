package com.igorlink.claudeidetools.model

/**
 * Enumeration of error codes for refactoring operations.
 *
 * These codes provide machine-readable error classification that allows
 * clients to handle specific error types programmatically.
 */
enum class ErrorCode {
    /** The specified file was not found on the file system. */
    FILE_NOT_FOUND,

    /** The file does not belong to any open project. */
    FILE_NOT_IN_PROJECT,

    /** No PSI element exists at the specified location. */
    ELEMENT_NOT_FOUND,

    /** The element at the location cannot be refactored. */
    ELEMENT_NOT_REFACTORABLE,

    /** The line or column number is out of bounds. */
    LOCATION_OUT_OF_BOUNDS,

    /** The IDE is currently indexing. */
    INDEXING_IN_PROGRESS,

    /** The specified project was not found. */
    PROJECT_NOT_FOUND,

    /** Invalid input parameters provided. */
    INVALID_INPUT,

    /** The refactoring operation failed. */
    REFACTORING_FAILED,

    /** Required plugin is not available. */
    PLUGIN_NOT_AVAILABLE,

    /** The language is not supported for this operation. */
    UNSUPPORTED_LANGUAGE,

    /** An unexpected error occurred. */
    INTERNAL_ERROR
}

/**
 * Structured representation of an operation error.
 *
 * Combines a human-readable message with a machine-readable error code
 * for better error handling on both the user and programmatic sides.
 *
 * ## Usage
 * ```kotlin
 * val error = OperationError(
 *     code = ErrorCode.FILE_NOT_FOUND,
 *     message = "File not found: /path/to/file.java"
 * )
 * ```
 *
 * @property code Machine-readable error code for programmatic handling
 * @property message Human-readable error description
 */
data class OperationError(
    val code: ErrorCode,
    val message: String
) {
    companion object {
        /**
         * Creates an error for a file not found condition.
         *
         * @param filePath The path to the file that was not found
         */
        fun fileNotFound(filePath: String): OperationError =
            OperationError(ErrorCode.FILE_NOT_FOUND, "File not found: $filePath")

        /**
         * Creates an error for a file not belonging to any project.
         *
         * @param filePath The path to the file
         * @param openProjects Names of currently open projects
         */
        fun fileNotInProject(filePath: String, openProjects: List<String>): OperationError =
            OperationError(
                ErrorCode.FILE_NOT_IN_PROJECT,
                "File '$filePath' does not belong to any open project. " +
                    "Open projects: ${openProjects.joinToString(", ")}. " +
                    "You can specify 'project' parameter explicitly."
            )

        /**
         * Creates an error for an element not found at a location.
         *
         * @param filePath The file path
         * @param line The line number
         * @param column The column number
         */
        fun elementNotFound(filePath: String, line: Int, column: Int): OperationError =
            OperationError(
                ErrorCode.ELEMENT_NOT_FOUND,
                "No code element found at $filePath:$line:$column"
            )

        /**
         * Creates an error for a location out of bounds.
         *
         * @param message Description of the bounds violation
         */
        fun locationOutOfBounds(message: String): OperationError =
            OperationError(ErrorCode.LOCATION_OUT_OF_BOUNDS, message)

        /**
         * Creates an error when the IDE is indexing.
         */
        fun indexingInProgress(): OperationError =
            OperationError(
                ErrorCode.INDEXING_IN_PROGRESS,
                "IDE is currently indexing. Please wait and try again."
            )

        /**
         * Creates an error for invalid input parameters.
         *
         * @param message Description of the invalid input
         */
        fun invalidInput(message: String): OperationError =
            OperationError(ErrorCode.INVALID_INPUT, message)

        /**
         * Creates an error when a refactoring operation fails.
         *
         * @param message Description of the failure
         */
        fun refactoringFailed(message: String): OperationError =
            OperationError(ErrorCode.REFACTORING_FAILED, message)

        /**
         * Creates an error when a required plugin is not available.
         *
         * @param language The language that requires the missing plugin
         */
        fun pluginNotAvailable(language: String): OperationError =
            OperationError(
                ErrorCode.PLUGIN_NOT_AVAILABLE,
                "$language plugin is not available in this IDE"
            )

        /**
         * Creates an error for an unsupported language.
         *
         * @param operation The operation being attempted
         * @param supportedLanguages List of supported languages
         */
        fun unsupportedLanguage(operation: String, supportedLanguages: List<String>): OperationError =
            OperationError(
                ErrorCode.UNSUPPORTED_LANGUAGE,
                "Unsupported language for $operation. Supported: ${supportedLanguages.joinToString(", ")}."
            )

        /**
         * Creates an internal error.
         *
         * @param message Description of the internal error
         */
        fun internalError(message: String): OperationError =
            OperationError(ErrorCode.INTERNAL_ERROR, message)
    }
}

/**
 * Generic result type for operations that can fail.
 *
 * This sealed class provides a unified way to represent success or failure
 * across all refactoring operations. It's similar to Kotlin's Result but
 * carries structured error information.
 *
 * ## Usage
 * ```kotlin
 * fun findElement(): OperationResult<PsiElement> {
 *     return if (elementFound) {
 *         OperationResult.success(element)
 *     } else {
 *         OperationResult.failure(OperationError.elementNotFound(file, line, column))
 *     }
 * }
 *
 * when (val result = findElement()) {
 *     is OperationResult.Success -> println("Found: ${result.value}")
 *     is OperationResult.Failure -> println("Error: ${result.error.message}")
 * }
 * ```
 *
 * @param T The type of the success value
 */
sealed class OperationResult<out T> {
    /**
     * Represents a successful operation with a value.
     *
     * @property value The result value of the operation
     */
    data class Success<T>(val value: T) : OperationResult<T>()

    /**
     * Represents a failed operation with an error.
     *
     * @property error The structured error information
     */
    data class Failure(val error: OperationError) : OperationResult<Nothing>()

    companion object {
        /**
         * Creates a successful result.
         *
         * @param value The success value
         */
        fun <T> success(value: T): OperationResult<T> = Success(value)

        /**
         * Creates a failure result.
         *
         * @param error The operation error
         */
        fun failure(error: OperationError): OperationResult<Nothing> = Failure(error)
    }

    /**
     * Returns the value if successful, otherwise returns null.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    /**
     * Returns the value if successful, otherwise returns the default value.
     *
     * @param default The default value to return on failure
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Failure -> default
    }

    /**
     * Returns the value if successful, otherwise invokes the handler.
     *
     * @param onFailure Handler function that produces a fallback value
     */
    inline fun getOrElse(onFailure: (OperationError) -> @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Failure -> onFailure(error)
    }

    /**
     * Transforms the success value using the given function.
     *
     * @param transform The transformation function
     * @return A new OperationResult with the transformed value
     */
    inline fun <R> map(transform: (T) -> R): OperationResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    /**
     * Transforms the success value using a function that returns another OperationResult.
     *
     * @param transform The transformation function returning OperationResult
     * @return The result of the transformation
     */
    inline fun <R> flatMap(transform: (T) -> OperationResult<R>): OperationResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    /**
     * Executes an action if the result is successful.
     *
     * @param action The action to execute with the success value
     * @return This result unchanged
     */
    inline fun onSuccess(action: (T) -> Unit): OperationResult<T> {
        if (this is Success) action(value)
        return this
    }

    /**
     * Executes an action if the result is a failure.
     *
     * @param action The action to execute with the error
     * @return This result unchanged
     */
    inline fun onFailure(action: (OperationError) -> Unit): OperationResult<T> {
        if (this is Failure) action(error)
        return this
    }

    /**
     * Returns true if this result is a success.
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Returns true if this result is a failure.
     */
    val isFailure: Boolean get() = this is Failure
}
