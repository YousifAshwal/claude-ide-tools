package com.igorlink.claudeidetools.diagnostics

import com.igorlink.claudeidetools.model.Diagnostic
import com.igorlink.claudeidetools.model.DiagnosticSeverity
import com.igorlink.claudeidetools.model.QuickFix
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/**
 * Diagnostics provider that actively runs inspections via InspectionManager.
 *
 * This provider executes local inspections programmatically on files, regardless of whether
 * they have been previously analyzed by the IDE's background daemon. It provides
 * comprehensive analysis but is slower than [CachedHighlightsProvider].
 *
 * Note: Only LocalInspectionTool inspections are supported. GlobalInspectionTool
 * requires GlobalInspectionContext which is heavyweight and designed for project-wide
 * analysis, not per-file analysis.
 *
 * Use this provider for:
 * - Project-wide diagnostics checks
 * - Analyzing files that haven't been opened in the editor
 * - CI/CD integration where fresh analysis is required
 *
 * @see CachedHighlightsProvider For fast cached diagnostics
 */
object InspectionRunnerProvider : DiagnosticsProvider {

    private val logger = Logger.getInstance(InspectionRunnerProvider::class.java)

    override val providerName: String = "InspectionRunner"

    /**
     * Collects diagnostics from a file by running local inspections programmatically.
     *
     * Uses [InspectionEngine.runInspectionOnFile] with a proper [GlobalInspectionContext]
     * for correct inspection execution. Runs in a background thread via [ProgressManager]
     * as required by the IntelliJ Platform API.
     *
     * @param project The containing project
     * @param virtualFile The file to analyze
     * @param severityFilter Set of severity levels to include (empty = include all)
     * @return List of diagnostics from all applicable inspections
     */
    override fun collectFromFile(
        project: Project,
        virtualFile: VirtualFile,
        severityFilter: Set<DiagnosticSeverity>
    ): List<Diagnostic> {
        // Get PSI file in read action
        val psiFile = ReadAction.compute<PsiFile?, Throwable> {
            PsiManager.getInstance(project).findFile(virtualFile)
        } ?: return emptyList()

        // Verify PSI file is backed by a document (required for offset calculation)
        val hasDocument = ReadAction.compute<Boolean, Throwable> {
            PsiDocumentManager.getInstance(project).getDocument(psiFile) != null
        }
        if (!hasDocument) return emptyList()

        val inspectionManagerEx = InspectionManagerEx.getInstance(project) as InspectionManagerEx
        val profile = InspectionProfileManager.getInstance(project).currentProfile
        val tools = profile.getAllEnabledInspectionTools(project)

        // Filter to only LocalInspectionToolWrappers
        val localInspectionWrappers = tools
            .map { it.tool }
            .filterIsInstance<LocalInspectionToolWrapper>()

        if (localInspectionWrappers.isEmpty()) return emptyList()

        val diagnostics = mutableListOf<Diagnostic>()
        val filePath = virtualFile.path

        // Create inspection context for running inspections
        val context = inspectionManagerEx.createNewGlobalContext()

        try {
            // Run inspections in background thread with progress
            val allProblems = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<Pair<String, ProblemDescriptor>>, Exception>(
                {
                    ReadAction.compute<List<Pair<String, ProblemDescriptor>>, Throwable> {
                        val results = mutableListOf<Pair<String, ProblemDescriptor>>()
                        for (wrapper in localInspectionWrappers) {
                            try {
                                val problems = InspectionEngine.runInspectionOnFile(psiFile, wrapper, context)
                                for (problem in problems) {
                                    results.add(wrapper.shortName to problem)
                                }
                            } catch (e: Exception) {
                                logger.debug("Inspection '${wrapper.shortName}' failed on $filePath: ${e.message}")
                            }
                        }
                        results
                    }
                },
                "Running inspections on ${virtualFile.name}",
                true,
                project
            )

            // Convert problems to diagnostics
            for ((toolId, problem) in allProblems) {
                val diagnostic = convertProblemToDiagnostic(
                    problem = problem,
                    filePath = filePath,
                    toolId = toolId,
                    severityFilter = severityFilter
                )
                if (diagnostic != null) {
                    diagnostics.add(diagnostic)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to run inspections on $filePath", e)
        } finally {
            context.cleanup()
        }

        return diagnostics
    }

    /**
     * Converts an IntelliJ [ProblemDescriptor] to our [Diagnostic] model.
     *
     * @param problem The problem descriptor from an inspection
     * @param filePath Path to the file containing the problem
     * @param toolId The inspection tool's short name
     * @param severityFilter Severity filter to apply
     * @return A [Diagnostic] if conversion succeeds and passes filter, null otherwise
     */
    private fun convertProblemToDiagnostic(
        problem: ProblemDescriptor,
        filePath: String,
        toolId: String,
        severityFilter: Set<DiagnosticSeverity>
    ): Diagnostic? {
        val severity = DiagnosticsUtils.mapProblemHighlightType(problem.highlightType)

        // Apply severity filter
        if (severityFilter.isNotEmpty() && severity !in severityFilter) return null

        val message = problem.descriptionTemplate.trim()
        if (message.isBlank()) return null

        val psiElement = problem.psiElement ?: return null
        val containingFile = psiElement.containingFile ?: return null
        val document = PsiDocumentManager.getInstance(psiElement.project)
            .getDocument(containingFile) ?: return null

        val textRange = problem.textRangeInElement?.let { range ->
            val elementOffset = psiElement.textRange?.startOffset ?: 0
            elementOffset + range.startOffset to elementOffset + range.endOffset
        } ?: (psiElement.textRange?.startOffset ?: 0) to (psiElement.textRange?.endOffset ?: 0)

        // Extract quick fixes from problem descriptor
        val fixes = extractQuickFixes(problem)

        return DiagnosticsUtils.createDiagnostic(
            document = document,
            filePath = filePath,
            startOffset = textRange.first,
            endOffset = textRange.second,
            severity = severity,
            message = message,
            source = toolId,
            fixes = fixes
        )
    }

    /**
     * Extracts quick fix actions from a ProblemDescriptor.
     *
     * @param problem The problem descriptor containing quick fixes
     * @return List of QuickFix models
     */
    private fun extractQuickFixes(problem: ProblemDescriptor): List<QuickFix> {
        val problemFixes = problem.fixes ?: return emptyList()

        return problemFixes.mapIndexed { index, fix ->
            QuickFix(
                id = index,
                name = fix.name,
                familyName = fix.familyName,
                description = null
            )
        }
    }
}
