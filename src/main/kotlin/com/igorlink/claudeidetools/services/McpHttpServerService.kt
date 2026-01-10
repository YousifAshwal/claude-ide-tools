package com.igorlink.claudeidetools.services

import com.igorlink.claudeidetools.server.McpHttpServer
import com.igorlink.claudeidetools.util.IdeDetector
import com.igorlink.claudeidetools.util.NotificationHelper
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger

/**
 * Application service that manages the lifecycle of the MCP HTTP server.
 *
 * This service wraps [McpHttpServer] and integrates it with IntelliJ's
 * service lifecycle. When the plugin is unloaded (disabled, uninstalled,
 * or updated), the [dispose] method is automatically called by the platform,
 * ensuring the HTTP server is properly stopped and the port is released.
 *
 * ## Why This Exists
 * Without proper lifecycle management, dynamic plugin unloading would leave
 * the Netty server thread running, causing "Address already in use" errors
 * when the plugin is reloaded.
 *
 * ## Usage
 * ```kotlin
 * val serverService = service<McpHttpServerService>()
 * serverService.start()
 * // Server runs until plugin is unloaded or stop() is called
 * ```
 *
 * @see McpHttpServer The underlying HTTP server implementation
 */
class McpHttpServerService : Disposable {
    private val logger = Logger.getInstance(McpHttpServerService::class.java)

    private var server: McpHttpServer? = null
    private var isRunning = false

    /**
     * Starts the HTTP server on the IDE-specific port.
     *
     * Creates and starts an [McpHttpServer] instance bound to localhost
     * on the port assigned to the current IDE type. If the server is
     * already running, this method does nothing.
     *
     * @return `true` if the server was started successfully, `false` otherwise
     */
    @Synchronized
    fun start(): Boolean {
        if (isRunning) {
            logger.info("Claude Tools: HTTP server is already running")
            return true
        }

        val ide = IdeDetector.detect()
        val port = ide.port

        logger.info("Claude Tools: Starting HTTP server on port $port...")
        return try {
            server = McpHttpServer(port = port)
            server?.start()
            isRunning = true
            logger.info("Claude Tools: HTTP server started on port $port")
            NotificationHelper.info("Claude Tools started on port $port (${ide.displayName})")
            true
        } catch (e: Exception) {
            logger.error("Claude Tools: Failed to start HTTP server", e)
            NotificationHelper.error("Failed to start Claude Tools server: ${e.message}")
            false
        }
    }

    /**
     * Stops the HTTP server if it is running.
     *
     * Safe to call multiple times - subsequent calls have no effect
     * if the server is already stopped.
     */
    @Synchronized
    fun stop() {
        if (!isRunning) {
            logger.debug("Claude Tools: HTTP server is not running, nothing to stop")
            return
        }

        logger.info("Claude Tools: Stopping HTTP server...")
        try {
            server?.stop()
            server = null
            isRunning = false
            logger.info("Claude Tools: HTTP server stopped")
        } catch (e: Exception) {
            logger.error("Claude Tools: Error stopping HTTP server", e)
        }
    }

    /**
     * Returns whether the HTTP server is currently running.
     */
    fun isRunning(): Boolean = isRunning

    /**
     * Called by IntelliJ when the plugin is being unloaded.
     *
     * This ensures the HTTP server is stopped and the port is released,
     * preventing "Address already in use" errors on plugin reload.
     */
    override fun dispose() {
        logger.info("Claude Tools: Disposing McpHttpServerService...")
        stop()
    }
}
