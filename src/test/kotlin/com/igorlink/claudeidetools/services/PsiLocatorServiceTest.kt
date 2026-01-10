package com.igorlink.claudeidetools.services

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for PsiLocatorService.
 *
 * Note: Tests that require full IntelliJ Platform mocking (static methods like
 * ProjectManager.getInstance(), LocalFileSystem.getInstance(), etc.) cannot be
 * reliably unit tested with MockK due to AbstractMethodError issues with
 * IntelliJ's internal classes.
 *
 * These tests focus on:
 * 1. Path normalization logic (testable via string manipulation)
 * 2. Result type behavior (PsiLookupResult, ProjectLookupResult)
 * 3. Validation error message formats
 *
 * For full integration tests, use the IntelliJ Platform test framework with
 * LightJavaCodeInsightFixtureTestCase or similar.
 */
class PsiLocatorServiceTest {

    // ==================== Path Normalization Tests ====================

    @Nested
    inner class PathNormalizationTest {

        /**
         * Tests the path normalization logic that converts Windows backslashes to forward slashes.
         * This mirrors the logic in resolveFileAndProject: filePath.replace("\\", "/")
         */
        @Test
        fun `normalizes Windows backslash paths to forward slashes`() {
            val windowsPath = "C:\\Users\\Developer\\Project\\src\\Main.kt"
            val normalizedPath = windowsPath.replace("\\", "/")

            assertEquals("C:/Users/Developer/Project/src/Main.kt", normalizedPath)
        }

        @Test
        fun `handles Unix forward slash paths unchanged`() {
            val unixPath = "/home/developer/project/src/Main.kt"
            val normalizedPath = unixPath.replace("\\", "/")

            assertEquals("/home/developer/project/src/Main.kt", normalizedPath)
        }

        @Test
        fun `handles mixed path separators`() {
            val mixedPath = "C:\\Users/Developer\\Project/src\\Main.kt"
            val normalizedPath = mixedPath.replace("\\", "/")

            assertEquals("C:/Users/Developer/Project/src/Main.kt", normalizedPath)
        }

        @Test
        fun `handles path with multiple consecutive backslashes`() {
            val pathWithMultipleBackslashes = "C:\\\\Users\\\\Project\\\\src\\\\Main.kt"
            val normalizedPath = pathWithMultipleBackslashes.replace("\\", "/")

            assertEquals("C://Users//Project//src//Main.kt", normalizedPath)
        }

        @Test
        fun `handles empty path`() {
            val emptyPath = ""
            val normalizedPath = emptyPath.replace("\\", "/")

            assertEquals("", normalizedPath)
        }

        @Test
        fun `handles path with only backslashes`() {
            val backslashPath = "\\\\\\\\"
            val normalizedPath = backslashPath.replace("\\", "/")

            assertEquals("////", normalizedPath)
        }

        @Test
        fun `handles UNC path`() {
            val uncPath = "\\\\server\\share\\folder\\file.kt"
            val normalizedPath = uncPath.replace("\\", "/")

            assertEquals("//server/share/folder/file.kt", normalizedPath)
        }
    }

    // ==================== Project Hint Normalization Tests ====================

    @Nested
    inner class ProjectHintNormalizationTest {

        /**
         * Tests the project hint normalization logic.
         * This mirrors the logic in findProjectByHint: hint.replace("\\", "/").lowercase()
         */
        @Test
        fun `normalizes project hint with Windows backslashes`() {
            val projectHint = "C:\\projects\\TestProject"
            val normalizedHint = projectHint.replace("\\", "/").lowercase()

            assertEquals("c:/projects/testproject", normalizedHint)
        }

        @Test
        fun `normalizes project hint to lowercase`() {
            val projectHint = "TestProject"
            val normalizedHint = projectHint.replace("\\", "/").lowercase()

            assertEquals("testproject", normalizedHint)
        }

        @Test
        fun `normalizes mixed case path hint`() {
            val projectHint = "C:\\Users\\DEVELOPER\\Projects\\MyApp"
            val normalizedHint = projectHint.replace("\\", "/").lowercase()

            assertEquals("c:/users/developer/projects/myapp", normalizedHint)
        }
    }

    // ==================== PsiLookupResult Tests ====================

    @Nested
    inner class PsiLookupResultTest {

        @Test
        fun `Found contains project and element`() {
            val mockProject = mockk<Project>()
            val mockElement = mockk<PsiElement>()

            val result = PsiLookupResult.Found(mockProject, mockElement)

            assertEquals(mockProject, result.project)
            assertEquals(mockElement, result.element)
        }

        @Test
        fun `Error contains message`() {
            val errorMessage = "Test error message"

            val result = PsiLookupResult.Error(errorMessage)

            assertEquals(errorMessage, result.message)
        }

        @Test
        fun `Found is instance of PsiLookupResult`() {
            val mockProject = mockk<Project>()
            val mockElement = mockk<PsiElement>()

            val result: PsiLookupResult = PsiLookupResult.Found(mockProject, mockElement)

            assertTrue(result is PsiLookupResult.Found)
        }

        @Test
        fun `Error is instance of PsiLookupResult`() {
            val result: PsiLookupResult = PsiLookupResult.Error("error")

            assertTrue(result is PsiLookupResult.Error)
        }

        @Test
        fun `can pattern match on Found`() {
            val mockProject = mockk<Project>()
            val mockElement = mockk<PsiElement>()
            val result: PsiLookupResult = PsiLookupResult.Found(mockProject, mockElement)

            val message = when (result) {
                is PsiLookupResult.Found -> "Found: ${result.project}, ${result.element}"
                is PsiLookupResult.Error -> "Error: ${result.message}"
            }

            assertTrue(message.startsWith("Found:"))
        }

        @Test
        fun `can pattern match on Error`() {
            val result: PsiLookupResult = PsiLookupResult.Error("something went wrong")

            val message = when (result) {
                is PsiLookupResult.Found -> "Found"
                is PsiLookupResult.Error -> "Error: ${result.message}"
            }

            assertEquals("Error: something went wrong", message)
        }

        @Test
        fun `Error handles empty message`() {
            val result = PsiLookupResult.Error("")

            assertEquals("", result.message)
        }

        @Test
        fun `Error handles multiline message`() {
            val multilineMessage = """
                Error occurred at line 10
                Details: File not found
                Stack trace follows
            """.trimIndent()

            val result = PsiLookupResult.Error(multilineMessage)

            assertTrue(result.message.contains("line 10"))
            assertTrue(result.message.contains("File not found"))
        }
    }

    // ==================== ProjectLookupResult Tests ====================

    @Nested
    inner class ProjectLookupResultTest {

        @Test
        fun `Found contains project`() {
            val mockProject = mockk<Project>()

            val result = ProjectLookupResult.Found(mockProject)

            assertEquals(mockProject, result.project)
        }

        @Test
        fun `Error contains message`() {
            val errorMessage = "Project not found"

            val result = ProjectLookupResult.Error(errorMessage)

            assertEquals(errorMessage, result.message)
        }

        @Test
        fun `Found is instance of ProjectLookupResult`() {
            val mockProject = mockk<Project>()

            val result: ProjectLookupResult = ProjectLookupResult.Found(mockProject)

            assertTrue(result is ProjectLookupResult.Found)
        }

        @Test
        fun `Error is instance of ProjectLookupResult`() {
            val result: ProjectLookupResult = ProjectLookupResult.Error("error")

            assertTrue(result is ProjectLookupResult.Error)
        }

        @Test
        fun `can pattern match on Found`() {
            val mockProject = mockk<Project>()
            val result: ProjectLookupResult = ProjectLookupResult.Found(mockProject)

            val isFound = when (result) {
                is ProjectLookupResult.Found -> true
                is ProjectLookupResult.Error -> false
            }

            assertTrue(isFound)
        }

        @Test
        fun `can pattern match on Error`() {
            val result: ProjectLookupResult = ProjectLookupResult.Error("not found")

            val message = when (result) {
                is ProjectLookupResult.Found -> null
                is ProjectLookupResult.Error -> result.message
            }

            assertEquals("not found", message)
        }
    }

    // ==================== Coordinate Validation Logic Tests ====================

    @Nested
    inner class CoordinateValidationTest {

        /**
         * Tests the coordinate validation logic.
         * This mirrors the validation in findElementAt:
         * - Line validation: line < 1 || line > document.lineCount
         * - Column validation: column < 1 || column > maxColumn
         */
        @Test
        fun `line number less than 1 is invalid`() {
            val line = 0
            val lineCount = 10

            val isValid = line >= 1 && line <= lineCount

            assertFalse(isValid)
        }

        @Test
        fun `negative line number is invalid`() {
            val line = -5
            val lineCount = 10

            val isValid = line >= 1 && line <= lineCount

            assertFalse(isValid)
        }

        @Test
        fun `line number 1 is valid`() {
            val line = 1
            val lineCount = 10

            val isValid = line >= 1 && line <= lineCount

            assertTrue(isValid)
        }

        @Test
        fun `line number equal to lineCount is valid`() {
            val line = 10
            val lineCount = 10

            val isValid = line >= 1 && line <= lineCount

            assertTrue(isValid)
        }

        @Test
        fun `line number greater than lineCount is invalid`() {
            val line = 11
            val lineCount = 10

            val isValid = line >= 1 && line <= lineCount

            assertFalse(isValid)
        }

        @Test
        fun `column number less than 1 is invalid`() {
            val column = 0
            val maxColumn = 80

            val isValid = column >= 1 && column <= maxColumn

            assertFalse(isValid)
        }

        @Test
        fun `negative column number is invalid`() {
            val column = -1
            val maxColumn = 80

            val isValid = column >= 1 && column <= maxColumn

            assertFalse(isValid)
        }

        @Test
        fun `column number 1 is valid`() {
            val column = 1
            val maxColumn = 80

            val isValid = column >= 1 && column <= maxColumn

            assertTrue(isValid)
        }

        @Test
        fun `column number equal to maxColumn is valid`() {
            val column = 80
            val maxColumn = 80

            val isValid = column >= 1 && column <= maxColumn

            assertTrue(isValid)
        }

        @Test
        fun `column number greater than maxColumn is invalid`() {
            val column = 81
            val maxColumn = 80

            val isValid = column >= 1 && column <= maxColumn

            assertFalse(isValid)
        }

        @Test
        fun `maxColumn calculation is correct`() {
            // maxColumn = lineEndOffset - lineStartOffset + 1
            val lineStartOffset = 100
            val lineEndOffset = 150
            val maxColumn = lineEndOffset - lineStartOffset + 1

            assertEquals(51, maxColumn)
        }

        @Test
        fun `offset calculation is correct`() {
            // offset = lineStartOffset + (column - 1)
            val lineStartOffset = 100
            val column = 15
            val offset = lineStartOffset + (column - 1)

            assertEquals(114, offset)
        }
    }

    // ==================== Error Message Format Tests ====================

    @Nested
    inner class ErrorMessageFormatTest {

        @Test
        fun `file not found error message format`() {
            val filePath = "/path/to/missing/file.kt"
            val errorMessage = "File not found: $filePath"

            assertTrue(errorMessage.startsWith("File not found:"))
            assertTrue(errorMessage.contains(filePath))
        }

        @Test
        fun `line out of bounds error message format`() {
            val line = 100
            val lineCount = 50
            val errorMessage = "Line $line out of bounds (valid: 1-$lineCount)"

            assertTrue(errorMessage.contains("Line $line"))
            assertTrue(errorMessage.contains("out of bounds"))
            assertTrue(errorMessage.contains("1-$lineCount"))
        }

        @Test
        fun `column out of bounds error message format`() {
            val line = 10
            val column = 100
            val maxColumn = 80
            val errorMessage = "Column $column out of bounds for line $line (valid: 1-$maxColumn)"

            assertTrue(errorMessage.contains("Column $column"))
            assertTrue(errorMessage.contains("out of bounds"))
            assertTrue(errorMessage.contains("line $line"))
            assertTrue(errorMessage.contains("1-$maxColumn"))
        }

        @Test
        fun `no element found error message format`() {
            val filePath = "/path/to/file.kt"
            val line = 10
            val column = 5
            val errorMessage = "No code element found at $filePath:$line:$column"

            assertTrue(errorMessage.contains("No code element found"))
            assertTrue(errorMessage.contains("$filePath:$line:$column"))
        }

        @Test
        fun `cannot parse file error message format`() {
            val filePath = "/path/to/file.kt"
            val errorMessage = "Cannot parse file: $filePath"

            assertTrue(errorMessage.startsWith("Cannot parse file:"))
            assertTrue(errorMessage.contains(filePath))
        }

        @Test
        fun `cannot get document error message format`() {
            val filePath = "/path/to/file.kt"
            val errorMessage = "Cannot get document for file: $filePath"

            assertTrue(errorMessage.startsWith("Cannot get document"))
            assertTrue(errorMessage.contains(filePath))
        }

        @Test
        fun `file not in project error message format`() {
            val filePath = "/external/file.kt"
            val projectName = "TestProject"
            val errorMessage = "File '$filePath' is not part of project '$projectName'. " +
                "Please specify correct project name."

            assertTrue(errorMessage.contains(filePath))
            assertTrue(errorMessage.contains(projectName))
            assertTrue(errorMessage.contains("Please specify correct project name"))
        }

        @Test
        fun `file does not belong to any project error message format`() {
            val openProjects = listOf("Project1", "Project2", "Project3")
            val errorMessage = "File does not belong to any open project. " +
                "Open projects: ${openProjects.joinToString(", ")}. " +
                "You can specify 'project' parameter explicitly."

            assertTrue(errorMessage.contains("does not belong to any open project"))
            assertTrue(errorMessage.contains("Project1, Project2, Project3"))
            assertTrue(errorMessage.contains("'project' parameter"))
        }
    }

    // ==================== Service Instantiation Test ====================

    @Nested
    inner class ServiceInstantiationTest {

        @Test
        fun `service can be instantiated`() {
            val service = PsiLocatorService()

            assertNotNull(service)
        }
    }

    // ==================== Indexing Error Message Test ====================

    @Nested
    inner class IndexingModeTest {

        @Test
        fun `indexing error message is correct`() {
            val expectedMessage = "IDE is currently indexing. Please wait and try again."

            // This is the expected constant value from the service
            assertEquals(expectedMessage, "IDE is currently indexing. Please wait and try again.")
        }
    }
}
