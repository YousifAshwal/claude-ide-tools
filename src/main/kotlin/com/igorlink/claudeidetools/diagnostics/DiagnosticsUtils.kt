package com.igorlink.claudeidetools.diagnostics

import com.igorlink.claudeidetools.model.Diagnostic
import com.igorlink.claudeidetools.model.DiagnosticSeverity
import com.igorlink.claudeidetools.model.QuickFix
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document

/**
 * Shared utilities for diagnostics processing.
 *
 * Extracts common logic from DiagnosticsHandler and providers to avoid duplication (DRY).
 * All functions are pure and stateless.
 */
object DiagnosticsUtils {

    /**
     * Maps IntelliJ's [HighlightSeverity] to our [DiagnosticSeverity].
     *
     * @param severity The IntelliJ severity
     * @return The mapped diagnostic severity, or null if not mappable
     */
    fun mapHighlightSeverity(severity: HighlightSeverity): DiagnosticSeverity? {
        return when {
            severity >= HighlightSeverity.ERROR -> DiagnosticSeverity.ERROR
            severity >= HighlightSeverity.WARNING -> DiagnosticSeverity.WARNING
            severity >= HighlightSeverity.WEAK_WARNING -> DiagnosticSeverity.WEAK_WARNING
            severity >= HighlightSeverity.INFORMATION -> DiagnosticSeverity.INFO
            severity >= HighlightSeverity.TEXT_ATTRIBUTES -> DiagnosticSeverity.HINT
            else -> null
        }
    }

    /**
     * Maps IntelliJ's [ProblemHighlightType] to our [DiagnosticSeverity].
     * Used by InspectionRunnerProvider when processing ProblemDescriptors.
     *
     * @param type The problem highlight type
     * @return The mapped diagnostic severity
     */
    fun mapProblemHighlightType(type: ProblemHighlightType): DiagnosticSeverity {
        return when (type) {
            ProblemHighlightType.ERROR,
            ProblemHighlightType.GENERIC_ERROR -> DiagnosticSeverity.ERROR

            ProblemHighlightType.WARNING,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING -> DiagnosticSeverity.WARNING

            ProblemHighlightType.WEAK_WARNING -> DiagnosticSeverity.WEAK_WARNING

            ProblemHighlightType.INFORMATION,
            ProblemHighlightType.POSSIBLE_PROBLEM -> DiagnosticSeverity.INFO

            else -> DiagnosticSeverity.HINT
        }
    }

    /**
     * Detects the source of a highlight (e.g., "Java", "ESLint", "TypeScript").
     *
     * @param info The highlight info
     * @return Source identifier or null if unknown
     */
    fun detectSource(info: HighlightInfo): String? {
        // Try to extract from inspection tool ID first
        val toolId = info.inspectionToolId
        if (!toolId.isNullOrBlank()) {
            return toolId
        }

        // Fallback: try to infer from highlight type
        val typeName = info.type.toString()
            .replace("HIGHLIGHTINFO_", "")
            .replace("_", " ")
            .lowercase()
            .replaceFirstChar { it.uppercase() }

        return typeName.takeIf { it.isNotBlank() && it != "Unknown" }
    }

    /**
     * Parses severity filter strings into a set of [DiagnosticSeverity].
     *
     * @param severities List of severity strings (case-insensitive)
     * @return Set of parsed severities (empty set means include all)
     */
    fun parseSeverityFilter(severities: List<String>?): Set<DiagnosticSeverity> {
        if (severities.isNullOrEmpty()) return emptySet()
        return severities.mapNotNull { DiagnosticSeverity.fromString(it) }.toSet()
    }

    /**
     * Returns the sort order for a severity string (lower = more severe).
     */
    fun severityOrder(severity: String): Int = when (severity) {
        "ERROR" -> 0
        "WARNING" -> 1
        "WEAK_WARNING" -> 2
        "INFO" -> 3
        "HINT" -> 4
        else -> 5
    }

    /**
     * Builds a human-readable result message.
     */
    fun buildResultMessage(
        scopeDescription: String,
        returnedCount: Int,
        totalCount: Int,
        truncated: Boolean
    ): String = when {
        totalCount == 0 -> "No diagnostics found in $scopeDescription"
        truncated -> "Found $totalCount diagnostic(s) in $scopeDescription (showing first $returnedCount)"
        else -> "Found $totalCount diagnostic(s) in $scopeDescription"
    }

    /**
     * Creates a [Diagnostic] from document position information.
     *
     * Safely calculates line/column positions from offsets, handling edge cases
     * like offsets beyond document length.
     *
     * @param document The document containing the diagnostic
     * @param filePath Absolute path to the file
     * @param startOffset Start offset in the document
     * @param endOffset End offset in the document
     * @param severity The diagnostic severity
     * @param message The diagnostic message
     * @param source Optional source identifier (e.g., inspection tool ID)
     * @param fixes List of available quick fixes for this diagnostic
     * @return A fully constructed [Diagnostic] object
     */
    fun createDiagnostic(
        document: Document,
        filePath: String,
        startOffset: Int,
        endOffset: Int,
        severity: DiagnosticSeverity,
        message: String,
        source: String?,
        fixes: List<QuickFix> = emptyList()
    ): Diagnostic {
        // Safely calculate line/column positions
        val safeStartOffset = startOffset.coerceIn(0, document.textLength)
        val safeEndOffset = endOffset.coerceIn(safeStartOffset, document.textLength)

        val startLine = document.getLineNumber(safeStartOffset) + 1
        val startLineOffset = document.getLineStartOffset(startLine - 1)
        val startColumn = safeStartOffset - startLineOffset + 1

        val endLine = document.getLineNumber(safeEndOffset) + 1
        val endLineOffset = document.getLineStartOffset(endLine - 1)
        val endColumn = safeEndOffset - endLineOffset + 1

        return Diagnostic(
            file = filePath,
            line = startLine,
            column = startColumn,
            endLine = endLine,
            endColumn = endColumn,
            severity = severity.name,
            message = message,
            source = source,
            fixes = fixes
        )
    }
}
