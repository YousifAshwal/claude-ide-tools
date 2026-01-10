package com.igorlink.claudeidetools.diagnostics

import com.igorlink.claudeidetools.model.Diagnostic
import com.igorlink.claudeidetools.model.DiagnosticSeverity
import com.igorlink.claudeidetools.model.QuickFix
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager

/**
 * Diagnostics provider that uses DaemonCodeAnalyzer's cached highlights.
 *
 * This provider retrieves diagnostics from IntelliJ's background code analysis cache.
 * It's fast but only returns diagnostics for files that have already been analyzed
 * by the IDE's background daemon (typically files that are or were recently open in the editor).
 *
 * Use this provider for:
 * - Quick diagnostics checks on open files
 * - Real-time feedback during editing
 * - When speed is more important than completeness
 *
 * @see InspectionRunnerProvider For comprehensive analysis of all files
 */
object CachedHighlightsProvider : DiagnosticsProvider {

    override val providerName: String = "CachedHighlights"

    /**
     * Collects diagnostics from a file using cached highlights.
     *
     * Uses [DaemonCodeAnalyzerImpl.getHighlights] to retrieve highlight information
     * that was computed during background analysis. This method is fast but may
     * return empty results for files that haven't been analyzed yet.
     *
     * Wraps all PSI/document access in [ReadAction] for thread safety.
     *
     * @param project The containing project
     * @param virtualFile The file to get diagnostics from
     * @param severityFilter Set of severity levels to include (empty = include all)
     * @return List of diagnostics found in the file's cached highlights
     */
    override fun collectFromFile(
        project: Project,
        virtualFile: VirtualFile,
        severityFilter: Set<DiagnosticSeverity>
    ): List<Diagnostic> {
        return ReadAction.compute<List<Diagnostic>, Throwable> {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@compute emptyList()
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return@compute emptyList()

            val highlights = DaemonCodeAnalyzerImpl.getHighlights(document, null, project)
            if (highlights.isEmpty()) return@compute emptyList()

            val diagnostics = mutableListOf<Diagnostic>()
            val filePath = virtualFile.path

            for (info in highlights) {
                val severity = DiagnosticsUtils.mapHighlightSeverity(info.severity) ?: continue

                // Apply severity filter
                if (severityFilter.isNotEmpty() && severity !in severityFilter) continue

                // Skip empty or whitespace-only descriptions
                val message = info.description?.trim()
                if (message.isNullOrBlank()) continue

                // Extract quick fixes from the highlight
                val fixes = extractQuickFixes(info)

                val diagnostic = DiagnosticsUtils.createDiagnostic(
                    document = document,
                    filePath = filePath,
                    startOffset = info.startOffset,
                    endOffset = info.endOffset,
                    severity = severity,
                    message = message,
                    source = DiagnosticsUtils.detectSource(info),
                    fixes = fixes
                )
                diagnostics.add(diagnostic)
            }

            diagnostics
        }
    }

    /**
     * Extracts quick fix actions from a HighlightInfo.
     *
     * @param info The highlight info containing quick fixes
     * @return List of QuickFix models
     */
    private fun extractQuickFixes(info: HighlightInfo): List<QuickFix> {
        val fixes = mutableListOf<QuickFix>()
        var index = 0

        // Get quick fix action ranges from the highlight
        val quickFixActionRanges = info.quickFixActionRanges ?: return emptyList()

        for (pair in quickFixActionRanges) {
            val action = pair.first?.action ?: continue
            fixes.add(
                QuickFix(
                    id = index++,
                    name = action.text,
                    familyName = action.familyName,
                    description = null
                )
            )
        }

        return fixes
    }
}
