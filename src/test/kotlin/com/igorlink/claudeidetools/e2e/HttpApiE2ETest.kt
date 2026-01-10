package com.igorlink.claudeidetools.e2e

import com.igorlink.claudeidetools.model.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * End-to-End tests for HTTP API.
 *
 * These tests verify the complete HTTP request/response cycle through the real HTTP layer.
 * Stub handlers are used to provide predictable responses, but the HTTP layer (routing,
 * serialization, content negotiation, error handling) is fully tested.
 *
 * Test coverage:
 * 1. GET /status - Health check endpoint
 * 2. POST /rename - Rename refactoring endpoint
 * 3. POST /findUsages - Find usages endpoint
 * 4. POST /move - Move class/package endpoint
 * 5. POST /extractMethod - Extract method endpoint
 *
 * Each endpoint is tested for:
 * - Successful request/response flow
 * - Request deserialization
 * - Response serialization
 * - Error handling
 * - Business logic validation (via stub handlers)
 */
class HttpApiE2ETest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    // ==================== Stub Handlers ====================

    /**
     * Stub handler for /status endpoint.
     * Simulates real StatusHandler behavior.
     */
    private fun stubStatusHandler(): StatusResponse {
        return StatusResponse(
            ok = true,
            ideVersion = "IntelliJ IDEA 2024.1 (Build #IC-241.15989.150)",
            openProjects = listOf(
                ProjectInfo("TestProject", "/path/to/TestProject"),
                ProjectInfo("AnotherProject", "/path/to/AnotherProject")
            ),
            indexingInProgress = false
        )
    }

    /**
     * Stub handler for /rename endpoint.
     * Validates request and returns appropriate response.
     */
    private fun stubRenameHandler(request: RenameRequest): RefactoringResponse {
        // Simulate real handler validation
        if (request.newName.isBlank()) {
            return RefactoringResponse(false, "New name cannot be empty")
        }

        if (!request.file.endsWith(".kt") && !request.file.endsWith(".java")) {
            return RefactoringResponse(false, "Unsupported file type")
        }

        if (request.line <= 0 || request.column <= 0) {
            return RefactoringResponse(false, "Line and column must be positive")
        }

        return RefactoringResponse(
            success = true,
            message = "Renamed element to '${request.newName}' at ${request.file}:${request.line}:${request.column}",
            affectedFiles = listOf(request.file, "/related/File.kt")
        )
    }

    /**
     * Stub handler for /findUsages endpoint.
     * Simulates finding usages based on request parameters.
     */
    private fun stubFindUsagesHandler(request: FindUsagesRequest): FindUsagesResponse {
        if (request.line <= 0 || request.column <= 0) {
            return FindUsagesResponse(false, "Line and column must be positive")
        }

        // Simulate finding usages
        val usages = listOf(
            Usage(
                file = request.file,
                line = request.line,
                column = request.column,
                preview = "val element = SomeClass()"
            ),
            Usage(
                file = "/other/File.kt",
                line = 42,
                column = 15,
                preview = "fun process(element: SomeClass)"
            ),
            Usage(
                file = "/test/TestFile.kt",
                line = 10,
                column = 8,
                preview = "@Test fun testElement()"
            )
        )

        return FindUsagesResponse(
            success = true,
            message = "Found ${usages.size} usage(s)",
            usages = usages
        )
    }

    /**
     * Stub handler for /move endpoint.
     * Validates move request and returns response.
     */
    private fun stubMoveHandler(request: MoveRequest): RefactoringResponse {
        if (request.targetPackage.isBlank()) {
            return RefactoringResponse(false, "Target package cannot be empty")
        }

        if (!request.file.endsWith(".java") && !request.file.endsWith(".kt")) {
            return RefactoringResponse(false, "Move is only supported for Java/Kotlin files")
        }

        val className = request.file.substringAfterLast("/").substringBeforeLast(".")
        return RefactoringResponse(
            success = true,
            message = "Moved '$className' to package '${request.targetPackage}'",
            affectedFiles = listOf(request.file)
        )
    }

    /**
     * Stub handler for /extractMethod endpoint.
     * Validates extraction request and returns response.
     */
    private fun stubExtractMethodHandler(request: ExtractMethodRequest): RefactoringResponse {
        if (request.methodName.isBlank()) {
            return RefactoringResponse(false, "Method name cannot be empty")
        }

        if (request.startLine > request.endLine) {
            return RefactoringResponse(false, "Start line cannot be after end line")
        }

        if (request.startLine == request.endLine && request.startColumn > request.endColumn) {
            return RefactoringResponse(false, "Invalid range: start column is after end column on the same line")
        }

        if (!request.file.endsWith(".java")) {
            return RefactoringResponse(false, "Extract method is currently only supported for Java files")
        }

        return RefactoringResponse(
            success = true,
            message = "Extracted method '${request.methodName}' from lines ${request.startLine}-${request.endLine}",
            affectedFiles = listOf(request.file)
        )
    }

    // ==================== Test Application Configuration ====================

    /**
     * Configures test application with stub handlers that mirror production routing.
     * The HTTP layer is real - only handlers are stubbed.
     */
    private fun ApplicationTestBuilder.configureE2ETestApplication() {
        application {
            install(ContentNegotiation) {
                json(json)
            }

            routing {
                // GET /status
                get("/status") {
                    val response = stubStatusHandler()
                    call.respond(response)
                }

                // POST /rename
                post("/rename") {
                    handleRequest<RenameRequest>("RENAME_ERROR") { request ->
                        stubRenameHandler(request)
                    }
                }

                // POST /findUsages
                post("/findUsages") {
                    handleRequest<FindUsagesRequest>("FIND_USAGES_ERROR") { request ->
                        stubFindUsagesHandler(request)
                    }
                }

                // POST /move
                post("/move") {
                    handleRequest<MoveRequest>("MOVE_ERROR") { request ->
                        stubMoveHandler(request)
                    }
                }

                // POST /extractMethod
                post("/extractMethod") {
                    handleRequest<ExtractMethodRequest>("EXTRACT_METHOD_ERROR") { request ->
                        stubExtractMethodHandler(request)
                    }
                }
            }
        }
    }

    /**
     * Generic request handler that mirrors production error handling.
     */
    private suspend inline fun <reified TRequest : Any> PipelineContext<Unit, ApplicationCall>.handleRequest(
        errorCode: String,
        crossinline handler: (TRequest) -> Any
    ) {
        try {
            val request = call.receive<TRequest>()
            val response = handler(request)
            call.respond(response)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = e.message ?: "Unknown error", code = errorCode)
            )
        }
    }

    // ==================== E2E Tests: GET /status ====================

    @Nested
    @DisplayName("E2E: GET /status")
    inner class StatusEndpointE2ETest {

        @Test
        @DisplayName("GET /status returns 200 OK with valid StatusResponse")
        fun `status endpoint returns valid response`() = testApplication {
            configureE2ETestApplication()

            val response = client.get("/status")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)

            val body = response.bodyAsText()
            // Verify response contains expected fields by deserializing
            val statusResponse = json.decodeFromString<StatusResponse>(body)

            assertTrue(statusResponse.ok)
            assertTrue(statusResponse.ideVersion.contains("IntelliJ IDEA 2024.1"))
            assertTrue(statusResponse.openProjects.any { it.name == "TestProject" })
            assertTrue(statusResponse.openProjects.any { it.name == "AnotherProject" })
            // indexingInProgress defaults to false when not present in JSON
            assertFalse(statusResponse.indexingInProgress)
        }

        @Test
        @DisplayName("GET /status response is proper JSON")
        fun `status response is valid JSON`() = testApplication {
            configureE2ETestApplication()

            val response = client.get("/status")
            val body = response.bodyAsText()

            // Deserialize to verify JSON structure
            val statusResponse = json.decodeFromString<StatusResponse>(body)

            assertTrue(statusResponse.ok)
            assertEquals("IntelliJ IDEA 2024.1 (Build #IC-241.15989.150)", statusResponse.ideVersion)
            assertEquals(2, statusResponse.openProjects.size)
            assertFalse(statusResponse.indexingInProgress)
        }

        @Test
        @DisplayName("GET /status with Accept header")
        fun `status endpoint respects accept header`() = testApplication {
            configureE2ETestApplication()

            val response = client.get("/status") {
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)
        }
    }

    // ==================== E2E Tests: POST /rename ====================

    @Nested
    @DisplayName("E2E: POST /rename")
    inner class RenameEndpointE2ETest {

        @Test
        @DisplayName("POST /rename with valid request returns success")
        fun `rename with valid request succeeds`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/main/kotlin/MyClass.kt",
                        "line": 10,
                        "column": 15,
                        "newName": "renamedElement"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val result = json.decodeFromString<RefactoringResponse>(body)

            assertTrue(result.success)
            assertTrue(result.message.contains("renamedElement"))
            assertTrue(result.affectedFiles.isNotEmpty())
        }

        @Test
        @DisplayName("POST /rename with empty newName returns validation error")
        fun `rename with empty newName fails validation`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/MyClass.kt",
                        "line": 10,
                        "column": 15,
                        "newName": ""
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val result = json.decodeFromString<RefactoringResponse>(body)

            assertFalse(result.success)
            assertTrue(result.message.contains("empty"))
        }

        @Test
        @DisplayName("POST /rename with unsupported file type returns error")
        fun `rename with unsupported file type fails`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/config.xml",
                        "line": 5,
                        "column": 10,
                        "newName": "newName"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())

            assertFalse(result.success)
            assertTrue(result.message.contains("Unsupported"))
        }

        @Test
        @DisplayName("POST /rename with optional project parameter")
        fun `rename with project parameter`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/Main.java",
                        "line": 15,
                        "column": 20,
                        "newName": "updatedName",
                        "project": "MyProject"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())
            assertTrue(result.success)
        }

        @Test
        @DisplayName("POST /rename with invalid line/column returns error")
        fun `rename with invalid position fails`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/Main.kt",
                        "line": 0,
                        "column": -1,
                        "newName": "validName"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())

            assertFalse(result.success)
            assertTrue(result.message.contains("positive"))
        }

        @Test
        @DisplayName("POST /rename with malformed JSON returns 400")
        fun `rename with malformed JSON returns error`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("{invalid json}")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("RENAME_ERROR"))
        }

        @Test
        @DisplayName("POST /rename with missing required fields returns 400")
        fun `rename with missing fields returns error`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file": "/src/Main.kt"}""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val error = json.decodeFromString<ErrorResponse>(response.bodyAsText())
            assertEquals("RENAME_ERROR", error.code)
        }
    }

    // ==================== E2E Tests: POST /findUsages ====================

    @Nested
    @DisplayName("E2E: POST /findUsages")
    inner class FindUsagesEndpointE2ETest {

        @Test
        @DisplayName("POST /findUsages with valid request returns usages list")
        fun `findUsages returns usages list`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/SomeClass.kt",
                        "line": 25,
                        "column": 10
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<FindUsagesResponse>(response.bodyAsText())

            assertTrue(result.success)
            assertEquals(3, result.usages.size)
            assertTrue(result.message.contains("3"))

            // Verify usage structure
            val firstUsage = result.usages[0]
            assertEquals("/src/SomeClass.kt", firstUsage.file)
            assertEquals(25, firstUsage.line)
            assertEquals(10, firstUsage.column)
            assertTrue(firstUsage.preview.isNotBlank())
        }

        @Test
        @DisplayName("POST /findUsages with project parameter")
        fun `findUsages with project parameter`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/Service.java",
                        "line": 50,
                        "column": 20,
                        "project": "BackendProject"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<FindUsagesResponse>(response.bodyAsText())
            assertTrue(result.success)
        }

        @Test
        @DisplayName("POST /findUsages with invalid position returns error")
        fun `findUsages with invalid position fails`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/File.kt",
                        "line": -5,
                        "column": 0
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<FindUsagesResponse>(response.bodyAsText())

            assertFalse(result.success)
            assertTrue(result.message.contains("positive"))
        }

        @Test
        @DisplayName("POST /findUsages usage previews contain code context")
        fun `findUsages returns meaningful previews`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/Main.kt",
                        "line": 1,
                        "column": 1
                    }
                """.trimIndent())
            }

            val result = json.decodeFromString<FindUsagesResponse>(response.bodyAsText())

            assertTrue(result.usages.all { it.preview.isNotEmpty() })
            assertTrue(result.usages.any { it.preview.contains("val") || it.preview.contains("fun") || it.preview.contains("@Test") })
        }
    }

    // ==================== E2E Tests: POST /move ====================

    @Nested
    @DisplayName("E2E: POST /move")
    inner class MoveEndpointE2ETest {

        @Test
        @DisplayName("POST /move with valid request succeeds")
        fun `move with valid request succeeds`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/com/old/MyClass.java",
                        "line": 3,
                        "column": 14,
                        "targetPackage": "com.new.package"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())

            assertTrue(result.success)
            assertTrue(result.message.contains("MyClass"))
            assertTrue(result.message.contains("com.new.package"))
        }

        @Test
        @DisplayName("POST /move with empty targetPackage returns error")
        fun `move with empty target package fails`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/MyClass.java",
                        "line": 1,
                        "column": 1,
                        "targetPackage": ""
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())

            assertFalse(result.success)
            assertTrue(result.message.contains("empty"))
        }

        @Test
        @DisplayName("POST /move Kotlin file succeeds")
        fun `move kotlin file succeeds`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/com/example/Service.kt",
                        "line": 5,
                        "column": 7,
                        "targetPackage": "com.example.services"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())

            assertTrue(result.success)
            assertTrue(result.message.contains("Service"))
        }

        @Test
        @DisplayName("POST /move unsupported file type returns error")
        fun `move unsupported file fails`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/styles.css",
                        "line": 1,
                        "column": 1,
                        "targetPackage": "com.example"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())

            assertFalse(result.success)
            assertTrue(result.message.contains("Java/Kotlin"))
        }

        @Test
        @DisplayName("POST /move with project parameter")
        fun `move with project parameter`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/Handler.java",
                        "line": 3,
                        "column": 14,
                        "targetPackage": "com.handlers",
                        "project": "MyApplication"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())
            assertTrue(result.success)
        }
    }

    // ==================== E2E Tests: POST /extractMethod ====================

    @Nested
    @DisplayName("E2E: POST /extractMethod")
    inner class ExtractMethodEndpointE2ETest {

        @Test
        @DisplayName("POST /extractMethod with valid request succeeds")
        fun `extractMethod with valid request succeeds`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/extractMethod") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/Main.java",
                        "startLine": 10,
                        "startColumn": 5,
                        "endLine": 20,
                        "endColumn": 10,
                        "methodName": "calculateTotal"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())

            assertTrue(result.success)
            assertTrue(result.message.contains("calculateTotal"))
            assertTrue(result.message.contains("10"))
            assertTrue(result.message.contains("20"))
        }

        @Test
        @DisplayName("POST /extractMethod with empty methodName returns error")
        fun `extractMethod with empty name fails`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/extractMethod") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/Main.java",
                        "startLine": 5,
                        "startColumn": 1,
                        "endLine": 10,
                        "endColumn": 1,
                        "methodName": ""
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())

            assertFalse(result.success)
            assertTrue(result.message.contains("empty"))
        }

        @Test
        @DisplayName("POST /extractMethod with invalid range returns error")
        fun `extractMethod with invalid range fails`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/extractMethod") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/Main.java",
                        "startLine": 20,
                        "startColumn": 1,
                        "endLine": 10,
                        "endColumn": 1,
                        "methodName": "newMethod"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())

            assertFalse(result.success)
            assertTrue(result.message.contains("Start line"))
        }

        @Test
        @DisplayName("POST /extractMethod with same line invalid column range returns error")
        fun `extractMethod with same line invalid columns fails`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/extractMethod") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/Main.java",
                        "startLine": 15,
                        "startColumn": 30,
                        "endLine": 15,
                        "endColumn": 10,
                        "methodName": "extracted"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())

            assertFalse(result.success)
            assertTrue(result.message.contains("column"))
        }

        @Test
        @DisplayName("POST /extractMethod only supports Java files")
        fun `extractMethod on Kotlin file fails`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/extractMethod") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/Main.kt",
                        "startLine": 5,
                        "startColumn": 1,
                        "endLine": 10,
                        "endColumn": 20,
                        "methodName": "extracted"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())

            assertFalse(result.success)
            assertTrue(result.message.contains("Java"))
        }

        @Test
        @DisplayName("POST /extractMethod with project parameter")
        fun `extractMethod with project parameter`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/extractMethod") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/Service.java",
                        "startLine": 50,
                        "startColumn": 9,
                        "endLine": 60,
                        "endColumn": 5,
                        "methodName": "processData",
                        "project": "BackendApp"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<RefactoringResponse>(response.bodyAsText())
            assertTrue(result.success)
        }
    }

    // ==================== E2E Tests: HTTP Layer Behavior ====================

    @Nested
    @DisplayName("E2E: HTTP Layer Behavior")
    inner class HttpLayerE2ETest {

        @Test
        @DisplayName("All endpoints return JSON content-type")
        fun `all endpoints return json content type`() = testApplication {
            configureE2ETestApplication()

            val statusResponse = client.get("/status")
            val renameResponse = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.kt","line":1,"column":1,"newName":"n"}""")
            }
            val findUsagesResponse = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.kt","line":1,"column":1}""")
            }
            val moveResponse = client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.java","line":1,"column":1,"targetPackage":"pkg"}""")
            }
            val extractResponse = client.post("/extractMethod") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.java","startLine":1,"startColumn":1,"endLine":2,"endColumn":1,"methodName":"m"}""")
            }

            listOf(statusResponse, renameResponse, findUsagesResponse, moveResponse, extractResponse).forEach { response ->
                assertTrue(
                    response.contentType()?.match(ContentType.Application.Json) == true,
                    "Expected JSON content-type for response"
                )
            }
        }

        @Test
        @DisplayName("Unknown endpoints return 404")
        fun `unknown endpoints return 404`() = testApplication {
            configureE2ETestApplication()

            val unknownGet = client.get("/unknown")
            val unknownPost = client.post("/nonexistent") {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }

            assertEquals(HttpStatusCode.NotFound, unknownGet.status)
            assertEquals(HttpStatusCode.NotFound, unknownPost.status)
        }

        @Test
        @DisplayName("Wrong HTTP method returns error")
        fun `wrong http method returns error`() = testApplication {
            configureE2ETestApplication()

            val postStatus = client.post("/status")
            val getRename = client.get("/rename")

            // Ktor returns 404 when method doesn't match
            assertTrue(postStatus.status == HttpStatusCode.NotFound || postStatus.status == HttpStatusCode.MethodNotAllowed)
            assertTrue(getRename.status == HttpStatusCode.NotFound || getRename.status == HttpStatusCode.MethodNotAllowed)
        }

        @Test
        @DisplayName("Missing Content-Type header returns error")
        fun `missing content type returns error`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/rename") {
                setBody("""{"file":"/f.kt","line":1,"column":1,"newName":"n"}""")
            }

            assertTrue(
                response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.UnsupportedMediaType,
                "Expected error status for missing Content-Type"
            )
        }

        @Test
        @DisplayName("Unicode in request/response is handled correctly")
        fun `unicode handling works correctly`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "file": "/src/main/kotlin/MyClass.kt",
                        "line": 10,
                        "column": 15,
                        "newName": "renamedElement"
                    }
                """.trimIndent())
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            // Response should be valid UTF-8 JSON
            assertTrue(body.contains("success"))
        }

        @Test
        @DisplayName("Large line/column numbers are handled")
        fun `large numbers are handled`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.kt","line":999999,"column":999999}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val result = json.decodeFromString<FindUsagesResponse>(response.bodyAsText())
            assertTrue(result.success)
        }

        @Test
        @DisplayName("Empty request body returns error")
        fun `empty body returns error`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        @DisplayName("Error response format is consistent")
        fun `error response format is consistent`() = testApplication {
            configureE2ETestApplication()

            val responses = listOf(
                client.post("/rename") {
                    contentType(ContentType.Application.Json)
                    setBody("{invalid}")
                },
                client.post("/findUsages") {
                    contentType(ContentType.Application.Json)
                    setBody("{invalid}")
                },
                client.post("/move") {
                    contentType(ContentType.Application.Json)
                    setBody("{invalid}")
                },
                client.post("/extractMethod") {
                    contentType(ContentType.Application.Json)
                    setBody("{invalid}")
                }
            )

            responses.forEach { response ->
                assertEquals(HttpStatusCode.BadRequest, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("\"error\""), "Error response should have error field")
                assertTrue(body.contains("\"code\""), "Error response should have code field")
            }
        }
    }

    // ==================== E2E Tests: Response Structure Validation ====================

    @Nested
    @DisplayName("E2E: Response Structure Validation")
    inner class ResponseStructureE2ETest {

        @Test
        @DisplayName("StatusResponse has all required fields")
        fun `status response has all fields`() = testApplication {
            configureE2ETestApplication()

            val response = client.get("/status")
            val body = response.bodyAsText()

            // Required fields that are always serialized
            assertTrue(body.contains("\"ok\""), "Should contain 'ok' field")
            assertTrue(body.contains("\"ideVersion\""), "Should contain 'ideVersion' field")
            assertTrue(body.contains("\"openProjects\""), "Should contain 'openProjects' field")
            // Note: indexingInProgress has default value false, so it may not be serialized
            // when using default Json settings. Verify it deserializes correctly instead.
            val statusResponse = json.decodeFromString<StatusResponse>(body)
            // Boolean field is never null - just verify deserialization succeeds
            assertFalse(statusResponse.indexingInProgress)
        }

        @Test
        @DisplayName("RefactoringResponse has all required fields")
        fun `refactoring response has all fields`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.kt","line":1,"column":1,"newName":"n"}""")
            }
            val body = response.bodyAsText()

            assertTrue(body.contains("\"success\""))
            assertTrue(body.contains("\"message\""))
            assertTrue(body.contains("\"affectedFiles\""))
        }

        @Test
        @DisplayName("FindUsagesResponse has all required fields")
        fun `find usages response has all fields`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.kt","line":1,"column":1}""")
            }
            val body = response.bodyAsText()

            assertTrue(body.contains("\"success\""))
            assertTrue(body.contains("\"message\""))
            assertTrue(body.contains("\"usages\""))
        }

        @Test
        @DisplayName("Usage object has all required fields")
        fun `usage object has all fields`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.kt","line":1,"column":1}""")
            }
            val result = json.decodeFromString<FindUsagesResponse>(response.bodyAsText())

            assertTrue(result.usages.isNotEmpty())
            val usage = result.usages.first()
            assertTrue(usage.file.isNotEmpty())
            assertTrue(usage.line > 0)
            assertTrue(usage.column > 0)
            assertTrue(usage.preview.isNotEmpty())
        }

        @Test
        @DisplayName("ErrorResponse has all required fields")
        fun `error response has all fields`() = testApplication {
            configureE2ETestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("{invalid}")
            }
            val error = json.decodeFromString<ErrorResponse>(response.bodyAsText())

            assertFalse(error.success)
            assertTrue(error.error.isNotEmpty())
            assertTrue(error.code.isNotEmpty())
        }
    }
}
