package com.igorlink.claudejetbrainstools.server

import com.igorlink.claudejetbrainstools.model.*
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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for McpHttpServer routing and request handling.
 *
 * Tests cover:
 * 1. GET /status routing
 * 2. POST endpoints routing (/rename, /findUsages, /move, /extractMethod)
 * 3. Malformed JSON handling
 * 4. Unknown routes (404)
 * 5. Content-Type validation
 *
 * These tests use Ktor's testApplication to test the routing logic independently
 * from the actual IntelliJ handlers. We mock the handlers by providing stub implementations
 * that return predictable responses.
 */
class McpHttpServerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * Creates a test application with the same routing configuration as McpHttpServer,
     * but with stub handlers for testing purposes.
     */
    private fun ApplicationTestBuilder.configureTestApplication(
        statusHandler: () -> StatusResponse = { defaultStatusResponse() },
        renameHandler: (RenameRequest) -> RefactoringResponse = { defaultRefactoringResponse("rename") },
        findUsagesHandler: (FindUsagesRequest) -> FindUsagesResponse = { defaultFindUsagesResponse() },
        moveHandler: (MoveRequest) -> RefactoringResponse = { defaultRefactoringResponse("move") },
        extractMethodHandler: (ExtractMethodRequest) -> RefactoringResponse = { defaultRefactoringResponse("extractMethod") }
    ) {
        application {
            install(ContentNegotiation) {
                json(json)
            }

            routing {
                // Health check / status endpoint
                get("/status") {
                    val response = statusHandler()
                    call.respond(response)
                }

                // Rename refactoring
                post("/rename") {
                    handleTestRequest<RenameRequest>("RENAME_ERROR") { request ->
                        renameHandler(request)
                    }
                }

                // Find usages
                post("/findUsages") {
                    handleTestRequest<FindUsagesRequest>("FIND_USAGES_ERROR") { request ->
                        findUsagesHandler(request)
                    }
                }

                // Move class/package
                post("/move") {
                    handleTestRequest<MoveRequest>("MOVE_ERROR") { request ->
                        moveHandler(request)
                    }
                }

                // Extract method
                post("/extractMethod") {
                    handleTestRequest<ExtractMethodRequest>("EXTRACT_METHOD_ERROR") { request ->
                        extractMethodHandler(request)
                    }
                }
            }
        }
    }

    /**
     * Generic request handler that mirrors the production handleRequest logic.
     */
    private suspend inline fun <reified TRequest : Any> PipelineContext<Unit, ApplicationCall>.handleTestRequest(
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

    // ==================== Default Response Factories ====================

    private fun defaultStatusResponse() = StatusResponse(
        ok = true,
        ideVersion = "IntelliJ IDEA 2024.1",
        openProjects = listOf(ProjectInfo("TestProject", "/path/to/TestProject")),
        indexingInProgress = false
    )

    private fun defaultRefactoringResponse(operation: String) = RefactoringResponse(
        success = true,
        message = "$operation completed successfully",
        affectedFiles = listOf("/path/to/File.kt")
    )

    private fun defaultFindUsagesResponse() = FindUsagesResponse(
        success = true,
        message = "Found 2 usages",
        usages = listOf(
            Usage("/path/to/File1.kt", 10, 5, "usage preview 1"),
            Usage("/path/to/File2.kt", 20, 10, "usage preview 2")
        )
    )

    // ==================== GET /status Tests ====================

    @Nested
    inner class StatusEndpointTest {

        @Test
        fun `GET status returns 200 and StatusResponse`() = testApplication {
            configureTestApplication()

            val response = client.get("/status")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"ok\""))
            assertTrue(body.contains("\"ideVersion\""))
            assertTrue(body.contains("\"openProjects\""))
        }

        @Test
        fun `GET status returns correct content-type`() = testApplication {
            configureTestApplication()

            val response = client.get("/status")

            assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)
        }

        @Test
        fun `GET status returns custom status response`() = testApplication {
            val customResponse = StatusResponse(
                ok = true,
                ideVersion = "PyCharm 2024.2",
                openProjects = listOf(
                    ProjectInfo("Project1", "/path/to/Project1"),
                    ProjectInfo("Project2", "/path/to/Project2"),
                    ProjectInfo("Project3", "/path/to/Project3")
                ),
                indexingInProgress = true
            )
            configureTestApplication(statusHandler = { customResponse })

            val response = client.get("/status")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("PyCharm 2024.2"))
            assertTrue(body.contains("Project1"))
            assertTrue(body.contains("Project2"))
            assertTrue(body.contains("Project3"))
            assertTrue(body.contains("\"indexingInProgress\"") && body.contains("true"))
        }

        @Test
        fun `GET status with empty projects list`() = testApplication {
            val emptyProjectsResponse = StatusResponse(
                ok = true,
                ideVersion = "IDEA 2024.1",
                openProjects = emptyList(),
                indexingInProgress = false
            )
            configureTestApplication(statusHandler = { emptyProjectsResponse })

            val response = client.get("/status")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            // Deserialize and verify empty list
            val decoded = Json.decodeFromString<StatusResponse>(body)
            assertTrue(decoded.openProjects.isEmpty(), "Expected empty openProjects list")
        }

        @Test
        fun `POST status returns 405 Method Not Allowed`() = testApplication {
            configureTestApplication()

            val response = client.post("/status")

            // Ktor returns 404 for route not found when method doesn't match
            // This is expected behavior as POST /status is not defined
            assertTrue(response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.MethodNotAllowed)
        }
    }

    // ==================== POST /rename Tests ====================

    @Nested
    inner class RenameEndpointTest {

        @Test
        fun `POST rename with valid request returns 200`() = testApplication {
            configureTestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/path/to/File.kt","line":10,"column":5,"newName":"newMethodName"}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"success\""))
            assertTrue(body.contains("true"))
            assertTrue(body.contains("rename completed successfully"))
        }

        @Test
        fun `POST rename passes request to handler`() = testApplication {
            var capturedRequest: RenameRequest? = null
            configureTestApplication(
                renameHandler = { request ->
                    capturedRequest = request
                    defaultRefactoringResponse("rename")
                }
            )

            client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/src/Main.kt","line":42,"column":15,"newName":"renamedSymbol","project":"MyProject"}""")
            }

            assertNotNull(capturedRequest)
            assertEquals("/src/Main.kt", capturedRequest?.file)
            assertEquals(42, capturedRequest?.line)
            assertEquals(15, capturedRequest?.column)
            assertEquals("renamedSymbol", capturedRequest?.newName)
            assertEquals("MyProject", capturedRequest?.project)
        }

        @Test
        fun `POST rename with optional project null`() = testApplication {
            var capturedRequest: RenameRequest? = null
            configureTestApplication(
                renameHandler = { request ->
                    capturedRequest = request
                    defaultRefactoringResponse("rename")
                }
            )

            client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/path/File.kt","line":1,"column":1,"newName":"name"}""")
            }

            assertNotNull(capturedRequest)
            assertNull(capturedRequest?.project)
        }

        @Test
        fun `POST rename returns handler response`() = testApplication {
            val customResponse = RefactoringResponse(
                success = true,
                message = "Renamed 'oldName' to 'newName'",
                affectedFiles = listOf("/a.kt", "/b.kt", "/c.kt")
            )
            configureTestApplication(renameHandler = { customResponse })

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.kt","line":1,"column":1,"newName":"n"}""")
            }

            val body = response.bodyAsText()
            assertTrue(body.contains("Renamed 'oldName' to 'newName'"))
            assertTrue(body.contains("/a.kt"))
            assertTrue(body.contains("/b.kt"))
            assertTrue(body.contains("/c.kt"))
        }

        @Test
        fun `GET rename returns error status`() = testApplication {
            configureTestApplication()

            val response = client.get("/rename")

            // Ktor may return 404 (Not Found) or 405 (Method Not Allowed) depending on routing configuration
            assertTrue(
                response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.MethodNotAllowed,
                "Expected 404 or 405 but got ${response.status}"
            )
        }
    }

    // ==================== POST /findUsages Tests ====================

    @Nested
    inner class FindUsagesEndpointTest {

        @Test
        fun `POST findUsages with valid request returns 200`() = testApplication {
            configureTestApplication()

            val response = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/path/to/File.kt","line":25,"column":10}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"success\""))
            assertTrue(body.contains("true"))
            assertTrue(body.contains("\"usages\""))
        }

        @Test
        fun `POST findUsages passes request to handler`() = testApplication {
            var capturedRequest: FindUsagesRequest? = null
            configureTestApplication(
                findUsagesHandler = { request ->
                    capturedRequest = request
                    defaultFindUsagesResponse()
                }
            )

            client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/src/Service.java","line":100,"column":20,"project":"Backend"}""")
            }

            assertNotNull(capturedRequest)
            assertEquals("/src/Service.java", capturedRequest?.file)
            assertEquals(100, capturedRequest?.line)
            assertEquals(20, capturedRequest?.column)
            assertEquals("Backend", capturedRequest?.project)
        }

        @Test
        fun `POST findUsages returns usages list`() = testApplication {
            val customResponse = FindUsagesResponse(
                success = true,
                message = "Found 3 usages",
                usages = listOf(
                    Usage("/file1.kt", 10, 5, "val x = method()"),
                    Usage("/file2.kt", 20, 10, "method().chain()"),
                    Usage("/file3.kt", 30, 15, "override fun method()")
                )
            )
            configureTestApplication(findUsagesHandler = { customResponse })

            val response = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.kt","line":1,"column":1}""")
            }

            val body = response.bodyAsText()
            assertTrue(body.contains("Found 3 usages"))
            assertTrue(body.contains("val x = method()"))
            assertTrue(body.contains("method().chain()"))
            assertTrue(body.contains("override fun method()"))
        }
    }

    // ==================== POST /move Tests ====================

    @Nested
    inner class MoveEndpointTest {

        @Test
        fun `POST move with valid request returns 200`() = testApplication {
            configureTestApplication()

            val response = client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/src/com/old/MyClass.java","line":5,"column":14,"targetPackage":"com.new.pkg"}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"success\""))
            assertTrue(body.contains("true"))
        }

        @Test
        fun `POST move passes request to handler`() = testApplication {
            var capturedRequest: MoveRequest? = null
            configureTestApplication(
                moveHandler = { request ->
                    capturedRequest = request
                    defaultRefactoringResponse("move")
                }
            )

            client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/src/com/example/Service.kt","line":3,"column":7,"targetPackage":"com.example.services","project":"MyApp"}""")
            }

            assertNotNull(capturedRequest)
            assertEquals("/src/com/example/Service.kt", capturedRequest?.file)
            assertEquals(3, capturedRequest?.line)
            assertEquals(7, capturedRequest?.column)
            assertEquals("com.example.services", capturedRequest?.targetPackage)
            assertEquals("MyApp", capturedRequest?.project)
        }

        @Test
        fun `POST move with empty target package`() = testApplication {
            var capturedRequest: MoveRequest? = null
            configureTestApplication(
                moveHandler = { request ->
                    capturedRequest = request
                    defaultRefactoringResponse("move")
                }
            )

            client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.kt","line":1,"column":1,"targetPackage":""}""")
            }

            assertNotNull(capturedRequest)
            assertEquals("", capturedRequest?.targetPackage)
        }
    }

    // ==================== POST /extractMethod Tests ====================

    @Nested
    inner class ExtractMethodEndpointTest {

        @Test
        fun `POST extractMethod with valid request returns 200`() = testApplication {
            configureTestApplication()

            val response = client.post("/extractMethod") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/src/Main.java","startLine":10,"startColumn":5,"endLine":20,"endColumn":10,"methodName":"extractedMethod"}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"success\""))
            assertTrue(body.contains("true"))
        }

        @Test
        fun `POST extractMethod passes request to handler`() = testApplication {
            var capturedRequest: ExtractMethodRequest? = null
            configureTestApplication(
                extractMethodHandler = { request ->
                    capturedRequest = request
                    defaultRefactoringResponse("extractMethod")
                }
            )

            client.post("/extractMethod") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/src/Service.kt","startLine":15,"startColumn":9,"endLine":25,"endColumn":5,"methodName":"calculateTotal","project":"Shop"}""")
            }

            assertNotNull(capturedRequest)
            assertEquals("/src/Service.kt", capturedRequest?.file)
            assertEquals(15, capturedRequest?.startLine)
            assertEquals(9, capturedRequest?.startColumn)
            assertEquals(25, capturedRequest?.endLine)
            assertEquals(5, capturedRequest?.endColumn)
            assertEquals("calculateTotal", capturedRequest?.methodName)
            assertEquals("Shop", capturedRequest?.project)
        }

        @Test
        fun `POST extractMethod with same start and end position`() = testApplication {
            var capturedRequest: ExtractMethodRequest? = null
            configureTestApplication(
                extractMethodHandler = { request ->
                    capturedRequest = request
                    defaultRefactoringResponse("extractMethod")
                }
            )

            client.post("/extractMethod") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.kt","startLine":5,"startColumn":10,"endLine":5,"endColumn":10,"methodName":"single"}""")
            }

            assertNotNull(capturedRequest)
            assertEquals(capturedRequest?.startLine, capturedRequest?.endLine)
            assertEquals(capturedRequest?.startColumn, capturedRequest?.endColumn)
        }
    }

    // ==================== Malformed JSON Tests ====================

    @Nested
    inner class MalformedJsonTest {

        @Test
        fun `POST rename with invalid JSON returns 400`() = testApplication {
            configureTestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                // Send JSON with wrong type for line field (string instead of integer)
                setBody("""{"file": "/path", "line": "not a number", "column": 1, "newName": "test"}""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            // Error response should contain error information
            assertTrue(body.contains("error") || body.contains("RENAME_ERROR"), "Expected error in body: $body")
        }

        @Test
        fun `POST rename with malformed JSON syntax returns 400`() = testApplication {
            configureTestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{invalid json syntax""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"error\""))
        }

        @Test
        fun `POST rename with missing required fields returns 400`() = testApplication {
            configureTestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file": "/path"}""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("RENAME_ERROR"))
        }

        @Test
        fun `POST findUsages with invalid JSON returns 400 with correct error code`() = testApplication {
            configureTestApplication()

            val response = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""{"broken""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("FIND_USAGES_ERROR"))
        }

        @Test
        fun `POST move with invalid JSON returns 400 with correct error code`() = testApplication {
            configureTestApplication()

            val response = client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""not json at all""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("MOVE_ERROR"))
        }

        @Test
        fun `POST extractMethod with invalid JSON returns 400 with correct error code`() = testApplication {
            configureTestApplication()

            val response = client.post("/extractMethod") {
                contentType(ContentType.Application.Json)
                setBody("""{"startLine": null}""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("EXTRACT_METHOD_ERROR"))
        }

        @Test
        fun `POST with empty body returns 400`() = testApplication {
            configureTestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        fun `POST with array instead of object returns 400`() = testApplication {
            configureTestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""[{"file": "/f", "line": 1, "column": 1, "newName": "n"}]""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    // ==================== Unknown Routes (404) Tests ====================

    @Nested
    inner class UnknownRoutesTest {

        @Test
        fun `GET unknown route returns 404`() = testApplication {
            configureTestApplication()

            val response = client.get("/unknown")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        @Test
        fun `POST unknown route returns 404`() = testApplication {
            configureTestApplication()

            val response = client.post("/nonexistent") {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        @Test
        fun `GET root returns 404`() = testApplication {
            configureTestApplication()

            val response = client.get("/")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        @Test
        fun `case sensitive routes - Status vs status`() = testApplication {
            configureTestApplication()

            val lowerResponse = client.get("/status")
            val upperResponse = client.get("/Status")

            assertEquals(HttpStatusCode.OK, lowerResponse.status)
            assertEquals(HttpStatusCode.NotFound, upperResponse.status)
        }

        @Test
        fun `case sensitive routes - Rename vs rename`() = testApplication {
            configureTestApplication()

            val validResponse = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f","line":1,"column":1,"newName":"n"}""")
            }
            val invalidResponse = client.post("/Rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f","line":1,"column":1,"newName":"n"}""")
            }

            assertEquals(HttpStatusCode.OK, validResponse.status)
            assertEquals(HttpStatusCode.NotFound, invalidResponse.status)
        }

        @Test
        fun `trailing slash difference`() = testApplication {
            configureTestApplication()

            val withoutSlash = client.get("/status")
            val withSlash = client.get("/status/")

            assertEquals(HttpStatusCode.OK, withoutSlash.status)
            // Ktor default behavior: trailing slash is treated differently
            assertTrue(withSlash.status == HttpStatusCode.OK || withSlash.status == HttpStatusCode.NotFound)
        }

        @Test
        fun `similar but incorrect route names return 404`() = testApplication {
            configureTestApplication()

            val responses = listOf(
                client.get("/stat"),
                client.get("/statuss"),
                client.post("/renam") { contentType(ContentType.Application.Json); setBody("{}") },
                client.post("/findUsage") { contentType(ContentType.Application.Json); setBody("{}") },
                client.post("/moves") { contentType(ContentType.Application.Json); setBody("{}") },
                client.post("/extract-method") { contentType(ContentType.Application.Json); setBody("{}") }
            )

            responses.forEach { response ->
                assertEquals(HttpStatusCode.NotFound, response.status)
            }
        }
    }

    // ==================== Content-Type Validation Tests ====================

    @Nested
    inner class ContentTypeValidationTest {

        @Test
        fun `POST with application json content-type succeeds`() = testApplication {
            configureTestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f","line":1,"column":1,"newName":"n"}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `POST with text plain content-type fails`() = testApplication {
            configureTestApplication()

            val response = client.post("/rename") {
                contentType(ContentType.Text.Plain)
                setBody("""{"file":"/f","line":1,"column":1,"newName":"n"}""")
            }

            // Ktor Content Negotiation should reject non-JSON content types
            assertTrue(response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.UnsupportedMediaType)
        }

        @Test
        fun `POST with no content-type header`() = testApplication {
            configureTestApplication()

            val response = client.post("/rename") {
                setBody("""{"file":"/f","line":1,"column":1,"newName":"n"}""")
            }

            // Without Content-Type, Ktor may not know how to deserialize
            assertTrue(response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.UnsupportedMediaType)
        }

        @Test
        fun `POST with application json charset utf-8 succeeds`() = testApplication {
            configureTestApplication()

            val response = client.post("/rename") {
                header(HttpHeaders.ContentType, "application/json; charset=utf-8")
                setBody("""{"file":"/f","line":1,"column":1,"newName":"n"}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
        }

        @Test
        fun `POST with application xml content-type fails`() = testApplication {
            configureTestApplication()

            val response = client.post("/findUsages") {
                contentType(ContentType.Application.Xml)
                setBody("<request><file>/f</file></request>")
            }

            assertTrue(response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.UnsupportedMediaType)
        }

        @Test
        fun `response content-type is application json`() = testApplication {
            configureTestApplication()

            val statusResponse = client.get("/status")
            val renameResponse = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f","line":1,"column":1,"newName":"n"}""")
            }

            assertTrue(statusResponse.contentType()?.match(ContentType.Application.Json) == true)
            assertTrue(renameResponse.contentType()?.match(ContentType.Application.Json) == true)
        }
    }

    // ==================== Handler Exception Tests ====================

    @Nested
    inner class HandlerExceptionTest {

        @Test
        fun `handler throwing exception returns 400 with error response`() = testApplication {
            configureTestApplication(
                renameHandler = { throw RuntimeException("Something went wrong in handler") }
            )

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f","line":1,"column":1,"newName":"n"}""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Something went wrong in handler"))
            assertTrue(body.contains("RENAME_ERROR"))
        }

        @Test
        fun `handler throwing IllegalArgumentException returns error message`() = testApplication {
            configureTestApplication(
                findUsagesHandler = { throw IllegalArgumentException("Invalid file path") }
            )

            val response = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f","line":1,"column":1}""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Invalid file path"))
        }

        @Test
        fun `handler throwing exception with null message returns Unknown error`() = testApplication {
            configureTestApplication(
                moveHandler = { throw RuntimeException() }
            )

            val response = client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f","line":1,"column":1,"targetPackage":"pkg"}""")
            }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Unknown error"))
        }

        @Test
        fun `each endpoint has its own error code`() = testApplication {
            configureTestApplication(
                renameHandler = { throw RuntimeException("error") },
                findUsagesHandler = { throw RuntimeException("error") },
                moveHandler = { throw RuntimeException("error") },
                extractMethodHandler = { throw RuntimeException("error") }
            )

            val renameResponse = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f","line":1,"column":1,"newName":"n"}""")
            }
            val findUsagesResponse = client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f","line":1,"column":1}""")
            }
            val moveResponse = client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f","line":1,"column":1,"targetPackage":"pkg"}""")
            }
            val extractMethodResponse = client.post("/extractMethod") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f","startLine":1,"startColumn":1,"endLine":2,"endColumn":2,"methodName":"m"}""")
            }

            assertTrue(renameResponse.bodyAsText().contains("RENAME_ERROR"))
            assertTrue(findUsagesResponse.bodyAsText().contains("FIND_USAGES_ERROR"))
            assertTrue(moveResponse.bodyAsText().contains("MOVE_ERROR"))
            assertTrue(extractMethodResponse.bodyAsText().contains("EXTRACT_METHOD_ERROR"))
        }
    }

    // ==================== Edge Cases Tests ====================

    @Nested
    inner class EdgeCasesTest {

        @Test
        fun `handles unicode in request body`() = testApplication {
            var capturedRequest: RenameRequest? = null
            configureTestApplication(
                renameHandler = { request ->
                    capturedRequest = request
                    defaultRefactoringResponse("rename")
                }
            )

            client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/путь/к/Файл.kt","line":1,"column":1,"newName":"новоеИмя"}""")
            }

            assertNotNull(capturedRequest)
            assertEquals("/путь/к/Файл.kt", capturedRequest?.file)
            assertEquals("новоеИмя", capturedRequest?.newName)
        }

        @Test
        fun `handles special characters in file path`() = testApplication {
            var capturedRequest: RenameRequest? = null
            configureTestApplication(
                renameHandler = { request ->
                    capturedRequest = request
                    defaultRefactoringResponse("rename")
                }
            )

            client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/path/with spaces/and\"quotes/File.kt","line":1,"column":1,"newName":"name"}""")
            }

            assertNotNull(capturedRequest)
            assertTrue(capturedRequest?.file?.contains("spaces") == true)
            assertTrue(capturedRequest?.file?.contains("quotes") == true)
        }

        @Test
        fun `handles large line and column numbers`() = testApplication {
            var capturedRequest: FindUsagesRequest? = null
            configureTestApplication(
                findUsagesHandler = { request ->
                    capturedRequest = request
                    defaultFindUsagesResponse()
                }
            )

            client.post("/findUsages") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.kt","line":${Int.MAX_VALUE},"column":${Int.MAX_VALUE}}""")
            }

            assertNotNull(capturedRequest)
            assertEquals(Int.MAX_VALUE, capturedRequest?.line)
            assertEquals(Int.MAX_VALUE, capturedRequest?.column)
        }

        @Test
        fun `handles extra whitespace in JSON`() = testApplication {
            var capturedRequest: RenameRequest? = null
            configureTestApplication(
                renameHandler = { request ->
                    capturedRequest = request
                    defaultRefactoringResponse("rename")
                }
            )

            val jsonWithWhitespace = """
                {
                    "file"    :    "/path/to/File.kt"   ,
                    "line"    :    10   ,
                    "column"  :    5    ,
                    "newName" :    "newName"
                }
            """.trimIndent()

            client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody(jsonWithWhitespace)
            }

            assertNotNull(capturedRequest)
            assertEquals("/path/to/File.kt", capturedRequest?.file)
        }

        @Test
        fun `handles Windows-style paths`() = testApplication {
            var capturedRequest: MoveRequest? = null
            configureTestApplication(
                moveHandler = { request ->
                    capturedRequest = request
                    defaultRefactoringResponse("move")
                }
            )

            client.post("/move") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"C:\\Users\\Dev\\Project\\src\\Main.kt","line":1,"column":1,"targetPackage":"com.new"}""")
            }

            assertNotNull(capturedRequest)
            assertEquals("C:\\Users\\Dev\\Project\\src\\Main.kt", capturedRequest?.file)
        }

        @Test
        fun `ignores unknown keys in request`() = testApplication {
            var capturedRequest: RenameRequest? = null
            configureTestApplication(
                renameHandler = { request ->
                    capturedRequest = request
                    defaultRefactoringResponse("rename")
                }
            )

            val response = client.post("/rename") {
                contentType(ContentType.Application.Json)
                setBody("""{"file":"/f.kt","line":1,"column":1,"newName":"n","unknownKey":"value","anotherUnknown":123}""")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertNotNull(capturedRequest)
            assertEquals("/f.kt", capturedRequest?.file)
        }
    }
}
