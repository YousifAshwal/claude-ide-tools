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
