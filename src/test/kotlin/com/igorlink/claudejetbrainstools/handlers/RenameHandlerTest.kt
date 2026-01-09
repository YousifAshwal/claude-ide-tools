package com.igorlink.claudejetbrainstools.handlers

import com.igorlink.claudejetbrainstools.model.RefactoringResponse
import com.igorlink.claudejetbrainstools.model.RenameRequest
import com.igorlink.claudejetbrainstools.services.PsiLocatorService
import com.igorlink.claudejetbrainstools.services.PsiLookupResult
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for RenameHandler.
 *
 * These tests focus on:
 * 1. Input validation (empty name)
 * 2. Successful response format
 * 3. Error handling (element not found, non-renamable element)
 *
 * Note: Full integration tests with actual PSI manipulation require
 * the IntelliJ Platform test framework.
 */
class RenameHandlerTest {

    private lateinit var mockApplication: Application
    private lateinit var mockLocatorService: PsiLocatorService
    private lateinit var mockProject: Project

    @BeforeEach
    fun setUp() {
        mockApplication = mockk(relaxed = true)
        mockLocatorService = mockk()
        mockProject = mockk(relaxed = true)

        // Mock ApplicationManager.getApplication() to return our mock
        mockkStatic(ApplicationManager::class)
        every { ApplicationManager.getApplication() } returns mockApplication

        // Mock the service lookup
        every { mockApplication.getService(PsiLocatorService::class.java) } returns mockLocatorService
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Input Validation Tests ====================

    @Nested
    inner class InputValidationTest {

        @Test
        fun `returns error when newName is empty`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = ""
            )

            val response = RenameHandler.handle(request)

            assertFalse(response.success)
            assertEquals("New name cannot be empty", response.message)
        }

        @Test
        fun `returns error when newName is blank (only spaces)`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = "   "
            )

            val response = RenameHandler.handle(request)

            assertFalse(response.success)
            assertEquals("New name cannot be empty", response.message)
        }

        @Test
        fun `returns error when newName is blank (tabs and spaces)`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = "\t  \t"
            )

            val response = RenameHandler.handle(request)

            assertFalse(response.success)
            assertEquals("New name cannot be empty", response.message)
        }

        @Test
        fun `returns error when newName contains only newlines`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = "\n\n"
            )

            val response = RenameHandler.handle(request)

            assertFalse(response.success)
            assertEquals("New name cannot be empty", response.message)
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    inner class ErrorHandlingTest {

        @Test
        fun `returns error when element is not found`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = "newMethodName"
            )

            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/to/file.kt",
                    line = 10,
                    column = 5,
                    projectHint = null
                )
            } returns PsiLookupResult.Error("No code element found at /path/to/file.kt:10:5")

            val response = RenameHandler.handle(request)

            assertFalse(response.success)
            assertEquals("No code element found at /path/to/file.kt:10:5", response.message)
        }

        @Test
        fun `returns error when file is not found`() {
            val request = RenameRequest(
                file = "/nonexistent/file.kt",
                line = 1,
                column = 1,
                newName = "newName"
            )

            every {
                mockLocatorService.findElementAt(
                    filePath = "/nonexistent/file.kt",
                    line = 1,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error("File not found: /nonexistent/file.kt")

            val response = RenameHandler.handle(request)

            assertFalse(response.success)
            assertEquals("File not found: /nonexistent/file.kt", response.message)
        }

        @Test
        fun `returns error when line is out of bounds`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 999,
                column = 1,
                newName = "newName"
            )

            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/to/file.kt",
                    line = 999,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error("Line 999 out of bounds (valid: 1-50)")

            val response = RenameHandler.handle(request)

            assertFalse(response.success)
            assertEquals("Line 999 out of bounds (valid: 1-50)", response.message)
        }

        @Test
        fun `returns error when column is out of bounds`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 200,
                newName = "newName"
            )

            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/to/file.kt",
                    line = 10,
                    column = 200,
                    projectHint = null
                )
            } returns PsiLookupResult.Error("Column 200 out of bounds for line 10 (valid: 1-80)")

            val response = RenameHandler.handle(request)

            assertFalse(response.success)
            assertEquals("Column 200 out of bounds for line 10 (valid: 1-80)", response.message)
        }

        @Test
        fun `returns error when element is not a named element`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = "newName"
            )

            // Create a mock PsiElement that is NOT a PsiNamedElement
            val mockElement = mockk<PsiElement>()

            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/to/file.kt",
                    line = 10,
                    column = 5,
                    projectHint = null
                )
            } returns PsiLookupResult.Found(mockProject, mockElement)

            every { mockLocatorService.checkDumbMode(mockProject) } returns null

            val response = RenameHandler.handle(request)

            assertFalse(response.success)
            assertEquals("Element at location is not renamable (not a named element)", response.message)
        }

        @Test
        fun `returns error when IDE is in dumb mode (indexing)`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = "newName"
            )

            val mockElement = mockk<PsiNamedElement>()

            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/to/file.kt",
                    line = 10,
                    column = 5,
                    projectHint = null
                )
            } returns PsiLookupResult.Found(mockProject, mockElement)

            every { mockLocatorService.checkDumbMode(mockProject) } returns "IDE is currently indexing. Please wait and try again."

            val response = RenameHandler.handle(request)

            assertFalse(response.success)
            assertEquals("IDE is currently indexing. Please wait and try again.", response.message)
        }

        @Test
        fun `returns error with project hint when specified`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = "newName",
                project = "MyProject"
            )

            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/to/file.kt",
                    line = 10,
                    column = 5,
                    projectHint = "MyProject"
                )
            } returns PsiLookupResult.Error("File '/path/to/file.kt' is not part of project 'MyProject'. Please specify correct project name.")

            val response = RenameHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("MyProject"))
        }
    }

    // ==================== Response Format Tests ====================

    @Nested
    inner class ResponseFormatTest {

        @Test
        fun `error response has success false`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = ""
            )

            val response = RenameHandler.handle(request)

            assertFalse(response.success)
        }

        @Test
        fun `error response has non-empty message`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = ""
            )

            val response = RenameHandler.handle(request)

            assertTrue(response.message.isNotEmpty())
        }

        @Test
        fun `error response is of type RefactoringResponse`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = ""
            )

            val response: RefactoringResponse = RenameHandler.handle(request)

            assertNotNull(response)
        }

        @Test
        fun `error response has empty affectedFiles list by default`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = ""
            )

            val response = RenameHandler.handle(request)

            assertTrue(response.affectedFiles.isEmpty())
        }
    }

    // ==================== Request Processing Tests ====================

    @Nested
    inner class RequestProcessingTest {

        @Test
        fun `processes request with valid newName containing leading spaces`() {
            // Names with leading spaces are technically valid from validation perspective,
            // but would likely fail during actual rename
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = " validName" // has leading space but not blank
            )

            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Some error")

            RenameHandler.handle(request)

            // Validation passes, but lookup fails
            verify { mockLocatorService.findElementAt("/path/to/file.kt", 10, 5, null) }
        }

        @Test
        fun `passes project parameter to locator service`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = "newName",
                project = "TestProject"
            )

            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Error")

            RenameHandler.handle(request)

            verify { mockLocatorService.findElementAt("/path/to/file.kt", 10, 5, "TestProject") }
        }

        @Test
        fun `passes null project when not specified`() {
            val request = RenameRequest(
                file = "/path/to/file.kt",
                line = 10,
                column = 5,
                newName = "newName"
            )

            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Error")

            RenameHandler.handle(request)

            verify { mockLocatorService.findElementAt("/path/to/file.kt", 10, 5, null) }
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    inner class EdgeCasesTest {

        @Test
        fun `handles request with minimum valid coordinates`() {
            val request = RenameRequest(
                file = "/file.kt",
                line = 1,
                column = 1,
                newName = "a"
            )

            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Element not found")

            RenameHandler.handle(request)

            verify { mockLocatorService.findElementAt("/file.kt", 1, 1, null) }
        }

        @Test
        fun `handles request with unicode newName`() {
            val request = RenameRequest(
                file = "/file.kt",
                line = 1,
                column = 1,
                newName = "имяМетода"
            )

            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Element not found")

            RenameHandler.handle(request)

            // Validation passes for unicode names
            verify { mockLocatorService.findElementAt(any(), any(), any(), any()) }
        }

        @Test
        fun `handles request with special characters in newName`() {
            val request = RenameRequest(
                file = "/file.kt",
                line = 1,
                column = 1,
                newName = "method_with_123"
            )

            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Element not found")

            RenameHandler.handle(request)

            // Validation passes
            verify { mockLocatorService.findElementAt(any(), any(), any(), any()) }
        }

        @Test
        fun `handles Windows file path`() {
            val request = RenameRequest(
                file = "C:\\Users\\Developer\\Project\\src\\Main.kt",
                line = 10,
                column = 5,
                newName = "newName"
            )

            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("File not found")

            RenameHandler.handle(request)

            verify { mockLocatorService.findElementAt("C:\\Users\\Developer\\Project\\src\\Main.kt", 10, 5, null) }
        }
    }
}
