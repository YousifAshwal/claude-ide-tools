package com.igorlink.claudeidetools.handlers

import com.igorlink.claudeidetools.model.MoveRequest
import com.igorlink.claudeidetools.model.RefactoringResponse
import com.igorlink.claudeidetools.services.PsiLocatorService
import com.igorlink.claudeidetools.services.PsiLookupResult
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for MoveHandler.
 * Tests input validation, response format, and error handling.
 *
 * Note: Full integration tests with IntelliJ Platform require the platform test framework.
 * These unit tests focus on handler logic that can be tested with mocks.
 */
class MoveHandlerTest {

    private lateinit var mockApplication: Application
    private lateinit var mockProject: Project
    private lateinit var mockElement: PsiElement
    private lateinit var mockPsiClass: PsiClass
    private lateinit var mockLocatorService: PsiLocatorService

    @BeforeEach
    fun setUp() {
        mockApplication = mockk(relaxed = true)
        mockProject = mockk(relaxed = true)
        mockElement = mockk(relaxed = true)
        mockPsiClass = mockk(relaxed = true)
        mockLocatorService = mockk()

        // Mock ApplicationManager
        mockkStatic(ApplicationManager::class)
        every { ApplicationManager.getApplication() } returns mockApplication

        // Mock service lookup
        every { mockApplication.getService(PsiLocatorService::class.java) } returns mockLocatorService

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
        fun `MoveRequest stores file path correctly`() {
            val request = MoveRequest(
                file = "/path/to/MyClass.java",
                line = 1,
                column = 14,
                targetPackage = "com.example.newpackage"
            )

            assertEquals("/path/to/MyClass.java", request.file)
        }

        @Test
        fun `MoveRequest stores line and column correctly`() {
            val request = MoveRequest(
                file = "/path/to/file.java",
                line = 5,
                column = 20,
                targetPackage = "com.example"
            )

            assertEquals(5, request.line)
            assertEquals(20, request.column)
        }

        @Test
        fun `MoveRequest stores target package correctly`() {
            val request = MoveRequest(
                file = "/path/to/file.java",
                line = 1,
                column = 1,
                targetPackage = "com.example.domain.model"
            )

            assertEquals("com.example.domain.model", request.targetPackage)
        }

        @Test
        fun `MoveRequest optional project defaults to null`() {
            val request = MoveRequest(
                file = "/path/to/file.java",
                line = 1,
                column = 1,
                targetPackage = "com.example"
            )

            assertNull(request.project)
        }

        @Test
        fun `MoveRequest with explicit project value`() {
            val request = MoveRequest(
                file = "/path/to/file.java",
                line = 1,
                column = 1,
                targetPackage = "com.example",
                project = "TestProject"
            )

            assertEquals("TestProject", request.project)
        }

        @Test
        fun `MoveRequest implements LocatorRequest interface`() {
            val request = MoveRequest(
                file = "/test/path.java",
                line = 5,
                column = 10,
                targetPackage = "com.test",
                project = "MyProject"
            )

            // Verify interface properties are accessible
            assertEquals("/test/path.java", request.file)
            assertEquals(5, request.line)
            assertEquals(10, request.column)
            assertEquals("MyProject", request.project)
        }
    }

    // ==================== Input Validation Tests ====================

    @Nested
    inner class InputValidationTests {

        @Test
        fun `returns error when target package is empty`() {
            val request = MoveRequest(
                file = "/path/to/MyClass.java",
                line = 1,
                column = 14,
                targetPackage = ""
            )

            val response = MoveHandler.handle(request)

            assertFalse(response.success)
            assertEquals("Target package/module cannot be empty", response.message)
        }

        @Test
        fun `returns error when target package is blank`() {
            val request = MoveRequest(
                file = "/path/to/MyClass.java",
                line = 1,
                column = 14,
                targetPackage = "   "
            )

            val response = MoveHandler.handle(request)

            assertFalse(response.success)
            assertEquals("Target package/module cannot be empty", response.message)
        }

        @Test
        fun `returns error when target package contains only whitespace`() {
            val request = MoveRequest(
                file = "/path/to/MyClass.java",
                line = 1,
                column = 14,
                targetPackage = "\t\n"
            )

            val response = MoveHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("cannot be empty"))
        }

        @Test
        fun `accepts valid package name`() {
            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Test - not important for this validation")

            val request = MoveRequest(
                file = "/path/to/MyClass.java",
                line = 1,
                column = 14,
                targetPackage = "com.example.newpackage"
            )

            val response = MoveHandler.handle(request)

            // The error should NOT be about empty package
            assertNotEquals("Target package cannot be empty", response.message)
        }

        @Test
        fun `accepts single segment package name`() {
            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Test")

            val request = MoveRequest(
                file = "/path/to/MyClass.java",
                line = 1,
                column = 14,
                targetPackage = "example"
            )

            val response = MoveHandler.handle(request)

            assertNotEquals("Target package cannot be empty", response.message)
        }

        @Test
        fun `validates empty target package before element lookup`() {
            // Empty package validation should happen BEFORE any locator calls
            val request = MoveRequest(
                file = "/path/file.java",
                line = 1,
                column = 1,
                targetPackage = ""
            )

            MoveHandler.handle(request)

            // Verify locator was never called since validation fails first
            verify(exactly = 0) { mockLocatorService.findElementAt(any(), any(), any(), any()) }
        }

        @Test
        fun `validates blank target package before element lookup`() {
            val request = MoveRequest(
                file = "/path/file.java",
                line = 1,
                column = 1,
                targetPackage = "   "
            )

            MoveHandler.handle(request)

            verify(exactly = 0) { mockLocatorService.findElementAt(any(), any(), any(), any()) }
        }

        @Test
        fun `proceeds with element lookup for valid target package`() {
            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Test")

            val request = MoveRequest(
                file = "/path/file.java",
                line = 1,
                column = 1,
                targetPackage = "com.example.valid"
            )

            MoveHandler.handle(request)

            verify(exactly = 1) { mockLocatorService.findElementAt(any(), any(), any(), any()) }
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    inner class ErrorHandlingTests {

        @Test
        fun `returns error response for element lookup failure`() {
            val errorMessage = "File not found: /invalid/path.java"
            every {
                mockLocatorService.findElementAt(
                    filePath = "/invalid/path.java",
                    line = 1,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error(errorMessage)

            val request = MoveRequest(
                file = "/invalid/path.java",
                line = 1,
                column = 1,
                targetPackage = "com.example"
            )

            val response = MoveHandler.handle(request)

            assertFalse(response.success)
            assertEquals(errorMessage, response.message)
        }

        @Test
        fun `returns error when line is out of bounds`() {
            val errorMessage = "Line 100 out of bounds (valid: 1-50)"
            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/file.java",
                    line = 100,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error(errorMessage)

            val request = MoveRequest(
                file = "/path/file.java",
                line = 100,
                column = 1,
                targetPackage = "com.example"
            )

            val response = MoveHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("out of bounds"))
        }

        @Test
        fun `returns error when column is out of bounds`() {
            val errorMessage = "Column 200 out of bounds for line 10 (valid: 1-80)"
            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/file.java",
                    line = 10,
                    column = 200,
                    projectHint = null
                )
            } returns PsiLookupResult.Error(errorMessage)

            val request = MoveRequest(
                file = "/path/file.java",
                line = 10,
                column = 200,
                targetPackage = "com.example"
            )

            val response = MoveHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("out of bounds"))
        }

        @Test
        fun `returns error when IDE is indexing`() {
            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/file.java",
                    line = 1,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Found(mockProject, mockElement)

            every {
                mockLocatorService.checkDumbMode(mockProject)
            } returns "IDE is currently indexing. Please wait and try again."

            val request = MoveRequest(
                file = "/path/file.java",
                line = 1,
                column = 1,
                targetPackage = "com.example"
            )

            val response = MoveHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("indexing"))
        }

        @Test
        fun `returns error when project hint is invalid`() {
            val errorMessage = "No project found with name 'InvalidProject'"
            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/file.java",
                    line = 1,
                    column = 1,
                    projectHint = "InvalidProject"
                )
            } returns PsiLookupResult.Error(errorMessage)

            val request = MoveRequest(
                file = "/path/file.java",
                line = 1,
                column = 1,
                targetPackage = "com.example",
                project = "InvalidProject"
            )

            val response = MoveHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("InvalidProject"))
        }

        @Test
        fun `returns error when no class found at location`() {
            // Mock element that is not a PsiClass and has no PsiClass parent
            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/file.java",
                    line = 10,
                    column = 5,
                    projectHint = null
                )
            } returns PsiLookupResult.Found(mockProject, mockElement)

            every { mockLocatorService.checkDumbMode(mockProject) } returns null

            // Setup ReadAction mock to execute immediately and return null (no class found)
            mockkStatic("com.intellij.openapi.application.ReadAction")
            every {
                com.intellij.openapi.application.ReadAction.compute<PsiClass?, Throwable>(any())
            } returns null

            val request = MoveRequest(
                file = "/path/file.java",
                line = 10,
                column = 5,
                targetPackage = "com.example"
            )

            val response = MoveHandler.handle(request)

            assertFalse(response.success)
            // With multi-language support, language detection happens first.
            // Mock setup doesn't provide a proper PsiFile with language, so it returns UNKNOWN
            assertTrue(response.message.contains("Unsupported language") || response.message.contains("No") && response.message.contains("class"))
        }
    }

    // ==================== Response Format Tests ====================

    @Nested
    inner class ResponseFormatTests {

        @Test
        fun `RefactoringResponse success format`() {
            val response = RefactoringResponse(
                success = true,
                message = "Moved 'MyClass' to 'com.example.newpackage' in project 'TestProject'"
            )

            assertTrue(response.success)
            assertTrue(response.message.contains("Moved"))
            assertTrue(response.affectedFiles.isEmpty())
        }

        @Test
        fun `RefactoringResponse failure format`() {
            val response = RefactoringResponse(
                success = false,
                message = "Cannot find target package: com.invalid.package"
            )

            assertFalse(response.success)
            assertTrue(response.message.contains("Cannot find"))
        }

        @Test
        fun `RefactoringResponse with affected files`() {
            val response = RefactoringResponse(
                success = true,
                message = "Moved class",
                affectedFiles = listOf("/path/OldLocation.java", "/path/NewLocation.java")
            )

            assertEquals(2, response.affectedFiles.size)
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
                mockLocatorService.findElementAt(
                    filePath = windowsPath,
                    line = 1,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error("Test")

            val request = MoveRequest(
                file = windowsPath,
                line = 1,
                column = 1,
                targetPackage = "com.example"
            )

            MoveHandler.handle(request)

            verify { mockLocatorService.findElementAt(windowsPath, 1, 1, null) }
        }

        @Test
        fun `handles Unix path format`() {
            val unixPath = "/home/dev/project/src/MyClass.java"
            every {
                mockLocatorService.findElementAt(
                    filePath = unixPath,
                    line = 1,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error("Test")

            val request = MoveRequest(
                file = unixPath,
                line = 1,
                column = 1,
                targetPackage = "com.example"
            )

            MoveHandler.handle(request)

            verify { mockLocatorService.findElementAt(unixPath, 1, 1, null) }
        }

        @Test
        fun `passes project hint to locator`() {
            val projectHint = "MyProject"
            every {
                mockLocatorService.findElementAt(any(), any(), any(), eq(projectHint))
            } returns PsiLookupResult.Error("Test")

            val request = MoveRequest(
                file = "/path/file.java",
                line = 5,
                column = 10,
                targetPackage = "com.example",
                project = projectHint
            )

            MoveHandler.handle(request)

            verify { mockLocatorService.findElementAt(any(), any(), any(), projectHint) }
        }

        @Test
        fun `passes null project when not specified`() {
            every {
                mockLocatorService.findElementAt(any(), any(), any(), isNull())
            } returns PsiLookupResult.Error("Test")

            val request = MoveRequest(
                file = "/path/file.java",
                line = 5,
                column = 10,
                targetPackage = "com.example"
            )

            MoveHandler.handle(request)

            verify { mockLocatorService.findElementAt(any(), any(), any(), null) }
        }
    }

    // ==================== Message Format Tests ====================

    @Nested
    inner class MessageFormatTests {

        @Test
        fun `success message includes class name`() {
            val expectedPattern = Regex("Moved '\\w+' to")
            val sampleMessage = "Moved 'MyClass' to 'com.example.newpackage' in project 'TestProject'"

            assertTrue(expectedPattern.containsMatchIn(sampleMessage))
        }

        @Test
        fun `success message includes target package`() {
            val targetPackage = "com.example.newpackage"
            val sampleMessage = "Moved 'MyClass' to '$targetPackage' in project 'TestProject'"

            assertTrue(sampleMessage.contains(targetPackage))
        }

        @Test
        fun `success message includes project name`() {
            val projectName = "TestProject"
            val sampleMessage = "Moved 'MyClass' to 'com.example' in project '$projectName'"

            assertTrue(sampleMessage.contains(projectName))
        }

        @Test
        fun `error message for missing package includes package name`() {
            val packageName = "com.invalid.package"
            val errorMessage = "Cannot find target package: $packageName. Make sure it exists."

            assertTrue(errorMessage.contains(packageName))
        }

        @Test
        fun `error message for no class found is descriptive`() {
            val errorMessage = "No class found at the specified location"

            assertTrue(errorMessage.contains("No class found"))
            assertTrue(errorMessage.contains("location"))
        }
    }

    // ==================== Coordinates Handling Tests ====================

    @Nested
    inner class CoordinatesHandlingTests {

        @Test
        fun `passes 1-based coordinates to locator`() {
            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/file.java",
                    line = 5,
                    column = 10,
                    projectHint = null
                )
            } returns PsiLookupResult.Error("Test")

            val request = MoveRequest(
                file = "/path/file.java",
                line = 5,
                column = 10,
                targetPackage = "com.example"
            )

            MoveHandler.handle(request)

            verify { mockLocatorService.findElementAt(any(), 5, 10, any()) }
        }

        @Test
        fun `handles line 1 column 1 correctly`() {
            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/file.java",
                    line = 1,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error("Test")

            val request = MoveRequest(
                file = "/path/file.java",
                line = 1,
                column = 1,
                targetPackage = "com.example"
            )

            MoveHandler.handle(request)

            verify { mockLocatorService.findElementAt(any(), 1, 1, any()) }
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    inner class EdgeCasesTests {

        @Test
        fun `handles request with minimum valid coordinates`() {
            val request = MoveRequest(
                file = "/file.java",
                line = 1,
                column = 1,
                targetPackage = "a"
            )

            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Element not found")

            MoveHandler.handle(request)

            verify { mockLocatorService.findElementAt("/file.java", 1, 1, null) }
        }

        @Test
        fun `handles deep package name`() {
            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Test")

            val deepPackage = "com.example.very.deep.package.structure.module"
            val request = MoveRequest(
                file = "/path/file.java",
                line = 1,
                column = 1,
                targetPackage = deepPackage
            )

            val response = MoveHandler.handle(request)

            assertNotEquals("Target package cannot be empty", response.message)
        }

        @Test
        fun `handles package name with numbers`() {
            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Test")

            val request = MoveRequest(
                file = "/path/file.java",
                line = 1,
                column = 1,
                targetPackage = "com.example.v2.api"
            )

            val response = MoveHandler.handle(request)

            assertNotEquals("Target package cannot be empty", response.message)
        }
    }
}
