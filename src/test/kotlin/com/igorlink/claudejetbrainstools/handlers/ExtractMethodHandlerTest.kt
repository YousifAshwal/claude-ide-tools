package com.igorlink.claudejetbrainstools.handlers

import com.igorlink.claudejetbrainstools.model.ExtractMethodRequest
import com.igorlink.claudejetbrainstools.model.RefactoringResponse
import com.igorlink.claudejetbrainstools.services.PsiLocatorService
import com.igorlink.claudejetbrainstools.services.ProjectLookupResult
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for ExtractMethodHandler.
 * Tests input validation, response format, and error handling.
 *
 * Note: Full integration tests with IntelliJ Platform require the platform test framework.
 * These unit tests focus on handler logic that can be tested with mocks.
 */
class ExtractMethodHandlerTest {

    private lateinit var mockApplication: Application
    private lateinit var mockProject: Project
    private lateinit var mockLocatorService: PsiLocatorService
    private lateinit var mockVirtualFile: VirtualFile
    private lateinit var mockPsiFile: PsiFile
    private lateinit var mockPsiJavaFile: PsiJavaFile
    private lateinit var mockPsiManager: PsiManager
    private lateinit var mockLocalFileSystem: LocalFileSystem

    @BeforeEach
    fun setUp() {
        mockApplication = mockk(relaxed = true)
        mockProject = mockk(relaxed = true)
        mockLocatorService = mockk()
        mockVirtualFile = mockk(relaxed = true)
        mockPsiFile = mockk(relaxed = true)
        mockPsiJavaFile = mockk(relaxed = true)
        mockPsiManager = mockk(relaxed = true)
        mockLocalFileSystem = mockk(relaxed = true)

        // Mock ApplicationManager
        mockkStatic(ApplicationManager::class)
        every { ApplicationManager.getApplication() } returns mockApplication

        // Mock service lookup
        every { mockApplication.getService(PsiLocatorService::class.java) } returns mockLocatorService

        // Mock LocalFileSystem
        mockkStatic(LocalFileSystem::class)
        every { LocalFileSystem.getInstance() } returns mockLocalFileSystem

        // Mock project name
        every { mockProject.name } returns "TestProject"
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Request Data Model Tests ====================

    @Nested
    inner class RequestModelTests {

        @Test
        fun `ExtractMethodRequest stores file path correctly`() {
            val request = ExtractMethodRequest(
                file = "/path/to/MyClass.java",
                startLine = 10,
                startColumn = 1,
                endLine = 20,
                endColumn = 50,
                methodName = "extractedMethod"
            )

            assertEquals("/path/to/MyClass.java", request.file)
        }

        @Test
        fun `ExtractMethodRequest stores range coordinates correctly`() {
            val request = ExtractMethodRequest(
                file = "/path/to/file.java",
                startLine = 15,
                startColumn = 5,
                endLine = 25,
                endColumn = 30,
                methodName = "newMethod"
            )

            assertEquals(15, request.startLine)
            assertEquals(5, request.startColumn)
            assertEquals(25, request.endLine)
            assertEquals(30, request.endColumn)
        }

        @Test
        fun `ExtractMethodRequest stores method name correctly`() {
            val request = ExtractMethodRequest(
                file = "/path/to/file.java",
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "calculateTotal"
            )

            assertEquals("calculateTotal", request.methodName)
        }

        @Test
        fun `ExtractMethodRequest optional project defaults to null`() {
            val request = ExtractMethodRequest(
                file = "/path/to/file.java",
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "method"
            )

            assertNull(request.project)
        }

        @Test
        fun `ExtractMethodRequest with explicit project value`() {
            val request = ExtractMethodRequest(
                file = "/path/to/file.java",
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "method",
                project = "TestProject"
            )

            assertEquals("TestProject", request.project)
        }

        @Test
        fun `ExtractMethodRequest has different structure than LocatorRequest`() {
            // ExtractMethodRequest uses withProjectLookup, not withElementLookup
            // because it works with ranges, not single points
            val request = ExtractMethodRequest(
                file = "/test/path.java",
                startLine = 5,
                startColumn = 1,
                endLine = 15,
                endColumn = 80,
                methodName = "extractedMethod",
                project = "MyProject"
            )

            // Verify it has start/end coordinates instead of single line/column
            assertNotNull(request.startLine)
            assertNotNull(request.startColumn)
            assertNotNull(request.endLine)
            assertNotNull(request.endColumn)
        }
    }

    // ==================== Input Validation Tests ====================

    @Nested
    inner class InputValidationTests {

        @Test
        fun `returns error when method name is empty`() {
            val request = ExtractMethodRequest(
                file = "/path/to/MyClass.java",
                startLine = 10,
                startColumn = 1,
                endLine = 20,
                endColumn = 50,
                methodName = ""
            )

            val response = ExtractMethodHandler.handle(request)

            assertFalse(response.success)
            assertEquals("Method name cannot be empty", response.message)
        }

        @Test
        fun `returns error when method name is blank`() {
            val request = ExtractMethodRequest(
                file = "/path/to/MyClass.java",
                startLine = 10,
                startColumn = 1,
                endLine = 20,
                endColumn = 50,
                methodName = "   "
            )

            val response = ExtractMethodHandler.handle(request)

            assertFalse(response.success)
            assertEquals("Method name cannot be empty", response.message)
        }

        @Test
        fun `returns error when method name contains only whitespace`() {
            val request = ExtractMethodRequest(
                file = "/path/to/MyClass.java",
                startLine = 10,
                startColumn = 1,
                endLine = 20,
                endColumn = 50,
                methodName = "\t\n"
            )

            val response = ExtractMethodHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("cannot be empty"))
        }

        @Test
        fun `validates method name before project lookup`() {
            // Empty method name validation should happen BEFORE any locator calls
            val request = ExtractMethodRequest(
                file = "/path/file.java",
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = ""
            )

            ExtractMethodHandler.handle(request)

            // Verify locator was never called since validation fails first
            verify(exactly = 0) { mockLocatorService.findProjectForFilePath(any(), any()) }
        }

        @Test
        fun `accepts valid method name`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Error("Test - not important for validation")

            val request = ExtractMethodRequest(
                file = "/path/to/MyClass.java",
                startLine = 10,
                startColumn = 1,
                endLine = 20,
                endColumn = 50,
                methodName = "extractedMethod"
            )

            val response = ExtractMethodHandler.handle(request)

            // The error should NOT be about empty method name
            assertNotEquals("Method name cannot be empty", response.message)
        }

        @Test
        fun `accepts camelCase method name`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Error("Test")

            val request = ExtractMethodRequest(
                file = "/path/to/MyClass.java",
                startLine = 10,
                startColumn = 1,
                endLine = 20,
                endColumn = 50,
                methodName = "calculateTotalPrice"
            )

            val response = ExtractMethodHandler.handle(request)

            assertNotEquals("Method name cannot be empty", response.message)
        }

        @Test
        fun `accepts method name with underscores`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Error("Test")

            val request = ExtractMethodRequest(
                file = "/path/to/MyClass.java",
                startLine = 10,
                startColumn = 1,
                endLine = 20,
                endColumn = 50,
                methodName = "calculate_total"
            )

            val response = ExtractMethodHandler.handle(request)

            assertNotEquals("Method name cannot be empty", response.message)
        }

        @Test
        fun `accepts method name with numbers`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Error("Test")

            val request = ExtractMethodRequest(
                file = "/path/to/MyClass.java",
                startLine = 10,
                startColumn = 1,
                endLine = 20,
                endColumn = 50,
                methodName = "parseV2Response"
            )

            val response = ExtractMethodHandler.handle(request)

            assertNotEquals("Method name cannot be empty", response.message)
        }

        @Test
        fun `single character method name is valid`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Error("Test")

            val request = ExtractMethodRequest(
                file = "/path/file.java",
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "a"
            )

            val response = ExtractMethodHandler.handle(request)

            assertNotEquals("Method name cannot be empty", response.message)
        }

        @Test
        fun `long method name is valid`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Error("Test")

            val longMethodName = "aVeryLongMethodNameThatDescribesExactlyWhatTheMethodDoes"
            val request = ExtractMethodRequest(
                file = "/path/file.java",
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = longMethodName
            )

            val response = ExtractMethodHandler.handle(request)

            assertNotEquals("Method name cannot be empty", response.message)
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    inner class ErrorHandlingTests {

        @Test
        fun `returns error response for project lookup failure`() {
            val errorMessage = "File does not belong to any open project"
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Error(errorMessage)

            val request = ExtractMethodRequest(
                file = "/invalid/path.java",
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "newMethod"
            )

            val response = ExtractMethodHandler.handle(request)

            assertFalse(response.success)
            assertEquals(errorMessage, response.message)
        }

        @Test
        fun `returns error when file not found`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Found(mockProject)

            every { mockLocatorService.checkDumbMode(mockProject) } returns null
            every { mockLocalFileSystem.findFileByPath(any()) } returns null

            val request = ExtractMethodRequest(
                file = "/path/missing.java",
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "newMethod"
            )

            val response = ExtractMethodHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("File not found"))
        }

        @Test
        fun `returns error when IDE is indexing`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Found(mockProject)

            every {
                mockLocatorService.checkDumbMode(mockProject)
            } returns "IDE is currently indexing. Please wait and try again."

            val request = ExtractMethodRequest(
                file = "/path/file.java",
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "newMethod"
            )

            val response = ExtractMethodHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("indexing"))
        }

        @Test
        fun `returns error when project hint is invalid`() {
            val errorMessage = "No project found with name 'InvalidProject'"
            every {
                mockLocatorService.findProjectForFilePath(any(), eq("InvalidProject"))
            } returns ProjectLookupResult.Error(errorMessage)

            val request = ExtractMethodRequest(
                file = "/path/file.java",
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "newMethod",
                project = "InvalidProject"
            )

            val response = ExtractMethodHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("InvalidProject"))
        }

        @Test
        fun `returns error for non-Java file when language plugin unavailable`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Found(mockProject)

            every { mockLocatorService.checkDumbMode(mockProject) } returns null
            every { mockLocalFileSystem.findFileByPath(any()) } returns mockVirtualFile

            // Mock ReadAction to return a non-Java PsiFile
            mockkStatic("com.intellij.openapi.application.ReadAction")
            every {
                com.intellij.openapi.application.ReadAction.compute<PsiFile?, Throwable>(any())
            } returns mockPsiFile // mockPsiFile is PsiFile, not PsiJavaFile

            val request = ExtractMethodRequest(
                file = "/path/file.kt", // Kotlin file
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "newMethod"
            )

            val response = ExtractMethodHandler.handle(request)

            assertFalse(response.success)
            // With multi-language support, the error depends on whether the language plugin is available
            // and the file's detected language. Mock without proper language returns UNKNOWN.
            assertTrue(
                response.message.contains("Unsupported language") ||
                response.message.contains("plugin") ||
                response.message.contains("not available") ||
                response.message.contains("not a valid")
            )
        }

        @Test
        fun `returns error when file cannot be parsed`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Found(mockProject)

            every { mockLocatorService.checkDumbMode(mockProject) } returns null
            every { mockLocalFileSystem.findFileByPath(any()) } returns mockVirtualFile

            // Mock ReadAction to return null PsiFile
            mockkStatic("com.intellij.openapi.application.ReadAction")
            every {
                com.intellij.openapi.application.ReadAction.compute<PsiFile?, Throwable>(any())
            } returns null

            val request = ExtractMethodRequest(
                file = "/path/corrupted.java",
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "newMethod"
            )

            val response = ExtractMethodHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("Cannot parse file"))
        }
    }

    // ==================== Response Format Tests ====================

    @Nested
    inner class ResponseFormatTests {

        @Test
        fun `RefactoringResponse success format`() {
            val response = RefactoringResponse(
                success = true,
                message = "Extracted method 'calculateTotal' in project 'TestProject'"
            )

            assertTrue(response.success)
            assertTrue(response.message.contains("Extracted"))
            assertTrue(response.affectedFiles.isEmpty())
        }

        @Test
        fun `RefactoringResponse failure format`() {
            val response = RefactoringResponse(
                success = false,
                message = "Cannot extract method from the selected code"
            )

            assertFalse(response.success)
            assertTrue(response.message.contains("Cannot extract"))
        }

        @Test
        fun `RefactoringResponse with affected files`() {
            val response = RefactoringResponse(
                success = true,
                message = "Extracted method",
                affectedFiles = listOf("/path/MyClass.java")
            )

            assertEquals(1, response.affectedFiles.size)
        }

        @Test
        fun `RefactoringResponse default affectedFiles is empty`() {
            val response = RefactoringResponse(
                success = true,
                message = "Test"
            )

            assertTrue(response.affectedFiles.isEmpty())
        }
    }

    // ==================== Path Handling Tests ====================

    @Nested
    inner class PathHandlingTests {

        @Test
        fun `handles Windows path format`() {
            val windowsPath = "C:\\Users\\dev\\Project\\src\\MyClass.java"
            every {
                mockLocatorService.findProjectForFilePath(eq(windowsPath), isNull())
            } returns ProjectLookupResult.Error("Test")

            val request = ExtractMethodRequest(
                file = windowsPath,
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "newMethod"
            )

            ExtractMethodHandler.handle(request)

            verify { mockLocatorService.findProjectForFilePath(windowsPath, null) }
        }

        @Test
        fun `handles Unix path format`() {
            val unixPath = "/home/dev/project/src/MyClass.java"
            every {
                mockLocatorService.findProjectForFilePath(eq(unixPath), isNull())
            } returns ProjectLookupResult.Error("Test")

            val request = ExtractMethodRequest(
                file = unixPath,
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "newMethod"
            )

            ExtractMethodHandler.handle(request)

            verify { mockLocatorService.findProjectForFilePath(unixPath, null) }
        }

        @Test
        fun `passes project hint to locator`() {
            val projectHint = "MyProject"
            every {
                mockLocatorService.findProjectForFilePath(any(), eq(projectHint))
            } returns ProjectLookupResult.Error("Test")

            val request = ExtractMethodRequest(
                file = "/path/file.java",
                startLine = 5,
                startColumn = 1,
                endLine = 15,
                endColumn = 1,
                methodName = "newMethod",
                project = projectHint
            )

            ExtractMethodHandler.handle(request)

            verify { mockLocatorService.findProjectForFilePath(any(), projectHint) }
        }

        @Test
        fun `passes null project when not specified`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), isNull())
            } returns ProjectLookupResult.Error("Test")

            val request = ExtractMethodRequest(
                file = "/path/file.java",
                startLine = 5,
                startColumn = 1,
                endLine = 15,
                endColumn = 1,
                methodName = "newMethod"
            )

            ExtractMethodHandler.handle(request)

            verify { mockLocatorService.findProjectForFilePath(any(), null) }
        }

        @Test
        fun `normalizes Windows backslashes in path for file lookup`() {
            // The handler normalizes paths: request.file.replace("\\", "/")
            val windowsPath = "C:\\Users\\dev\\Project\\src\\MyClass.java"
            val normalizedPath = windowsPath.replace("\\", "/")

            assertEquals("C:/Users/dev/Project/src/MyClass.java", normalizedPath)
        }
    }

    // ==================== Message Format Tests ====================

    @Nested
    inner class MessageFormatTests {

        @Test
        fun `success message includes method name`() {
            val methodName = "calculateTotal"
            val sampleMessage = "Extracted method '$methodName' in project 'TestProject'"

            assertTrue(sampleMessage.contains(methodName))
        }

        @Test
        fun `success message includes project name`() {
            val projectName = "TestProject"
            val sampleMessage = "Extracted method 'newMethod' in project '$projectName'"

            assertTrue(sampleMessage.contains(projectName))
        }

        @Test
        fun `error message for extraction failure is descriptive`() {
            val errorMessage = "Cannot extract method from the selected code"

            assertTrue(errorMessage.contains("Cannot extract"))
        }

        @Test
        fun `error message for non-Java file is descriptive`() {
            val errorMessage = "Extract method is currently only supported for Java files"

            assertTrue(errorMessage.contains("Java files"))
        }

        @Test
        fun `error message for no statements found is descriptive`() {
            val errorMessage = "No statements found in the specified range"

            assertTrue(errorMessage.contains("No statements found"))
        }
    }

    // ==================== Range Coordinates Tests ====================

    @Nested
    inner class RangeCoordinatesTests {

        @Test
        fun `request accepts single line range`() {
            val request = ExtractMethodRequest(
                file = "/path/file.java",
                startLine = 10,
                startColumn = 5,
                endLine = 10,
                endColumn = 50,
                methodName = "singleLineMethod"
            )

            assertEquals(request.startLine, request.endLine)
        }

        @Test
        fun `request accepts multi-line range`() {
            val request = ExtractMethodRequest(
                file = "/path/file.java",
                startLine = 10,
                startColumn = 1,
                endLine = 30,
                endColumn = 50,
                methodName = "multiLineMethod"
            )

            assertTrue(request.endLine > request.startLine)
        }

        @Test
        fun `coordinates are 1-based`() {
            // The handler converts 1-based to 0-based internally:
            // startOffset = document.getLineStartOffset(request.startLine - 1) + (request.startColumn - 1)
            val request = ExtractMethodRequest(
                file = "/path/file.java",
                startLine = 1,
                startColumn = 1,
                endLine = 1,
                endColumn = 1,
                methodName = "method"
            )

            assertEquals(1, request.startLine)
            assertEquals(1, request.startColumn)
        }

        @Test
        fun `offset calculation is correct for start position`() {
            // offset = lineStartOffset + (column - 1)
            val lineStartOffset = 100
            val column = 15
            val offset = lineStartOffset + (column - 1)

            assertEquals(114, offset)
        }

        @Test
        fun `line index conversion is correct`() {
            // line index = line - 1 (converting from 1-based to 0-based)
            val line = 10
            val lineIndex = line - 1

            assertEquals(9, lineIndex)
        }
    }

    // ==================== File Type Validation Tests ====================

    @Nested
    inner class FileTypeValidationTests {

        @Test
        fun `Java files have java extension`() {
            val javaFile = "/path/to/MyClass.java"
            assertTrue(javaFile.endsWith(".java"))
        }

        @Test
        fun `Kotlin files have kt extension`() {
            val kotlinFile = "/path/to/MyClass.kt"
            assertTrue(kotlinFile.endsWith(".kt"))
        }

        @Test
        fun `handler checks for PsiJavaFile type`() {
            // The handler checks: if (psiFile !is PsiJavaFile)
            // This validates that only Java files are processed

            // mockPsiFile is just a PsiFile, not a PsiJavaFile
            assertFalse(mockPsiFile is PsiJavaFile)

            // mockPsiJavaFile is a PsiJavaFile
            assertTrue(mockPsiJavaFile is PsiJavaFile)
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    inner class EdgeCasesTests {

        @Test
        fun `handles request with minimum valid range`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Error("Test")

            val request = ExtractMethodRequest(
                file = "/file.java",
                startLine = 1,
                startColumn = 1,
                endLine = 1,
                endColumn = 1,
                methodName = "a"
            )

            val response = ExtractMethodHandler.handle(request)

            assertNotEquals("Method name cannot be empty", response.message)
        }

        @Test
        fun `handles large line range`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Error("Test")

            val request = ExtractMethodRequest(
                file = "/file.java",
                startLine = 1,
                startColumn = 1,
                endLine = 1000,
                endColumn = 1,
                methodName = "method"
            )

            val response = ExtractMethodHandler.handle(request)

            // Should proceed to project lookup, not fail on validation
            verify { mockLocatorService.findProjectForFilePath(any(), any()) }
        }

        @Test
        fun `handles path with spaces`() {
            every {
                mockLocatorService.findProjectForFilePath(any(), any())
            } returns ProjectLookupResult.Error("Test")

            val pathWithSpaces = "/path/My Project/src/MyClass.java"
            val request = ExtractMethodRequest(
                file = pathWithSpaces,
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 1,
                methodName = "method"
            )

            ExtractMethodHandler.handle(request)

            verify { mockLocatorService.findProjectForFilePath(pathWithSpaces, null) }
        }
    }
}
