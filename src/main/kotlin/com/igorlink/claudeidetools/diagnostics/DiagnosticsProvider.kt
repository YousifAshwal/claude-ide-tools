package com.igorlink.claudeidetools.diagnostics

import com.igorlink.claudeidetools.model.Diagnostic
import com.igorlink.claudeidetools.model.DiagnosticSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Contract for diagnostic collection strategies.
 *
 * Implementations provide different ways to collect diagnostics:
 * - [CachedHighlightsProvider]: Uses DaemonCodeAnalyzer's cached highlights (fast, but only for analyzed files)
 * - [InspectionRunnerProvider]: Runs inspections via GlobalInspectionContext (slower, but comprehensive)
 *
 * Follows ISP: focused interface with single method for collection.
 * Follows DIP: handlers depend on this abstraction, not concrete implementations.
 */
interface DiagnosticsProvider {

    /**
     * Collects diagnostics from a single file.
     *
     * Implementations are responsible for their own threading model:
     * - [CachedHighlightsProvider] wraps PSI access in [ReadAction]
     * - [InspectionRunnerProvider] uses [ProgressManager] for background execution
     *
     * Callers should NOT wrap calls in ReadAction - each provider manages its own threading.
     *
     * @param project The target project
     * @param virtualFile The file to analyze
     * @param severityFilter Set of severity levels to include (empty = include all)
     * @return List of diagnostics found in the file, sorted by position
     */
    fun collectFromFile(
        project: Project,
        virtualFile: VirtualFile,
        severityFilter: Set<DiagnosticSeverity>
    ): List<Diagnostic>

    /**
     * Human-readable name for logging and debugging purposes.
     */
    val providerName: String
}
