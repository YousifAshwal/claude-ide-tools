package com.igorlink.claudeidetools.util

/**
 * Centralized timeout configuration for refactoring operations.
 *
 * These timeout values (in seconds) are used by [RefactoringExecutor] to limit
 * how long refactoring operations can take before being considered failed.
 *
 * ## Timeout Values
 * - **DEFAULT (30s)**: Used for most operations (rename, find usages, extract method)
 * - **MOVE (60s)**: Longer timeout for move operations which may need to update many files
 *
 * ## Why Timeouts Are Needed
 * Refactoring operations run on the EDT and could potentially hang due to:
 * - Large codebases with many files to update
 * - Complex reference resolution
 * - Deadlock conditions in edge cases
 *
 * Timeouts ensure the HTTP server doesn't block indefinitely and can
 * return an error response to the client.
 */
object RefactoringTimeouts {
    /** Default timeout for most refactoring operations (30 seconds). */
    const val DEFAULT = 30L

    /** Extended timeout for move operations (60 seconds). */
    const val MOVE = 60L
}
