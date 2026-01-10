package com.igorlink.claudeidetools.server

import com.igorlink.claudeidetools.handlers.ApplyFixHandler
import com.igorlink.claudeidetools.handlers.DiagnosticsHandler
import com.igorlink.claudeidetools.handlers.ExtractMethodHandler
import com.igorlink.claudeidetools.handlers.FindUsagesHandler
import com.igorlink.claudeidetools.handlers.MoveHandler
import com.igorlink.claudeidetools.handlers.RenameHandler
import com.igorlink.claudeidetools.handlers.StatusHandler
import com.igorlink.claudeidetools.model.ApplyFixRequest
import com.igorlink.claudeidetools.model.DiagnosticsRequest
import com.igorlink.claudeidetools.model.ErrorResponse
import com.igorlink.claudeidetools.model.ExtractMethodRequest
import com.igorlink.claudeidetools.model.FindUsagesRequest
import com.igorlink.claudeidetools.model.MoveRequest
import com.igorlink.claudeidetools.model.RenameRequest
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.json.Json
import com.intellij.openapi.diagnostic.Logger

/**
 * HTTP server that exposes refactoring API endpoints for Claude Code integration.
 *
 * This server provides a REST API that allows Claude Code (via MCP servers) to perform
 * IDE refactoring operations. It binds exclusively to localhost (127.0.0.1) for security,
 * preventing external access to the refactoring endpoints.
 *
 * ## Endpoints
 * - `GET /status` - Health check and IDE information
 * - `POST /rename` - Rename symbol refactoring
 * - `POST /findUsages` - Find all usages of a symbol
 * - `POST /move` - Move class/symbol to different package
 * - `POST /extractMethod` - Extract code block into a new method
 * - `POST /diagnostics` - Get code analysis diagnostics (errors, warnings, etc.)
 * - `POST /applyFix` - Apply a quick fix action to resolve a diagnostic
 *
 * ## Lifecycle
 * The server is started automatically when the IDE launches via [McpPluginStartup]
 * and stopped when the IDE closes. Each JetBrains IDE uses a unique port to allow
 * multiple IDEs to run simultaneously.
 *
 * ## Usage Example
 * ```kotlin
 * val server = McpHttpServer(port = 8765)
 * server.start()
 * // ... server handles requests ...
 * server.stop()
 * ```
 *
 * @param port The port to bind the server to (each IDE has a unique port)
 * @see McpPluginStartup Manages server lifecycle
 * @see IdeDetector.getPort Returns the appropriate port for the current IDE
 */
class McpHttpServer(private val port: Int) {
    private val logger = Logger.getInstance(McpHttpServer::class.java)
    private var engine: ApplicationEngine? = null

    /**
     * Generic request handler that encapsulates the common try-catch error handling pattern.
     *
     * This inline function reduces boilerplate by handling request deserialization,
     * error catching, and response formatting in a consistent way across all endpoints.
     *
     * @param TRequest The type of request body expected (must be JSON serializable)
     * @param endpointName Name of the endpoint for logging purposes (e.g., "/rename")
     * @param errorCode Error code to include in [ErrorResponse] on failure
     * @param handler Business logic handler that processes the request and returns a response object
     */
    private suspend inline fun <reified TRequest : Any> PipelineContext<Unit, ApplicationCall>.handleRequest(
        endpointName: String,
        errorCode: String,
        crossinline handler: (TRequest) -> Any
    ) {
        logger.debug("Handling $endpointName request")
        try {
            val request = call.receive<TRequest>()
            val response = handler(request)
            call.respond(response)
        } catch (e: Exception) {
            logger.error("Error handling $endpointName", e)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = e.message ?: "Unknown error", code = errorCode)
            )
        }
    }

    /**
     * Starts the HTTP server and begins accepting connections.
     *
     * The server runs on a background thread (non-blocking) and binds to
     * localhost only for security. Once started, the server will handle
     * incoming refactoring requests until [stop] is called.
     *
     * The server configures:
     * - JSON content negotiation with pretty printing
     * - Routes for status, rename, findUsages, move, and extractMethod endpoints
     *
     * @throws Exception if the server cannot bind to the specified port
     */
    fun start() {
        engine = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }

            routing {
                // Health check / status endpoint
                get("/status") {
                    logger.debug("Handling /status request")
                    val response = StatusHandler.handle()
                    call.respond(response)
                }

                // Rename refactoring
                post("/rename") {
                    handleRequest<RenameRequest>("/rename", "RENAME_ERROR") { request ->
                        RenameHandler.handle(request)
                    }
                }

                // Find usages
                post("/findUsages") {
                    handleRequest<FindUsagesRequest>("/findUsages", "FIND_USAGES_ERROR") { request ->
                        FindUsagesHandler.handle(request)
                    }
                }

                // Move class/package
                post("/move") {
                    handleRequest<MoveRequest>("/move", "MOVE_ERROR") { request ->
                        MoveHandler.handle(request)
                    }
                }

                // Extract method
                post("/extractMethod") {
                    handleRequest<ExtractMethodRequest>("/extractMethod", "EXTRACT_METHOD_ERROR") { request ->
                        ExtractMethodHandler.handle(request)
                    }
                }

                // Diagnostics (errors, warnings, etc.)
                post("/diagnostics") {
                    handleRequest<DiagnosticsRequest>("/diagnostics", "DIAGNOSTICS_ERROR") { request ->
                        DiagnosticsHandler.handle(request)
                    }
                }

                // Apply quick fix
                post("/applyFix") {
                    handleRequest<ApplyFixRequest>("/applyFix", "APPLY_FIX_ERROR") { request ->
                        ApplyFixHandler.handle(request)
                    }
                }
            }
        }.start(wait = false)
    }

    /**
     * Stops the HTTP server gracefully.
     *
     * Allows up to 1 second for graceful shutdown and up to 2 seconds for
     * forced shutdown. After calling this method, the server will no longer
     * accept new connections.
     *
     * Safe to call multiple times - subsequent calls have no effect if the
     * server is already stopped.
     */
    fun stop() {
        engine?.stop(1000, 2000)
        engine = null
    }
}
