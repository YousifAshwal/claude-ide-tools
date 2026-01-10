package com.igorlink.claudeidetools.handlers

import com.igorlink.claudeidetools.model.ApplyFixRequest
import com.igorlink.claudeidetools.model.ApplyFixResponse
import com.igorlink.claudeidetools.util.HandlerUtils
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.util.concurrent.atomic.AtomicReference

/**
 * Handler for the `/applyFix` endpoint to apply quick fix actions.
 *
 * This handler locates a diagnostic at the specified position, finds the
 * requested quick fix by index, and applies it to resolve the issue.
 *
 * ## How it works
 * 1. Locates the file and finds highlights at the specified position
 * 2. Matches the diagnostic by message (if provided) or position
 * 3. Retrieves the quick fix action by index
 * 4. Applies the fix using WriteAction and CommandProcessor
 *
 * ## Analysis Modes
 * - **Cached (default)**: Uses IDE's cached highlights from DaemonCodeAnalyzer.
 *   Only works for files currently open in the editor with active analysis.
 * - **Inspections**: Set `runInspections=true` to run inspections programmatically.
 *   Works for any file, even if not open in the editor.
 *
 * ## Threading
 * Quick fix application requires:
 * - ReadAction for locating elements
 * - WriteAction for applying changes
 * - EDT for some intention actions
 */
object ApplyFixHandler {
    private val logger = Logger.getInstance(ApplyFixHandler::class.java)

    /**
     * Handles the apply fix request.
     *
     * Routes to either cached highlights mode or inspection-based mode
     * depending on the `runInspections` flag in the request.
     *
     * @param request The request containing file location, fix ID, and optional message
     * @return [ApplyFixResponse] indicating success or failure
     */
    fun handle(request: ApplyFixRequest): ApplyFixResponse {
        return HandlerUtils.withProjectLookup(
            file = request.file,
            projectHint = request.project,
            errorResponseFactory = { message -> ApplyFixResponse(false, message) }
        ) { project ->
            if (request.runInspections) {
                applyFixViaInspections(project, request)
            } else {
                applyFixViaCachedHighlights(project, request)
            }
        }
    }

    /**
     * Applies a quick fix using cached highlights from DaemonCodeAnalyzer.
     *
     * This mode only works for files that are currently open in the editor
     * and have been analyzed by the IDE's background daemon.
     */
    private fun applyFixViaCachedHighlights(project: Project, request: ApplyFixRequest): ApplyFixResponse {
        val virtualFile = LocalFileSystem.getInstance()
            .findFileByPath(request.file.replace("\\", "/"))
            ?: return ApplyFixResponse(
                success = false,
                message = "File not found: ${request.file}"
            )

        // Get PSI file and document
        val psiFile = ReadAction.compute<PsiFile?, Throwable> {
            PsiManager.getInstance(project).findFile(virtualFile)
        } ?: return ApplyFixResponse(
            success = false,
            message = "Cannot parse file: ${request.file}"
        )

        val document = ReadAction.compute<com.intellij.openapi.editor.Document?, Throwable> {
            PsiDocumentManager.getInstance(project).getDocument(psiFile)
        } ?: return ApplyFixResponse(
            success = false,
            message = "Cannot get document for file: ${request.file}"
        )

        // Calculate offset from line/column
        val offset = ReadAction.compute<Int, Throwable> {
            if (request.line < 1 || request.line > document.lineCount) {
                return@compute -1
            }
            val lineStartOffset = document.getLineStartOffset(request.line - 1)
            lineStartOffset + request.column - 1
        }

        if (offset < 0 || offset > document.textLength) {
            return ApplyFixResponse(
                success = false,
                message = "Invalid position: line ${request.line}, column ${request.column}"
            )
        }

        // Find highlight at position
        val highlights = ReadAction.compute<List<HighlightInfo>, Throwable> {
            DaemonCodeAnalyzerImpl.getHighlights(document, null, project)
                .filter { it.startOffset <= offset && offset <= it.endOffset }
        }

        if (highlights.isEmpty()) {
            return ApplyFixResponse(
                success = false,
                message = "No diagnostic found at line ${request.line}, column ${request.column}"
            )
        }

        // Find the matching highlight (by message if provided, otherwise first one)
        val targetHighlight = if (request.diagnosticMessage != null) {
            highlights.find { it.description?.trim() == request.diagnosticMessage.trim() }
                ?: return ApplyFixResponse(
                    success = false,
                    message = "No diagnostic matching message '${request.diagnosticMessage}' found at position"
                )
        } else {
            highlights.first()
        }

        // Get quick fixes from the highlight
        val quickFixRanges = targetHighlight.quickFixActionRanges
        if (quickFixRanges.isNullOrEmpty()) {
            return ApplyFixResponse(
                success = false,
                message = "No quick fixes available for this diagnostic"
            )
        }

        if (request.fixId < 0 || request.fixId >= quickFixRanges.size) {
            return ApplyFixResponse(
                success = false,
                message = "Invalid fix ID: ${request.fixId}. Available fixes: 0-${quickFixRanges.size - 1}"
            )
        }

        val intentionAction = quickFixRanges[request.fixId].first?.action
            ?: return ApplyFixResponse(
                success = false,
                message = "Fix action not available"
            )

        // Get or create editor for the file
        val editor = getOrOpenEditor(project, virtualFile, psiFile)
            ?: return ApplyFixResponse(
                success = false,
                message = "Cannot open editor for file"
            )

        // Apply the fix
        val resultRef = AtomicReference<ApplyFixResponse>()

        ApplicationManager.getApplication().invokeAndWait {
            try {
                // Check if the action is available
                val isAvailable = ReadAction.compute<Boolean, Throwable> {
                    intentionAction.isAvailable(project, editor, psiFile)
                }

                if (!isAvailable) {
                    resultRef.set(ApplyFixResponse(
                        success = false,
                        message = "Fix '${intentionAction.text}' is not available at this location"
                    ))
                    return@invokeAndWait
                }

                // Apply the fix within a command
                CommandProcessor.getInstance().executeCommand(
                    project,
                    {
                        WriteAction.run<Throwable> {
                            intentionAction.invoke(project, editor, psiFile)
                        }
                    },
                    "Apply Quick Fix: ${intentionAction.text}",
                    null
                )

                // Commit document changes and save to disk
                PsiDocumentManager.getInstance(project).commitDocument(document)
                FileDocumentManager.getInstance().saveDocument(document)

                resultRef.set(ApplyFixResponse(
                    success = true,
                    message = "Applied fix '${intentionAction.text}'",
                    fixName = intentionAction.text,
                    affectedFiles = listOf(request.file)
                ))
            } catch (e: Exception) {
                logger.error("Failed to apply fix", e)
                resultRef.set(ApplyFixResponse(
                    success = false,
                    message = "Failed to apply fix: ${e.message}"
                ))
            }
        }

        return resultRef.get()
    }

    /**
     * Applies a quick fix by running inspections programmatically.
     *
     * This mode works for any file, even if not open in the editor.
     * It runs all enabled inspections to find the problem at the specified location,
     * then applies the requested fix.
     */
    private fun applyFixViaInspections(project: Project, request: ApplyFixRequest): ApplyFixResponse {
        val virtualFile = LocalFileSystem.getInstance()
            .findFileByPath(request.file.replace("\\", "/"))
            ?: return ApplyFixResponse(
                success = false,
                message = "File not found: ${request.file}"
            )

        // Get PSI file and document
        val psiFile = ReadAction.compute<PsiFile?, Throwable> {
            PsiManager.getInstance(project).findFile(virtualFile)
        } ?: return ApplyFixResponse(
            success = false,
            message = "Cannot parse file: ${request.file}"
        )

        val document = ReadAction.compute<com.intellij.openapi.editor.Document?, Throwable> {
            PsiDocumentManager.getInstance(project).getDocument(psiFile)
        } ?: return ApplyFixResponse(
            success = false,
            message = "Cannot get document for file: ${request.file}"
        )

        // Calculate offset from line/column
        val offset = ReadAction.compute<Int, Throwable> {
            if (request.line < 1 || request.line > document.lineCount) {
                return@compute -1
            }
            val lineStartOffset = document.getLineStartOffset(request.line - 1)
            lineStartOffset + request.column - 1
        }

        if (offset < 0 || offset > document.textLength) {
            return ApplyFixResponse(
                success = false,
                message = "Invalid position: line ${request.line}, column ${request.column}"
            )
        }

        // Run inspections and find problems at the specified location
        val inspectionManagerEx = InspectionManagerEx.getInstance(project) as InspectionManagerEx
        val profile = InspectionProfileManager.getInstance(project).currentProfile
        val tools = profile.getAllEnabledInspectionTools(project)

        val localInspectionWrappers = tools
            .map { it.tool }
            .filterIsInstance<LocalInspectionToolWrapper>()

        if (localInspectionWrappers.isEmpty()) {
            return ApplyFixResponse(
                success = false,
                message = "No inspections available"
            )
        }

        val context = inspectionManagerEx.createNewGlobalContext()

        try {
            // Run inspections and collect problems at the target offset
            val problemsAtOffset = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<ProblemDescriptor>, Exception>(
                {
                    ReadAction.compute<List<ProblemDescriptor>, Throwable> {
                        val matchingProblems = mutableListOf<ProblemDescriptor>()
                        for (wrapper in localInspectionWrappers) {
                            try {
                                val problems = InspectionEngine.runInspectionOnFile(psiFile, wrapper, context)
                                for (problem in problems) {
                                    val psiElement = problem.psiElement ?: continue
                                    val elementRange = psiElement.textRange ?: continue

                                    // Check if this problem covers the target offset
                                    if (elementRange.startOffset <= offset && offset <= elementRange.endOffset) {
                                        // If diagnosticMessage is provided, also match by message
                                        if (request.diagnosticMessage != null) {
                                            if (problem.descriptionTemplate.trim() == request.diagnosticMessage.trim()) {
                                                matchingProblems.add(problem)
                                            }
                                        } else {
                                            matchingProblems.add(problem)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logger.debug("Inspection '${wrapper.shortName}' failed: ${e.message}")
                            }
                        }
                        matchingProblems
                    }
                },
                "Running inspections on ${virtualFile.name}",
                true,
                project
            )

            if (problemsAtOffset.isEmpty()) {
                return ApplyFixResponse(
                    success = false,
                    message = "No diagnostic found at line ${request.line}, column ${request.column}"
                )
            }

            // Use the first matching problem
            val targetProblem = problemsAtOffset.first()
            val fixes = targetProblem.fixes

            if (fixes.isNullOrEmpty()) {
                return ApplyFixResponse(
                    success = false,
                    message = "No quick fixes available for this diagnostic"
                )
            }

            if (request.fixId < 0 || request.fixId >= fixes.size) {
                return ApplyFixResponse(
                    success = false,
                    message = "Invalid fix ID: ${request.fixId}. Available fixes: 0-${fixes.size - 1}"
                )
            }

            val fix = fixes[request.fixId]
            val fixName = fix.name

            // Apply the fix
            val resultRef = AtomicReference<ApplyFixResponse>()

            ApplicationManager.getApplication().invokeAndWait {
                try {
                    CommandProcessor.getInstance().executeCommand(
                        project,
                        {
                            WriteAction.run<Throwable> {
                                fix.applyFix(project, targetProblem)
                            }
                        },
                        "Apply Quick Fix: $fixName",
                        null
                    )

                    // Commit document changes and save to disk
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    FileDocumentManager.getInstance().saveDocument(document)

                    resultRef.set(ApplyFixResponse(
                        success = true,
                        message = "Applied fix '$fixName'",
                        fixName = fixName,
                        affectedFiles = listOf(request.file)
                    ))
                } catch (e: Exception) {
                    logger.error("Failed to apply fix", e)
                    resultRef.set(ApplyFixResponse(
                        success = false,
                        message = "Failed to apply fix: ${e.message}"
                    ))
                }
            }

            return resultRef.get()
        } finally {
            context.cleanup()
        }
    }

    /**
     * Gets an existing editor or opens a new one for the file.
     */
    private fun getOrOpenEditor(
        project: Project,
        virtualFile: com.intellij.openapi.vfs.VirtualFile,
        psiFile: PsiFile
    ): Editor? {
        val editorRef = AtomicReference<Editor?>()

        ApplicationManager.getApplication().invokeAndWait {
            val fileEditorManager = FileEditorManager.getInstance(project)

            // Try to get existing editor
            val existingEditors = fileEditorManager.getEditors(virtualFile)
            val textEditor = existingEditors.filterIsInstance<TextEditor>().firstOrNull()

            if (textEditor != null) {
                editorRef.set(textEditor.editor)
            } else {
                // Open the file
                val editors = fileEditorManager.openFile(virtualFile, false)
                val newTextEditor = editors.filterIsInstance<TextEditor>().firstOrNull()
                editorRef.set(newTextEditor?.editor)
            }
        }

        return editorRef.get()
    }
}
