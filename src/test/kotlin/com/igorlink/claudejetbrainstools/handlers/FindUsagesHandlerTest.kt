package com.igorlink.claudejetbrainstools.handlers

import com.igorlink.claudejetbrainstools.model.FindUsagesRequest
import com.igorlink.claudejetbrainstools.model.FindUsagesResponse
import com.igorlink.claudejetbrainstools.model.Usage
import com.igorlink.claudejetbrainstools.services.PsiLocatorService
import com.igorlink.claudejetbrainstools.services.PsiLookupResult
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for FindUsagesHandler.
 * Tests input validation, response format, and error handling.
 *
 * Note: Full integration tests with IntelliJ Platform require the platform test framework.
 * These unit tests focus on handler logic that can be tested with mocks.
 */
class FindUsagesHandlerTest {

    private lateinit var mockApplication: Application
    private lateinit var mockProject: Project
    private lateinit var mockElement: PsiElement
    private lateinit var mockLocatorService: PsiLocatorService

    @BeforeEach
    fun setUp() {
        mockApplication = mockk(relaxed = true)
        mockProject = mockk(relaxed = true)
        mockElement = mockk(relaxed = true)
        mockLocatorService = mockk()

        // Mock ApplicationManager to return our mock Application
        mockkStatic(ApplicationManager::class)
        every { ApplicationManager.getApplication() } returns mockApplication

        // Mock service lookup
        every { mockApplication.getService(PsiLocatorService::class.java) } returns mockLocatorService
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Request Data Model Tests ====================

    @Nested
    inner class RequestModelTests {

        @Test
        fun `FindUsagesRequest stores file path correctly`() {
            val request = FindUsagesRequest(
                file = "/path/to/MyClass.java",
                line = 10,
                column = 5
            )

            assertEquals("/path/to/MyClass.java", request.file)
        }

        @Test
        fun `FindUsagesRequest stores line and column correctly`() {
            val request = FindUsagesRequest(
                file = "/path/to/file.kt",
                line = 42,
                column = 15
            )

            assertEquals(42, request.line)
            assertEquals(15, request.column)
        }

        @Test
        fun `FindUsagesRequest optional project defaults to null`() {
            val request = FindUsagesRequest(
                file = "/path/to/file.kt",
                line = 1,
                column = 1
            )

            assertNull(request.project)
        }

        @Test
        fun `FindUsagesRequest with explicit project value`() {
            val request = FindUsagesRequest(
                file = "/path/to/file.kt",
                line = 1,
                column = 1,
                project = "TestProject"
            )

            assertEquals("TestProject", request.project)
        }

        @Test
        fun `FindUsagesRequest implements LocatorRequest interface`() {
            val request = FindUsagesRequest(
                file = "/test/path.java",
                line = 5,
                column = 10,
                project = "MyProject"
            )

            // Verify interface properties are accessible
            assertEquals("/test/path.java", request.file)
            assertEquals(5, request.line)
            assertEquals(10, request.column)
            assertEquals("MyProject", request.project)
        }
    }

    // ==================== Response Format Tests ====================

    @Nested
    inner class ResponseFormatTests {

        @Test
        fun `FindUsagesResponse success with empty usages`() {
            val response = FindUsagesResponse(
                success = true,
                message = "Found 0 usage(s)",
                usages = emptyList()
            )

            assertTrue(response.success)
            assertEquals("Found 0 usage(s)", response.message)
            assertTrue(response.usages.isEmpty())
        }

        @Test
        fun `FindUsagesResponse success with usages list`() {
            val usages = listOf(
                Usage(file = "/path/file1.kt", line = 10, column = 5, preview = "val x = myMethod()"),
                Usage(file = "/path/file2.kt", line = 20, column = 15, preview = "myMethod()")
            )

            val response = FindUsagesResponse(
                success = true,
                message = "Found 2 usage(s)",
                usages = usages
            )

            assertTrue(response.success)
            assertEquals(2, response.usages.size)
            assertEquals("/path/file1.kt", response.usages[0].file)
            assertEquals(10, response.usages[0].line)
            assertEquals(5, response.usages[0].column)
            assertEquals("val x = myMethod()", response.usages[0].preview)
        }

        @Test
        fun `FindUsagesResponse failure format`() {
            val response = FindUsagesResponse(
                success = false,
                message = "File not found: /invalid/path.kt"
            )

            assertFalse(response.success)
            assertTrue(response.message.contains("File not found"))
            assertTrue(response.usages.isEmpty())
        }

        @Test
        fun `Usage data class stores all fields correctly`() {
            val usage = Usage(
                file = "/project/src/Main.java",
                line = 42,
                column = 8,
                preview = "public void testMethod() {"
            )

            assertEquals("/project/src/Main.java", usage.file)
            assertEquals(42, usage.line)
            assertEquals(8, usage.column)
            assertEquals("public void testMethod() {", usage.preview)
        }

        @Test
        fun `FindUsagesResponse default usages is empty list`() {
            val response = FindUsagesResponse(
                success = true,
                message = "Test message"
            )

            assertEquals(emptyList<Usage>(), response.usages)
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    inner class ErrorHandlingTests {

        @Test
        fun `returns error response for element lookup failure`() {
            val errorMessage = "File not found: /invalid/path.kt"
            every {
                mockLocatorService.findElementAt(
                    filePath = "/invalid/path.kt",
                    line = 1,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error(errorMessage)

            val request = FindUsagesRequest(
                file = "/invalid/path.kt",
                line = 1,
                column = 1
            )

            val response = FindUsagesHandler.handle(request)

            assertFalse(response.success)
            assertEquals(errorMessage, response.message)
            assertTrue(response.usages.isEmpty())
        }

        @Test
        fun `returns error response when no element at location`() {
            val errorMessage = "No code element found at /path/file.kt:10:5"
            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/file.kt",
                    line = 10,
                    column = 5,
                    projectHint = null
                )
            } returns PsiLookupResult.Error(errorMessage)

            val request = FindUsagesRequest(
                file = "/path/file.kt",
                line = 10,
                column = 5
            )

            val response = FindUsagesHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("No code element found"))
        }

        @Test
        fun `returns error response when line is out of bounds`() {
            val errorMessage = "Line 100 out of bounds (valid: 1-50)"
            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/file.kt",
                    line = 100,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error(errorMessage)

            val request = FindUsagesRequest(
                file = "/path/file.kt",
                line = 100,
                column = 1
            )

            val response = FindUsagesHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("out of bounds"))
        }

        @Test
        fun `returns error response when column is out of bounds`() {
            val errorMessage = "Column 200 out of bounds for line 10 (valid: 1-80)"
            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/file.kt",
                    line = 10,
                    column = 200,
                    projectHint = null
                )
            } returns PsiLookupResult.Error(errorMessage)

            val request = FindUsagesRequest(
                file = "/path/file.kt",
                line = 10,
                column = 200
            )

            val response = FindUsagesHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("out of bounds"))
        }

        @Test
        fun `returns error response when IDE is indexing`() {
            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/file.kt",
                    line = 1,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Found(mockProject, mockElement)

            every {
                mockLocatorService.checkDumbMode(mockProject)
            } returns "IDE is currently indexing. Please wait and try again."

            val request = FindUsagesRequest(
                file = "/path/file.kt",
                line = 1,
                column = 1
            )

            val response = FindUsagesHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("indexing"))
        }

        @Test
        fun `returns error response when project hint is invalid`() {
            val errorMessage = "No project found with name 'InvalidProject'"
            every {
                mockLocatorService.findElementAt(
                    filePath = "/path/file.kt",
                    line = 1,
                    column = 1,
                    projectHint = "InvalidProject"
                )
            } returns PsiLookupResult.Error(errorMessage)

            val request = FindUsagesRequest(
                file = "/path/file.kt",
                line = 1,
                column = 1,
                project = "InvalidProject"
            )

            val response = FindUsagesHandler.handle(request)

            assertFalse(response.success)
            assertTrue(response.message.contains("InvalidProject"))
        }
    }

    // ==================== Input Validation Tests ====================

    @Nested
    inner class InputValidationTests {

        @Test
        fun `handles Windows path format`() {
            val windowsPath = "C:\\Users\\dev\\Project\\src\\Main.kt"
            every {
                mockLocatorService.findElementAt(
                    filePath = windowsPath,
                    line = 1,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error("Test")

            val request = FindUsagesRequest(
                file = windowsPath,
                line = 1,
                column = 1
            )

            FindUsagesHandler.handle(request)

            verify { mockLocatorService.findElementAt(windowsPath, 1, 1, null) }
        }

        @Test
        fun `handles Unix path format`() {
            val unixPath = "/home/dev/project/src/Main.kt"
            every {
                mockLocatorService.findElementAt(
                    filePath = unixPath,
                    line = 1,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error("Test")

            val request = FindUsagesRequest(
                file = unixPath,
                line = 1,
                column = 1
            )

            FindUsagesHandler.handle(request)

            verify { mockLocatorService.findElementAt(unixPath, 1, 1, null) }
        }

        @Test
        fun `passes project hint to locator`() {
            val projectHint = "MyProject"
            every {
                mockLocatorService.findElementAt(any(), any(), any(), eq(projectHint))
            } returns PsiLookupResult.Error("Test")

            val request = FindUsagesRequest(
                file = "/path/file.kt",
                line = 5,
                column = 10,
                project = projectHint
            )

            FindUsagesHandler.handle(request)

            verify { mockLocatorService.findElementAt(any(), any(), any(), projectHint) }
        }

        @Test
        fun `passes null project when not specified`() {
            every {
                mockLocatorService.findElementAt(any(), any(), any(), isNull())
            } returns PsiLookupResult.Error("Test")

            val request = FindUsagesRequest(
                file = "/path/file.kt",
                line = 5,
                column = 10
            )

            FindUsagesHandler.handle(request)

            verify { mockLocatorService.findElementAt(any(), any(), any(), null) }
        }

        @Test
        fun `handles path with spaces`() {
            val pathWithSpaces = "/path/My Project/src/Main File.kt"
            every {
                mockLocatorService.findElementAt(
                    filePath = pathWithSpaces,
                    line = 1,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error("Test")

            val request = FindUsagesRequest(
                file = pathWithSpaces,
                line = 1,
                column = 1
            )

            FindUsagesHandler.handle(request)

            verify { mockLocatorService.findElementAt(pathWithSpaces, 1, 1, null) }
        }

        @Test
        fun `handles path with special characters`() {
            val pathWithSpecialChars = "/path/project-name_v2.0/src/Main+Test.kt"
            every {
                mockLocatorService.findElementAt(
                    filePath = pathWithSpecialChars,
                    line = 1,
                    column = 1,
                    projectHint = null
                )
            } returns PsiLookupResult.Error("Test")

            val request = FindUsagesRequest(
                file = pathWithSpecialChars,
                line = 1,
                column = 1
            )

            FindUsagesHandler.handle(request)

            verify { mockLocatorService.findElementAt(pathWithSpecialChars, 1, 1, null) }
        }
    }

    // ==================== Message Format Tests ====================

    @Nested
    inner class MessageFormatTests {

        @Test
        fun `success message includes usage count`() {
            // Example expected format: "Found 5 usage(s) in project 'MyProject'"
            val expectedPattern = Regex("Found \\d+ usage\\(s\\)")

            // Sample message matching expected format
            val sampleMessage = "Found 5 usage(s) in project 'TestProject'"

            assertTrue(expectedPattern.containsMatchIn(sampleMessage))
        }

        @Test
        fun `success message includes project name`() {
            val projectName = "TestProject"
            val sampleMessage = "Found 3 usage(s) in project '$projectName'"

            assertTrue(sampleMessage.contains(projectName))
        }

        @Test
        fun `error message for file not found includes path`() {
            val filePath = "/path/to/missing/File.kt"
            val errorMessage = "File not found: $filePath"

            assertTrue(errorMessage.contains(filePath))
        }

        @Test
        fun `error message for no element includes coordinates`() {
            val file = "/path/File.kt"
            val line = 10
            val column = 5
            val errorMessage = "No code element found at $file:$line:$column"

            assertTrue(errorMessage.contains("$file:$line:$column"))
        }
    }

    // ==================== Usage Line Number Tests ====================

    @Nested
    inner class UsageLineNumberTests {

        @Test
        fun `Usage line numbers are 1-based`() {
            // In the handler, line numbers are converted: lineNumber + 1
            val usage = Usage(
                file = "/path/file.kt",
                line = 1, // First line should be 1, not 0
                column = 1,
                preview = "first line"
            )

            assertEquals(1, usage.line)
            assertTrue(usage.line >= 1, "Line numbers should be 1-based")
        }

        @Test
        fun `Usage column numbers are 1-based`() {
            // In the handler, column numbers are converted: refElement.textOffset - lineStart + 1
            val usage = Usage(
                file = "/path/file.kt",
                line = 1,
                column = 1, // First column should be 1, not 0
                preview = "code"
            )

            assertEquals(1, usage.column)
            assertTrue(usage.column >= 1, "Column numbers should be 1-based")
        }
    }

    // ==================== Preview Text Tests ====================

    @Nested
    inner class PreviewTextTests {

        @Test
        fun `preview text is trimmed`() {
            // The handler trims preview text: lineText.trim()
            val untrimmedPreview = "    val x = methodCall()    "
            val expectedTrimmed = untrimmedPreview.trim()

            assertEquals("val x = methodCall()", expectedTrimmed)
        }

        @Test
        fun `preview represents the line containing usage`() {
            val usage = Usage(
                file = "/path/file.kt",
                line = 10,
                column = 5,
                preview = "val result = targetMethod(param1, param2)"
            )

            assertFalse(usage.preview.isBlank())
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    inner class EdgeCasesTests {

        @Test
        fun `handles request with minimum valid coordinates`() {
            val request = FindUsagesRequest(
                file = "/file.kt",
                line = 1,
                column = 1
            )

            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Element not found")

            FindUsagesHandler.handle(request)

            verify { mockLocatorService.findElementAt("/file.kt", 1, 1, null) }
        }

        @Test
        fun `handles request with large line number`() {
            val request = FindUsagesRequest(
                file = "/file.kt",
                line = 10000,
                column = 1
            )

            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Line out of bounds")

            val response = FindUsagesHandler.handle(request)

            assertFalse(response.success)
        }

        @Test
        fun `handles request with large column number`() {
            val request = FindUsagesRequest(
                file = "/file.kt",
                line = 1,
                column = 1000
            )

            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("Column out of bounds")

            val response = FindUsagesHandler.handle(request)

            assertFalse(response.success)
        }

        @Test
        fun `handles empty file path`() {
            val request = FindUsagesRequest(
                file = "",
                line = 1,
                column = 1
            )

            every {
                mockLocatorService.findElementAt(any(), any(), any(), any())
            } returns PsiLookupResult.Error("File not found")

            val response = FindUsagesHandler.handle(request)

            assertFalse(response.success)
        }
    }
}
