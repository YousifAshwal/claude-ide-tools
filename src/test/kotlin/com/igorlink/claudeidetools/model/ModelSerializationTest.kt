package com.igorlink.claudeidetools.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Comprehensive tests for model serialization/deserialization.
 * Covers all DTO classes in Requests.kt and Responses.kt.
 */
class ModelSerializationTest {

    // JSON instance with ignoreUnknownKeys = true (mimics production config)
    private val jsonLenient = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Strict JSON instance for testing unknown keys behavior
    private val jsonStrict = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    // ==================== Request DTOs ====================

    @Nested
    inner class RenameRequestTest {

        @Test
        fun `serialize and deserialize with all fields`() {
            val request = RenameRequest(
                file = "/path/to/File.kt",
                line = 10,
                column = 5,
                newName = "newMethodName",
                project = "MyProject"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<RenameRequest>(json)

            assertEquals(request, decoded)
            assertTrue(json.contains("\"file\":\"/path/to/File.kt\""))
            assertTrue(json.contains("\"newName\":\"newMethodName\""))
        }

        @Test
        fun `serialize and deserialize with null project`() {
            val request = RenameRequest(
                file = "/path/to/File.kt",
                line = 1,
                column = 1,
                newName = "name",
                project = null
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<RenameRequest>(json)

            assertEquals(request, decoded)
            assertNull(decoded.project)
        }

        @Test
        fun `deserialize without optional project field`() {
            val json = """{"file":"/path/to/File.kt","line":10,"column":5,"newName":"newName"}"""

            val decoded = jsonLenient.decodeFromString<RenameRequest>(json)

            assertEquals("/path/to/File.kt", decoded.file)
            assertEquals(10, decoded.line)
            assertEquals(5, decoded.column)
            assertEquals("newName", decoded.newName)
            assertNull(decoded.project)
        }

        @Test
        fun `deserialize with unknown keys when lenient`() {
            val json = """{"file":"/path","line":1,"column":1,"newName":"n","unknownField":"value","anotherUnknown":123}"""

            val decoded = jsonLenient.decodeFromString<RenameRequest>(json)

            assertEquals("/path", decoded.file)
            assertEquals("n", decoded.newName)
        }

        @Test
        fun `deserialize with unknown keys fails when strict`() {
            val json = """{"file":"/path","line":1,"column":1,"newName":"n","unknownField":"value"}"""

            assertThrows<kotlinx.serialization.SerializationException> {
                jsonStrict.decodeFromString<RenameRequest>(json)
            }
        }

        @Test
        fun `handles special characters in strings`() {
            val request = RenameRequest(
                file = "/path/with spaces/and\"quotes/File.kt",
                line = 1,
                column = 1,
                newName = "name_with_\$pecial",
                project = "Project\\With\\Backslashes"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<RenameRequest>(json)

            assertEquals(request, decoded)
        }

        @Test
        fun `handles unicode characters`() {
            val request = RenameRequest(
                file = "/path/to/\u0424\u0430\u0439\u043B.kt", // Cyrillic "File"
                line = 1,
                column = 1,
                newName = "\u65B0\u540D\u79F0", // Japanese "new name"
                project = "\uD83D\uDE00Project" // Emoji + Project
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<RenameRequest>(json)

            assertEquals(request, decoded)
        }

        @Test
        fun `handles empty strings`() {
            val request = RenameRequest(
                file = "",
                line = 0,
                column = 0,
                newName = "",
                project = ""
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<RenameRequest>(json)

            assertEquals(request, decoded)
            assertEquals("", decoded.file)
            assertEquals("", decoded.newName)
            assertEquals("", decoded.project)
        }

        @Test
        fun `handles large line and column numbers`() {
            val request = RenameRequest(
                file = "/file.kt",
                line = Int.MAX_VALUE,
                column = Int.MAX_VALUE,
                newName = "name"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<RenameRequest>(json)

            assertEquals(Int.MAX_VALUE, decoded.line)
            assertEquals(Int.MAX_VALUE, decoded.column)
        }

        @Test
        fun `handles negative numbers`() {
            val request = RenameRequest(
                file = "/file.kt",
                line = -1,
                column = -100,
                newName = "name"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<RenameRequest>(json)

            assertEquals(-1, decoded.line)
            assertEquals(-100, decoded.column)
        }

        @Test
        fun `implements LocatorRequest interface`() {
            val request: LocatorRequest = RenameRequest(
                file = "/path/to/File.kt",
                line = 10,
                column = 5,
                newName = "newName",
                project = "project"
            )

            assertEquals("/path/to/File.kt", request.file)
            assertEquals(10, request.line)
            assertEquals(5, request.column)
            assertEquals("project", request.project)
        }
    }

    @Nested
    inner class FindUsagesRequestTest {

        @Test
        fun `serialize and deserialize with all fields`() {
            val request = FindUsagesRequest(
                file = "/path/to/File.java",
                line = 25,
                column = 10,
                project = "TestProject"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<FindUsagesRequest>(json)

            assertEquals(request, decoded)
        }

        @Test
        fun `deserialize without project field`() {
            val json = """{"file":"/File.kt","line":1,"column":1}"""

            val decoded = jsonLenient.decodeFromString<FindUsagesRequest>(json)

            assertNull(decoded.project)
        }

        @Test
        fun `handles unknown keys with lenient json`() {
            val json = """{"file":"/f","line":1,"column":1,"extra":"ignored","nested":{"a":1}}"""

            val decoded = jsonLenient.decodeFromString<FindUsagesRequest>(json)

            assertEquals("/f", decoded.file)
        }

        @Test
        fun `implements LocatorRequest interface`() {
            val request: LocatorRequest = FindUsagesRequest(
                file = "/path",
                line = 5,
                column = 3,
                project = null
            )

            assertEquals("/path", request.file)
            assertEquals(5, request.line)
            assertEquals(3, request.column)
            assertNull(request.project)
        }
    }

    @Nested
    inner class MoveRequestTest {

        @Test
        fun `serialize and deserialize with all fields`() {
            val request = MoveRequest(
                file = "/src/com/example/MyClass.java",
                line = 5,
                column = 14,
                targetPackage = "com.example.newpkg",
                project = "MyProject"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<MoveRequest>(json)

            assertEquals(request, decoded)
            assertTrue(json.contains("\"targetPackage\":\"com.example.newpkg\""))
        }

        @Test
        fun `deserialize without project field`() {
            val json = """{"file":"/File.kt","line":1,"column":1,"targetPackage":"com.new"}"""

            val decoded = jsonLenient.decodeFromString<MoveRequest>(json)

            assertEquals("com.new", decoded.targetPackage)
            assertNull(decoded.project)
        }

        @Test
        fun `handles empty target package`() {
            val request = MoveRequest(
                file = "/file.kt",
                line = 1,
                column = 1,
                targetPackage = ""
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<MoveRequest>(json)

            assertEquals("", decoded.targetPackage)
        }

        @Test
        fun `implements LocatorRequest interface`() {
            val request: LocatorRequest = MoveRequest(
                file = "/path",
                line = 1,
                column = 1,
                targetPackage = "pkg",
                project = "proj"
            )

            assertEquals("/path", request.file)
            assertEquals("proj", request.project)
        }
    }

    @Nested
    inner class ExtractMethodRequestTest {

        @Test
        fun `serialize and deserialize with all fields`() {
            val request = ExtractMethodRequest(
                file = "/src/Main.java",
                startLine = 10,
                startColumn = 5,
                endLine = 20,
                endColumn = 10,
                methodName = "extractedMethod",
                project = "MyProject"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<ExtractMethodRequest>(json)

            assertEquals(request, decoded)
            assertTrue(json.contains("\"startLine\":10"))
            assertTrue(json.contains("\"endLine\":20"))
        }

        @Test
        fun `deserialize without project field`() {
            val json = """{"file":"/f.kt","startLine":1,"startColumn":1,"endLine":5,"endColumn":10,"methodName":"m"}"""

            val decoded = jsonLenient.decodeFromString<ExtractMethodRequest>(json)

            assertEquals("m", decoded.methodName)
            assertNull(decoded.project)
        }

        @Test
        fun `handles same start and end position`() {
            val request = ExtractMethodRequest(
                file = "/file.kt",
                startLine = 5,
                startColumn = 10,
                endLine = 5,
                endColumn = 10,
                methodName = "singlePoint"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<ExtractMethodRequest>(json)

            assertEquals(request, decoded)
        }

        @Test
        fun `has different structure than LocatorRequest`() {
            // ExtractMethodRequest has different structure (startLine/endLine instead of line)
            // This test documents that it's intentionally different from LocatorRequest
            // It uses range-based selection (start/end) rather than single point (line/column)
            val request = ExtractMethodRequest(
                file = "/file.kt",
                startLine = 1,
                startColumn = 1,
                endLine = 10,
                endColumn = 20,
                methodName = "method"
            )

            // Verify the range-based structure
            assertNotEquals(request.startLine, request.endLine)
            assertEquals("/file.kt", request.file)
            assertEquals("method", request.methodName)
        }
    }

    // ==================== Response DTOs ====================

    @Nested
    inner class StatusResponseTest {

        @Test
        fun `serialize and deserialize with all fields`() {
            val response = StatusResponse(
                ok = true,
                ideVersion = "IntelliJ IDEA 2024.1",
                openProjects = listOf(
                    ProjectInfo("Project1", "/path/to/Project1"),
                    ProjectInfo("Project2", "/path/to/Project2")
                ),
                indexingInProgress = false
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<StatusResponse>(json)

            assertEquals(response, decoded)
        }

        @Test
        fun `deserialize without optional indexingInProgress`() {
            val json = """{"ok":true,"ideVersion":"IDEA 2024.1","openProjects":[{"name":"P1","path":"/path/to/P1"}]}"""

            val decoded = jsonLenient.decodeFromString<StatusResponse>(json)

            assertEquals(true, decoded.ok)
            assertEquals("IDEA 2024.1", decoded.ideVersion)
            assertEquals(listOf(ProjectInfo("P1", "/path/to/P1")), decoded.openProjects)
            assertEquals(false, decoded.indexingInProgress) // default value
        }

        @Test
        fun `handles empty openProjects list`() {
            val response = StatusResponse(
                ok = true,
                ideVersion = "IDEA",
                openProjects = emptyList()
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<StatusResponse>(json)

            assertTrue(decoded.openProjects.isEmpty())
        }

        @Test
        fun `handles many open projects`() {
            val projects = (1..100).map { ProjectInfo("Project$it", "/path/to/Project$it") }
            val response = StatusResponse(
                ok = true,
                ideVersion = "IDEA",
                openProjects = projects
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<StatusResponse>(json)

            assertEquals(100, decoded.openProjects.size)
            assertEquals("Project1", decoded.openProjects.first().name)
            assertEquals("Project100", decoded.openProjects.last().name)
        }

        @Test
        fun `handles unicode in project names`() {
            val response = StatusResponse(
                ok = true,
                ideVersion = "IDEA",
                openProjects = listOf(
                    ProjectInfo("\u041F\u0440\u043E\u0435\u043A\u0442", "/path/to/\u041F\u0440\u043E\u0435\u043A\u0442"),
                    ProjectInfo("\u30D7\u30ED\u30B8\u30A7\u30AF\u30C8", "/path/to/\u30D7\u30ED\u30B8\u30A7\u30AF\u30C8")
                )
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<StatusResponse>(json)

            assertEquals("\u041F\u0440\u043E\u0435\u043A\u0442", decoded.openProjects[0].name) // Russian
            assertEquals("\u30D7\u30ED\u30B8\u30A7\u30AF\u30C8", decoded.openProjects[1].name) // Japanese
        }
    }

    @Nested
    inner class RefactoringResponseTest {

        @Test
        fun `serialize and deserialize successful response`() {
            val response = RefactoringResponse(
                success = true,
                message = "Rename completed successfully",
                affectedFiles = listOf("/src/A.kt", "/src/B.kt")
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<RefactoringResponse>(json)

            assertEquals(response, decoded)
        }

        @Test
        fun `serialize and deserialize failed response`() {
            val response = RefactoringResponse(
                success = false,
                message = "Cannot rename: symbol not found"
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<RefactoringResponse>(json)

            assertFalse(decoded.success)
            assertTrue(decoded.affectedFiles.isEmpty())
        }

        @Test
        fun `deserialize without affectedFiles`() {
            val json = """{"success":true,"message":"OK"}"""

            val decoded = jsonLenient.decodeFromString<RefactoringResponse>(json)

            assertTrue(decoded.success)
            assertTrue(decoded.affectedFiles.isEmpty()) // default value
        }

        @Test
        fun `handles special characters in message`() {
            val response = RefactoringResponse(
                success = false,
                message = "Error: \"unexpected token\" at line 5\nDetails: \t<none>"
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<RefactoringResponse>(json)

            assertTrue(decoded.message.contains("\"unexpected token\""))
            assertTrue(decoded.message.contains("\n"))
            assertTrue(decoded.message.contains("\t"))
        }
    }

    @Nested
    inner class UsageTest {

        @Test
        fun `serialize and deserialize`() {
            val usage = Usage(
                file = "/src/main/java/Example.java",
                line = 42,
                column = 15,
                preview = "public void method() {"
            )

            val json = jsonLenient.encodeToString(usage)
            val decoded = jsonLenient.decodeFromString<Usage>(json)

            assertEquals(usage, decoded)
        }

        @Test
        fun `handles multiline preview`() {
            val usage = Usage(
                file = "/file.kt",
                line = 1,
                column = 1,
                preview = "line1\nline2\nline3"
            )

            val json = jsonLenient.encodeToString(usage)
            val decoded = jsonLenient.decodeFromString<Usage>(json)

            assertEquals("line1\nline2\nline3", decoded.preview)
        }

        @Test
        fun `handles empty preview`() {
            val usage = Usage(
                file = "/file.kt",
                line = 1,
                column = 1,
                preview = ""
            )

            val json = jsonLenient.encodeToString(usage)
            val decoded = jsonLenient.decodeFromString<Usage>(json)

            assertEquals("", decoded.preview)
        }

        @Test
        fun `handles preview with code symbols`() {
            val usage = Usage(
                file = "/file.kt",
                line = 1,
                column = 1,
                preview = "val map = mapOf<String, List<Int>>()"
            )

            val json = jsonLenient.encodeToString(usage)
            val decoded = jsonLenient.decodeFromString<Usage>(json)

            assertEquals("val map = mapOf<String, List<Int>>()", decoded.preview)
        }
    }

    @Nested
    inner class FindUsagesResponseTest {

        @Test
        fun `serialize and deserialize with usages`() {
            val response = FindUsagesResponse(
                success = true,
                message = "Found 2 usages",
                usages = listOf(
                    Usage("/A.kt", 10, 5, "usage 1"),
                    Usage("/B.kt", 20, 10, "usage 2")
                )
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<FindUsagesResponse>(json)

            assertEquals(response, decoded)
            assertEquals(2, decoded.usages.size)
        }

        @Test
        fun `deserialize without usages field`() {
            val json = """{"success":true,"message":"No usages found"}"""

            val decoded = jsonLenient.decodeFromString<FindUsagesResponse>(json)

            assertTrue(decoded.success)
            assertTrue(decoded.usages.isEmpty())
        }

        @Test
        fun `handles many usages`() {
            val usages = (1..500).map { Usage("/file$it.kt", it, it, "preview $it") }
            val response = FindUsagesResponse(
                success = true,
                message = "Found 500 usages",
                usages = usages
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<FindUsagesResponse>(json)

            assertEquals(500, decoded.usages.size)
        }
    }

    @Nested
    inner class ErrorResponseTest {

        @Test
        fun `serialize and deserialize with all fields`() {
            val response = ErrorResponse(
                success = false,
                error = "Symbol not found",
                code = "SYMBOL_NOT_FOUND"
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<ErrorResponse>(json)

            assertEquals(response, decoded)
        }

        @Test
        fun `deserialize with defaults`() {
            val json = """{"error":"Something went wrong"}"""

            val decoded = jsonLenient.decodeFromString<ErrorResponse>(json)

            assertFalse(decoded.success) // default = false
            assertEquals("Something went wrong", decoded.error)
            assertEquals("UNKNOWN_ERROR", decoded.code) // default
        }

        @Test
        fun `handles complex error messages`() {
            val response = ErrorResponse(
                success = false,
                error = """
                    Multiple errors occurred:
                    1. File not found: /path/to/file.kt
                    2. Permission denied
                    Stack trace:
                        at com.example.Method(File.kt:10)
                """.trimIndent(),
                code = "MULTIPLE_ERRORS"
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<ErrorResponse>(json)

            assertTrue(decoded.error.contains("Multiple errors occurred"))
            assertTrue(decoded.error.contains("Stack trace"))
        }
    }

    // ==================== Diagnostics DTOs ====================

    @Nested
    inner class DiagnosticsRequestTest {

        @Test
        fun `serialize and deserialize with all fields`() {
            val request = DiagnosticsRequest(
                file = "/path/to/File.kt",
                project = "MyProject",
                severity = listOf("ERROR", "WARNING"),
                limit = 50,
                runInspections = true
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<DiagnosticsRequest>(json)

            assertEquals(request, decoded)
            assertTrue(json.contains("\"file\":\"/path/to/File.kt\""))
            assertTrue(json.contains("\"severity\""))
            assertTrue(json.contains("\"runInspections\":true"))
        }

        @Test
        fun `serialize and deserialize with null file for project-wide`() {
            val request = DiagnosticsRequest(
                file = null,
                project = "MyProject",
                severity = listOf("ERROR"),
                limit = 100
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<DiagnosticsRequest>(json)

            assertEquals(request, decoded)
            assertNull(decoded.file)
        }

        @Test
        fun `serialize and deserialize with empty severity list`() {
            val request = DiagnosticsRequest(
                file = "/file.kt",
                severity = emptyList(),
                limit = 100
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<DiagnosticsRequest>(json)

            assertTrue(decoded.severity?.isEmpty() ?: true)
        }

        @Test
        fun `serialize and deserialize with multiple severities`() {
            val request = DiagnosticsRequest(
                file = "/file.kt",
                severity = listOf("ERROR", "WARNING", "WEAK_WARNING", "INFO", "HINT")
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<DiagnosticsRequest>(json)

            assertEquals(5, decoded.severity?.size)
            assertTrue(decoded.severity?.contains("ERROR") == true)
            assertTrue(decoded.severity?.contains("HINT") == true)
        }

        @Test
        fun `deserialize without optional fields uses defaults`() {
            val json = """{}"""

            val decoded = jsonLenient.decodeFromString<DiagnosticsRequest>(json)

            assertNull(decoded.file)
            assertNull(decoded.project)
            assertNull(decoded.severity)
            assertEquals(100, decoded.limit)
            assertFalse(decoded.runInspections)
        }

        @Test
        fun `serialize and deserialize with runInspections enabled`() {
            val request = DiagnosticsRequest(
                file = "/path/to/File.kt",
                runInspections = true
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<DiagnosticsRequest>(json)

            assertEquals(request, decoded)
            assertTrue(decoded.runInspections)
        }

        @Test
        fun `serialize and deserialize with runInspections disabled (default)`() {
            val request = DiagnosticsRequest(
                file = "/path/to/File.kt"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<DiagnosticsRequest>(json)

            assertEquals(request, decoded)
            assertFalse(decoded.runInspections)
        }

        @Test
        fun `deserialize with unknown keys when lenient`() {
            val json = """{"file":"/path","unknownField":"value","anotherUnknown":123}"""

            val decoded = jsonLenient.decodeFromString<DiagnosticsRequest>(json)

            assertEquals("/path", decoded.file)
        }

        @Test
        fun `handles special characters in file path`() {
            val request = DiagnosticsRequest(
                file = "/path/with spaces/and\"quotes/File.kt"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<DiagnosticsRequest>(json)

            assertEquals(request, decoded)
        }

        @Test
        fun `handles negative limit value`() {
            val request = DiagnosticsRequest(limit = -1)

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<DiagnosticsRequest>(json)

            assertEquals(-1, decoded.limit)
        }

        @Test
        fun `handles zero limit value`() {
            val request = DiagnosticsRequest(limit = 0)

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<DiagnosticsRequest>(json)

            assertEquals(0, decoded.limit)
        }
    }

    @Nested
    inner class DiagnosticsResponseTest {

        @Test
        fun `serialize and deserialize successful response with diagnostics`() {
            val response = DiagnosticsResponse(
                success = true,
                message = "Found 2 diagnostics",
                diagnostics = listOf(
                    Diagnostic("/a.kt", 10, 5, 10, 15, "ERROR", "Cannot resolve symbol", "Java"),
                    Diagnostic("/b.kt", 20, 10, 20, 25, "WARNING", "Unused variable", "Kotlin")
                ),
                totalCount = 2,
                truncated = false
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<DiagnosticsResponse>(json)

            assertEquals(response, decoded)
            assertEquals(2, decoded.diagnostics.size)
        }

        @Test
        fun `serialize and deserialize failed response`() {
            val response = DiagnosticsResponse(
                success = false,
                message = "File not found"
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<DiagnosticsResponse>(json)

            assertFalse(decoded.success)
            assertEquals("File not found", decoded.message)
            assertTrue(decoded.diagnostics.isEmpty())
        }

        @Test
        fun `deserialize without diagnostics field uses default empty list`() {
            val json = """{"success":true,"message":"No diagnostics"}"""

            val decoded = jsonLenient.decodeFromString<DiagnosticsResponse>(json)

            assertTrue(decoded.success)
            assertTrue(decoded.diagnostics.isEmpty())
            assertEquals(0, decoded.totalCount)
            assertFalse(decoded.truncated)
        }

        @Test
        fun `handles many diagnostics`() {
            val diagnostics = (1..500).map {
                Diagnostic("/file$it.kt", it, it, it, it + 5, "ERROR", "Error $it", "Java")
            }
            val response = DiagnosticsResponse(
                success = true,
                message = "Found 500 diagnostics",
                diagnostics = diagnostics,
                totalCount = 500,
                truncated = false
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<DiagnosticsResponse>(json)

            assertEquals(500, decoded.diagnostics.size)
        }

        @Test
        fun `totalCount and truncated fields serialize correctly`() {
            val response = DiagnosticsResponse(
                success = true,
                message = "Found 1000 diagnostics (showing first 100)",
                diagnostics = (1..100).map {
                    Diagnostic("/file.kt", it, 1, it, 10, "WARNING", "Warning $it", null)
                },
                totalCount = 1000,
                truncated = true
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<DiagnosticsResponse>(json)

            assertEquals(1000, decoded.totalCount)
            assertTrue(decoded.truncated)
            assertEquals(100, decoded.diagnostics.size)
        }
    }

    @Nested
    inner class DiagnosticTest {

        @Test
        fun `serialize and deserialize with all fields`() {
            val diagnostic = Diagnostic(
                file = "/src/Main.java",
                line = 42,
                column = 15,
                endLine = 42,
                endColumn = 25,
                severity = "ERROR",
                message = "Cannot resolve symbol 'foo'",
                source = "Java"
            )

            val json = jsonLenient.encodeToString(diagnostic)
            val decoded = jsonLenient.decodeFromString<Diagnostic>(json)

            assertEquals(diagnostic, decoded)
        }

        @Test
        fun `serialize and deserialize with null source`() {
            val diagnostic = Diagnostic(
                file = "/file.kt",
                line = 1,
                column = 1,
                endLine = 1,
                endColumn = 10,
                severity = "WARNING",
                message = "Unused variable",
                source = null
            )

            val json = jsonLenient.encodeToString(diagnostic)
            val decoded = jsonLenient.decodeFromString<Diagnostic>(json)

            assertNull(decoded.source)
        }

        @Test
        fun `handles special characters in message`() {
            val diagnostic = Diagnostic(
                file = "/file.kt",
                line = 1,
                column = 1,
                endLine = 1,
                endColumn = 5,
                severity = "ERROR",
                message = "Error: \"unexpected token\" at line 5\nDetails: \t<none>",
                source = null
            )

            val json = jsonLenient.encodeToString(diagnostic)
            val decoded = jsonLenient.decodeFromString<Diagnostic>(json)

            assertTrue(decoded.message.contains("\"unexpected token\""))
            assertTrue(decoded.message.contains("\n"))
            assertTrue(decoded.message.contains("\t"))
        }

        @Test
        fun `handles multiline message`() {
            val diagnostic = Diagnostic(
                file = "/file.kt",
                line = 1,
                column = 1,
                endLine = 1,
                endColumn = 5,
                severity = "ERROR",
                message = "Line 1\nLine 2\nLine 3",
                source = null
            )

            val json = jsonLenient.encodeToString(diagnostic)
            val decoded = jsonLenient.decodeFromString<Diagnostic>(json)

            assertEquals("Line 1\nLine 2\nLine 3", decoded.message)
        }

        @Test
        fun `handles code symbols in message`() {
            val diagnostic = Diagnostic(
                file = "/file.kt",
                line = 1,
                column = 1,
                endLine = 1,
                endColumn = 5,
                severity = "WARNING",
                message = "Type mismatch: expected Map<String, List<Int>> but got HashMap<String, ArrayList<Int>>",
                source = null
            )

            val json = jsonLenient.encodeToString(diagnostic)
            val decoded = jsonLenient.decodeFromString<Diagnostic>(json)

            assertTrue(decoded.message.contains("Map<String, List<Int>>"))
        }

        @Test
        fun `handles large line and column numbers`() {
            val diagnostic = Diagnostic(
                file = "/file.kt",
                line = Int.MAX_VALUE,
                column = Int.MAX_VALUE,
                endLine = Int.MAX_VALUE,
                endColumn = Int.MAX_VALUE,
                severity = "ERROR",
                message = "Error",
                source = null
            )

            val json = jsonLenient.encodeToString(diagnostic)
            val decoded = jsonLenient.decodeFromString<Diagnostic>(json)

            assertEquals(Int.MAX_VALUE, decoded.line)
            assertEquals(Int.MAX_VALUE, decoded.column)
        }
    }

    @Nested
    inner class DiagnosticSeverityTest {

        @Test
        fun `all severity values serialize correctly`() {
            for (severity in DiagnosticSeverity.entries) {
                val diagnostic = Diagnostic(
                    file = "/file.kt",
                    line = 1,
                    column = 1,
                    endLine = 1,
                    endColumn = 5,
                    severity = severity.name,
                    message = "Test",
                    source = null
                )

                val json = jsonLenient.encodeToString(diagnostic)
                assertTrue(json.contains("\"severity\":\"${severity.name}\""))
            }
        }

        @Test
        fun `fromString parses all valid severities`() {
            assertEquals(DiagnosticSeverity.ERROR, DiagnosticSeverity.fromString("ERROR"))
            assertEquals(DiagnosticSeverity.WARNING, DiagnosticSeverity.fromString("WARNING"))
            assertEquals(DiagnosticSeverity.WEAK_WARNING, DiagnosticSeverity.fromString("WEAK_WARNING"))
            assertEquals(DiagnosticSeverity.INFO, DiagnosticSeverity.fromString("INFO"))
            assertEquals(DiagnosticSeverity.HINT, DiagnosticSeverity.fromString("HINT"))
        }

        @Test
        fun `fromString handles case variations`() {
            assertEquals(DiagnosticSeverity.ERROR, DiagnosticSeverity.fromString("error"))
            assertEquals(DiagnosticSeverity.WARNING, DiagnosticSeverity.fromString("Warning"))
            assertEquals(DiagnosticSeverity.WEAK_WARNING, DiagnosticSeverity.fromString("weak_warning"))
            assertEquals(DiagnosticSeverity.INFO, DiagnosticSeverity.fromString("iNfO"))
        }

        @Test
        fun `fromString returns null for invalid strings`() {
            assertNull(DiagnosticSeverity.fromString("INVALID"))
            assertNull(DiagnosticSeverity.fromString(""))
            assertNull(DiagnosticSeverity.fromString("ERR"))
            assertNull(DiagnosticSeverity.fromString("WARNINGS"))
        }
    }

    // ==================== Cross-cutting Concerns ====================

    @Nested
    inner class IgnoreUnknownKeysTest {

        @Test
        fun `all request types ignore unknown keys`() {
            val renameJson = """{"file":"/f","line":1,"column":1,"newName":"n","unknown1":"v","unknown2":123}"""
            val findJson = """{"file":"/f","line":1,"column":1,"unknown":"v"}"""
            val moveJson = """{"file":"/f","line":1,"column":1,"targetPackage":"pkg","unknown":"v"}"""
            val extractJson = """{"file":"/f","startLine":1,"startColumn":1,"endLine":2,"endColumn":2,"methodName":"m","unknown":"v"}"""

            // All should deserialize without exception
            assertDoesNotThrow { jsonLenient.decodeFromString<RenameRequest>(renameJson) }
            assertDoesNotThrow { jsonLenient.decodeFromString<FindUsagesRequest>(findJson) }
            assertDoesNotThrow { jsonLenient.decodeFromString<MoveRequest>(moveJson) }
            assertDoesNotThrow { jsonLenient.decodeFromString<ExtractMethodRequest>(extractJson) }
        }

        @Test
        fun `all response types ignore unknown keys`() {
            val statusJson = """{"ok":true,"ideVersion":"v","openProjects":[],"unknown":"v"}"""
            val refactorJson = """{"success":true,"message":"m","unknown":"v"}"""
            val usageJson = """{"file":"/f","line":1,"column":1,"preview":"p","unknown":"v"}"""
            val findUsagesJson = """{"success":true,"message":"m","usages":[],"unknown":"v"}"""
            val errorJson = """{"error":"e","unknown":"v"}"""

            assertDoesNotThrow { jsonLenient.decodeFromString<StatusResponse>(statusJson) }
            assertDoesNotThrow { jsonLenient.decodeFromString<RefactoringResponse>(refactorJson) }
            assertDoesNotThrow { jsonLenient.decodeFromString<Usage>(usageJson) }
            assertDoesNotThrow { jsonLenient.decodeFromString<FindUsagesResponse>(findUsagesJson) }
            assertDoesNotThrow { jsonLenient.decodeFromString<ErrorResponse>(errorJson) }
        }
    }

    @Nested
    inner class EdgeCasesTest {

        @Test
        fun `handles Windows-style paths`() {
            val request = RenameRequest(
                file = "C:\\Users\\Developer\\Project\\src\\Main.kt",
                line = 1,
                column = 1,
                newName = "name"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<RenameRequest>(json)

            assertEquals(request.file, decoded.file)
        }

        @Test
        fun `handles paths with spaces and special chars`() {
            val request = FindUsagesRequest(
                file = "/Users/Dev User/My Project (v2)/src/Main File.kt",
                line = 1,
                column = 1
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<FindUsagesRequest>(json)

            assertEquals(request.file, decoded.file)
        }

        @Test
        fun `handles very long strings`() {
            val longString = "a".repeat(10000)
            val request = RenameRequest(
                file = "/path/$longString.kt",
                line = 1,
                column = 1,
                newName = longString
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<RenameRequest>(json)

            assertEquals(10000, decoded.newName.length)
        }

        @Test
        fun `handles JSON with different whitespace formatting`() {
            val compactJson = """{"file":"/f","line":1,"column":1,"newName":"n"}"""
            val prettyJson = """
                {
                    "file": "/f",
                    "line": 1,
                    "column": 1,
                    "newName": "n"
                }
            """.trimIndent()

            val fromCompact = jsonLenient.decodeFromString<RenameRequest>(compactJson)
            val fromPretty = jsonLenient.decodeFromString<RenameRequest>(prettyJson)

            assertEquals(fromCompact, fromPretty)
        }

        @Test
        fun `handles null vs absent optional fields consistently`() {
            val withNull = """{"file":"/f","line":1,"column":1,"newName":"n","project":null}"""
            val withoutField = """{"file":"/f","line":1,"column":1,"newName":"n"}"""

            val decodedWithNull = jsonLenient.decodeFromString<RenameRequest>(withNull)
            val decodedWithout = jsonLenient.decodeFromString<RenameRequest>(withoutField)

            assertNull(decodedWithNull.project)
            assertNull(decodedWithout.project)
            assertEquals(decodedWithNull, decodedWithout)
        }

        @Test
        fun `handles zero values`() {
            val request = RenameRequest(
                file = "/f",
                line = 0,
                column = 0,
                newName = "n"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<RenameRequest>(json)

            assertEquals(0, decoded.line)
            assertEquals(0, decoded.column)
        }

        @Test
        fun `handles newlines and tabs in strings`() {
            val response = RefactoringResponse(
                success = true,
                message = "Line1\nLine2\tTabbed\rCarriage"
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<RefactoringResponse>(json)

            assertTrue(decoded.message.contains("\n"))
            assertTrue(decoded.message.contains("\t"))
            assertTrue(decoded.message.contains("\r"))
        }
    }

    // ==================== Apply Fix DTOs ====================

    @Nested
    inner class ApplyFixRequestTest {

        @Test
        fun `serialize and deserialize with all fields`() {
            val request = ApplyFixRequest(
                file = "/path/to/File.kt",
                line = 10,
                column = 5,
                fixId = 0,
                diagnosticMessage = "Cannot resolve symbol 'foo'",
                project = "MyProject"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<ApplyFixRequest>(json)

            assertEquals(request, decoded)
            assertTrue(json.contains("\"file\":\"/path/to/File.kt\""))
            assertTrue(json.contains("\"fixId\":0"))
        }

        @Test
        fun `serialize and deserialize with null diagnosticMessage`() {
            val request = ApplyFixRequest(
                file = "/path/to/File.kt",
                line = 1,
                column = 1,
                fixId = 2,
                diagnosticMessage = null,
                project = null
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<ApplyFixRequest>(json)

            assertEquals(request, decoded)
            assertNull(decoded.diagnosticMessage)
            assertNull(decoded.project)
        }

        @Test
        fun `deserialize without optional fields`() {
            val json = """{"file":"/path/to/File.kt","line":10,"column":5,"fixId":0}"""

            val decoded = jsonLenient.decodeFromString<ApplyFixRequest>(json)

            assertEquals("/path/to/File.kt", decoded.file)
            assertEquals(10, decoded.line)
            assertEquals(5, decoded.column)
            assertEquals(0, decoded.fixId)
            assertNull(decoded.diagnosticMessage)
            assertNull(decoded.project)
        }

        @Test
        fun `handles special characters in diagnosticMessage`() {
            val request = ApplyFixRequest(
                file = "/file.kt",
                line = 1,
                column = 1,
                fixId = 0,
                diagnosticMessage = "Error: \"unexpected token\" at line 5\nDetails: \t<none>"
            )

            val json = jsonLenient.encodeToString(request)
            val decoded = jsonLenient.decodeFromString<ApplyFixRequest>(json)

            assertTrue(decoded.diagnosticMessage?.contains("\"unexpected token\"") == true)
            assertTrue(decoded.diagnosticMessage?.contains("\n") == true)
        }

        @Test
        fun `deserialize with unknown keys when lenient`() {
            val json = """{"file":"/path","line":1,"column":1,"fixId":0,"unknownField":"value"}"""

            val decoded = jsonLenient.decodeFromString<ApplyFixRequest>(json)

            assertEquals("/path", decoded.file)
            assertEquals(0, decoded.fixId)
        }
    }

    @Nested
    inner class ApplyFixResponseTest {

        @Test
        fun `serialize and deserialize successful response`() {
            val response = ApplyFixResponse(
                success = true,
                message = "Applied fix 'Add explicit this'",
                fixName = "Add explicit 'this'",
                affectedFiles = listOf("/path/to/MyClass.java")
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<ApplyFixResponse>(json)

            assertEquals(response, decoded)
            assertTrue(decoded.success)
            assertEquals("Add explicit 'this'", decoded.fixName)
        }

        @Test
        fun `serialize and deserialize failed response`() {
            val response = ApplyFixResponse(
                success = false,
                message = "No diagnostic found at the specified location"
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<ApplyFixResponse>(json)

            assertFalse(decoded.success)
            assertNull(decoded.fixName)
            assertTrue(decoded.affectedFiles.isEmpty())
        }

        @Test
        fun `deserialize without optional fields uses defaults`() {
            val json = """{"success":true,"message":"OK"}"""

            val decoded = jsonLenient.decodeFromString<ApplyFixResponse>(json)

            assertTrue(decoded.success)
            assertEquals("OK", decoded.message)
            assertNull(decoded.fixName)
            assertTrue(decoded.affectedFiles.isEmpty())
        }

        @Test
        fun `handles multiple affected files`() {
            val response = ApplyFixResponse(
                success = true,
                message = "Applied fix",
                fixName = "Import class",
                affectedFiles = listOf("/src/A.kt", "/src/B.kt", "/src/C.kt")
            )

            val json = jsonLenient.encodeToString(response)
            val decoded = jsonLenient.decodeFromString<ApplyFixResponse>(json)

            assertEquals(3, decoded.affectedFiles.size)
        }
    }

    @Nested
    inner class QuickFixTest {

        @Test
        fun `serialize and deserialize with all fields`() {
            val quickFix = QuickFix(
                id = 0,
                name = "Add explicit 'this'",
                familyName = "Add explicit 'this'",
                description = "Adds explicit 'this.' qualifier"
            )

            val json = jsonLenient.encodeToString(quickFix)
            val decoded = jsonLenient.decodeFromString<QuickFix>(json)

            assertEquals(quickFix, decoded)
            assertEquals(0, decoded.id)
            assertEquals("Add explicit 'this'", decoded.name)
        }

        @Test
        fun `serialize and deserialize with null optional fields`() {
            val quickFix = QuickFix(
                id = 1,
                name = "Import class",
                familyName = null,
                description = null
            )

            val json = jsonLenient.encodeToString(quickFix)
            val decoded = jsonLenient.decodeFromString<QuickFix>(json)

            assertEquals(1, decoded.id)
            assertEquals("Import class", decoded.name)
            assertNull(decoded.familyName)
            assertNull(decoded.description)
        }

        @Test
        fun `deserialize without optional fields`() {
            val json = """{"id":2,"name":"Create function"}"""

            val decoded = jsonLenient.decodeFromString<QuickFix>(json)

            assertEquals(2, decoded.id)
            assertEquals("Create function", decoded.name)
            assertNull(decoded.familyName)
            assertNull(decoded.description)
        }

        @Test
        fun `handles special characters in name and description`() {
            val quickFix = QuickFix(
                id = 0,
                name = "Add type <String, List<Int>>",
                familyName = "Add type",
                description = "Adds explicit type annotation:\n  val x: Map<String, List<Int>>"
            )

            val json = jsonLenient.encodeToString(quickFix)
            val decoded = jsonLenient.decodeFromString<QuickFix>(json)

            assertTrue(decoded.name.contains("<String, List<Int>>"))
            assertTrue(decoded.description?.contains("\n") == true)
        }
    }

    @Nested
    inner class DiagnosticWithFixesTest {

        @Test
        fun `serialize and deserialize diagnostic with fixes`() {
            val diagnostic = Diagnostic(
                file = "/src/Main.java",
                line = 42,
                column = 15,
                endLine = 42,
                endColumn = 25,
                severity = "ERROR",
                message = "Cannot resolve symbol 'foo'",
                source = "Java",
                fixes = listOf(
                    QuickFix(0, "Import class", "Import", null),
                    QuickFix(1, "Create local variable", "Create", null)
                )
            )

            val json = jsonLenient.encodeToString(diagnostic)
            val decoded = jsonLenient.decodeFromString<Diagnostic>(json)

            assertEquals(diagnostic, decoded)
            assertEquals(2, decoded.fixes.size)
            assertEquals("Import class", decoded.fixes[0].name)
            assertEquals("Create local variable", decoded.fixes[1].name)
        }

        @Test
        fun `serialize and deserialize diagnostic without fixes`() {
            val diagnostic = Diagnostic(
                file = "/src/Main.java",
                line = 1,
                column = 1,
                endLine = 1,
                endColumn = 10,
                severity = "INFO",
                message = "Info message",
                source = null,
                fixes = emptyList()
            )

            val json = jsonLenient.encodeToString(diagnostic)
            val decoded = jsonLenient.decodeFromString<Diagnostic>(json)

            assertTrue(decoded.fixes.isEmpty())
        }

        @Test
        fun `deserialize diagnostic without fixes field uses default empty list`() {
            val json = """{"file":"/f.kt","line":1,"column":1,"endLine":1,"endColumn":5,"severity":"ERROR","message":"Error"}"""

            val decoded = jsonLenient.decodeFromString<Diagnostic>(json)

            assertTrue(decoded.fixes.isEmpty())
        }

        @Test
        fun `handles many fixes`() {
            val fixes = (0..9).map { QuickFix(it, "Fix $it", "Family $it", null) }
            val diagnostic = Diagnostic(
                file = "/file.kt",
                line = 1,
                column = 1,
                endLine = 1,
                endColumn = 5,
                severity = "ERROR",
                message = "Error",
                source = null,
                fixes = fixes
            )

            val json = jsonLenient.encodeToString(diagnostic)
            val decoded = jsonLenient.decodeFromString<Diagnostic>(json)

            assertEquals(10, decoded.fixes.size)
            assertEquals("Fix 0", decoded.fixes.first().name)
            assertEquals("Fix 9", decoded.fixes.last().name)
        }
    }

    @Nested
    inner class JsonStructureTest {

        @Test
        fun `RenameRequest produces expected JSON structure`() {
            val request = RenameRequest(
                file = "/path/File.kt",
                line = 10,
                column = 5,
                newName = "newName",
                project = "proj"
            )

            val json = jsonLenient.encodeToString(request)

            // Verify all expected keys are present
            assertTrue(json.contains("\"file\":"))
            assertTrue(json.contains("\"line\":"))
            assertTrue(json.contains("\"column\":"))
            assertTrue(json.contains("\"newName\":"))
            assertTrue(json.contains("\"project\":"))
        }

        @Test
        fun `StatusResponse produces expected JSON structure`() {
            val response = StatusResponse(
                ok = true,
                ideVersion = "2024.1",
                openProjects = listOf(
                    ProjectInfo("P1", "/path/to/P1"),
                    ProjectInfo("P2", "/path/to/P2")
                ),
                indexingInProgress = true
            )

            val json = jsonLenient.encodeToString(response)

            assertTrue(json.contains("\"ok\":true"))
            assertTrue(json.contains("\"ideVersion\":"))
            assertTrue(json.contains("\"openProjects\":["))
            assertTrue(json.contains("\"indexingInProgress\":true"))
        }
    }
}
