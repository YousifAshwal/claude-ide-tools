package com.igorlink.claudeidetools.util

import com.igorlink.claudeidetools.model.RefactoringResponse
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Executor for refactoring operations that require EDT and write access.
 *
 * This utility encapsulates the complex threading pattern required for IntelliJ refactoring:
 * - Uses [CompletableFuture] for async result handling with timeout
 * - Executes on EDT (Event Dispatch Thread) via `invokeAndWait`
 * - Wraps operations in [WriteCommandAction] for undo/redo support and PSI modification
 *
 * ## Why This Is Needed
 * IntelliJ's PSI (Program Structure Interface) requires:
 * 1. **Write access**: PSI modifications must occur within a write action
 * 2. **EDT execution**: Many refactoring APIs require the Event Dispatch Thread
 * 3. **Command wrapping**: For proper undo/redo functionality
 *
 * ## Usage Patterns
 * Two execution patterns are provided:
 *
 * ### Simple Pattern (execute)
 * For straightforward operations that return a result directly:
 * ```kotlin
 * RefactoringExecutor.execute(project, "Rename") {
 *     processor.run()
 *     RefactoringResponse(true, "Done")
 * }
 * ```
 *
 * ### Callback Pattern (executeWithCallback)
 * For operations that may succeed or fail at different points:
 * ```kotlin
 * RefactoringExecutor.executeWithCallback(project, "Move") { callback ->
 *     if (canMove) {
 *         processor.run()
 *         callback.success("Moved successfully")
 *     } else {
 *         callback.failure("Cannot move: reason")
 *     }
 * }
 * ```
 *
 * @see RefactoringTimeouts Default timeout values
 */
object RefactoringExecutor {
    private val logger = Logger.getInstance(RefactoringExecutor::class.java)

    /**
     * Executes a refactoring action within a write command on the EDT.
     *
     * This method blocks the calling thread until the refactoring completes
     * or times out. The action is executed synchronously on the EDT within
     * a write command action.
     *
     * @param project The project context for the write command
     * @param commandName The name of the command (appears in Edit menu for undo/redo)
     * @param timeoutSeconds Maximum time to wait for completion (default: 30 seconds)
     * @param action The refactoring action to execute, must return [RefactoringResponse]
     * @return [RefactoringResponse] indicating success or failure with a message
     * @throws java.util.concurrent.TimeoutException if the operation exceeds the timeout
     */
    fun execute(
        project: Project,
        commandName: String,
        timeoutSeconds: Long = RefactoringTimeouts.DEFAULT,
        action: () -> RefactoringResponse
    ): RefactoringResponse {
        val result = CompletableFuture<RefactoringResponse>()

        ApplicationManager.getApplication().invokeAndWait {
            try {
                WriteCommandAction.runWriteCommandAction(project, commandName, null, {
                    try {
                        val response = action()
                        result.complete(response)
                    } catch (e: Exception) {
                        logger.error("Refactoring action failed: $commandName", e)
                        result.complete(RefactoringResponse(false, "Refactoring failed: ${e.message}"))
                    }
                })
            } catch (e: Exception) {
                logger.error("Write command execution failed: $commandName", e)
                result.complete(RefactoringResponse(false, "Execution failed: ${e.message}"))
            }
        }

        return result.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    /**
     * Executes a refactoring action on the EDT without WriteCommandAction.
     *
     * Use this for refactoring processors (like RenameProcessor, MoveClassProcessor)
     * that manage their own write actions internally. These processors start progress
     * indicators and would deadlock if wrapped in an external WriteCommandAction.
     *
     * @param timeoutSeconds Maximum time to wait for completion (default: 30 seconds)
     * @param action The refactoring action to execute, must return [RefactoringResponse]
     * @return [RefactoringResponse] indicating success or failure with a message
     */
    fun executeOnEdt(
        timeoutSeconds: Long = RefactoringTimeouts.DEFAULT,
        action: () -> RefactoringResponse
    ): RefactoringResponse {
        val result = CompletableFuture<RefactoringResponse>()

        ApplicationManager.getApplication().invokeAndWait {
            try {
                val response = action()
                result.complete(response)
            } catch (e: Exception) {
                logger.error("Refactoring action failed on EDT", e)
                result.complete(RefactoringResponse(false, "Refactoring failed: ${e.message}"))
            }
        }

        return result.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    /**
     * Executes a refactoring action on the EDT with callback-based result reporting,
     * without WriteCommandAction.
     *
     * Use this for refactoring processors that manage their own write actions internally
     * but may need to report failure before starting the refactoring.
     *
     * @param timeoutSeconds Maximum time to wait for completion (default: 30 seconds)
     * @param action The refactoring action that receives a [ResultCallback] to report completion
     * @return [RefactoringResponse] indicating success or failure with a message
     */
    fun executeOnEdtWithCallback(
        timeoutSeconds: Long = RefactoringTimeouts.DEFAULT,
        action: (ResultCallback) -> Unit
    ): RefactoringResponse {
        val result = CompletableFuture<RefactoringResponse>()
        val callback = ResultCallback(result)

        ApplicationManager.getApplication().invokeAndWait {
            try {
                action(callback)
            } catch (e: Exception) {
                logger.error("Refactoring action failed on EDT", e)
                if (!result.isDone) {
                    result.complete(RefactoringResponse(false, "Refactoring failed: ${e.message}"))
                }
            }
        }

        return result.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    /**
     * Executes a refactoring action with callback-based result reporting.
     *
     * This variant is useful for operations where success/failure is determined
     * at different points during execution, such as when validation might fail
     * before the actual refactoring, or when using processor callbacks.
     *
     * The callback can be called at any point during execution. If the action
     * completes without calling the callback, no result is set (will timeout).
     *
     * @param project The project context for the write command
     * @param commandName The name of the command (appears in Edit menu for undo/redo)
     * @param timeoutSeconds Maximum time to wait for completion (default: 30 seconds)
     * @param action The refactoring action that receives a [ResultCallback] to report completion
     * @return [RefactoringResponse] indicating success or failure with a message
     * @throws java.util.concurrent.TimeoutException if callback is not called within timeout
     */
    fun executeWithCallback(
        project: Project,
        commandName: String,
        timeoutSeconds: Long = RefactoringTimeouts.DEFAULT,
        action: (ResultCallback) -> Unit
    ): RefactoringResponse {
        val result = CompletableFuture<RefactoringResponse>()
        val callback = ResultCallback(result)

        ApplicationManager.getApplication().invokeAndWait {
            try {
                WriteCommandAction.runWriteCommandAction(project, commandName, null, {
                    try {
                        action(callback)
                    } catch (e: Exception) {
                        logger.error("Refactoring action failed: $commandName", e)
                        if (!result.isDone) {
                            result.complete(RefactoringResponse(false, "Refactoring failed: ${e.message}"))
                        }
                    }
                })
            } catch (e: Exception) {
                logger.error("Write command execution failed: $commandName", e)
                if (!result.isDone) {
                    result.complete(RefactoringResponse(false, "Execution failed: ${e.message}"))
                }
            }
        }

        return result.get(timeoutSeconds, TimeUnit.SECONDS)
    }

    /**
     * Callback for reporting refactoring results from within [executeWithCallback].
     *
     * Provides type-safe methods for reporting success, failure, or custom responses.
     * Only the first call to any method takes effect; subsequent calls are ignored.
     *
     * @property future The underlying CompletableFuture to complete with the result
     */
    class ResultCallback(private val future: CompletableFuture<RefactoringResponse>) {

        /**
         * Reports a successful refactoring operation.
         *
         * @param message Success message to include in the response
         */
        fun success(message: String) {
            if (!future.isDone) {
                future.complete(RefactoringResponse(true, message))
            }
        }

        /**
         * Reports a failed refactoring operation.
         *
         * @param message Error message explaining why the operation failed
         */
        fun failure(message: String) {
            if (!future.isDone) {
                future.complete(RefactoringResponse(false, message))
            }
        }

        /**
         * Reports a custom refactoring response.
         *
         * Use this when you need to include additional information like affected files.
         *
         * @param response The complete [RefactoringResponse] to return
         */
        fun complete(response: RefactoringResponse) {
            if (!future.isDone) {
                future.complete(response)
            }
        }
    }
}
