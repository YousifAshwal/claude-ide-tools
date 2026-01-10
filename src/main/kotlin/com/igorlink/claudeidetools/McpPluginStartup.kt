package com.igorlink.claudeidetools

import com.igorlink.claudeidetools.services.McpAutoRegistrationService
import com.igorlink.claudeidetools.services.McpHttpServerService
import com.igorlink.claudeidetools.util.IdeDetector
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger

/**
 * Plugin startup listener that initializes the MCP Bridge when the IDE launches.
 *
 * This listener is registered via `plugin.xml` and performs two main tasks:
 * 1. Starts the HTTP server on the IDE-specific port for refactoring requests
 * 2. Registers the MCP servers with Claude Code configuration (in background)
 *
 * ## Lifecycle
 * - **appFrameCreated**: Called when IDE window is created - starts server and registration
 * - **appWillBeClosed**: Called before IDE closes - stops the HTTP server
 *
 * ## Thread Safety
 * - Server is started via [McpHttpServerService] which handles synchronization
 * - MCP registration runs on a pooled thread to avoid blocking startup
 *
 * @see McpHttpServerService Manages HTTP server lifecycle
 * @see McpAutoRegistrationService Handles Claude Code configuration updates
 */
class McpPluginStartup : AppLifecycleListener {
    private val logger = Logger.getInstance(McpPluginStartup::class.java)

    /**
     * Called when the IDE window frame is created.
     *
     * Initializes the plugin by:
     * 1. Starting the HTTP server on the appropriate port
     * 2. Triggering MCP server registration in the background
     *
     * @param commandLineArgs Command-line arguments passed to the IDE (unused)
     */
    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        val ide = IdeDetector.detect()
        logger.info("Claude Tools: Initializing in ${ide.displayName}...")

        // Start HTTP server
        startHttpServer()

        // Auto-register with Claude Code (in background)
        ApplicationManager.getApplication().executeOnPooledThread {
            registerMcpServer()
        }
    }

    /**
     * Starts the HTTP server via [McpHttpServerService].
     *
     * The service handles server creation, lifecycle management, and
     * proper cleanup on plugin unload.
     */
    private fun startHttpServer() {
        val serverService = service<McpHttpServerService>()
        serverService.start()
    }

    /**
     * Registers the MCP servers with Claude Code configuration.
     *
     * Extracts bundled server files and updates Claude Code's config file
     * to include the MCP server entries. Shows appropriate notifications
     * based on the registration result.
     */
    private fun registerMcpServer() {
        try {
            val registrationService = service<McpAutoRegistrationService>()
            val result = registrationService.ensureRegistered()
            logger.info("Claude Tools: Registration result: $result")
            registrationService.showRegistrationNotification(result)
        } catch (e: Exception) {
            logger.error("Claude Tools: Error during MCP registration", e)
        }
    }

    /**
     * Called when the IDE is about to close.
     *
     * Stops the HTTP server to clean up resources and release the port.
     * Note: The server is also stopped by [McpHttpServerService.dispose]
     * when the plugin is unloaded, but we stop it here explicitly for
     * graceful shutdown during IDE close.
     *
     * @param isRestart `true` if the IDE is restarting, `false` if shutting down
     */
    override fun appWillBeClosed(isRestart: Boolean) {
        logger.info("Claude Tools: IDE closing, stopping HTTP server...")
        try {
            val serverService = service<McpHttpServerService>()
            serverService.stop()
        } catch (e: Exception) {
            logger.error("Claude Tools: Error stopping HTTP server", e)
        }
    }
}
