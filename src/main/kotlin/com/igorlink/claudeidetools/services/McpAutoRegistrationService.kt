package com.igorlink.claudeidetools.services

import com.igorlink.claudeidetools.util.IdeDetector
import com.igorlink.claudeidetools.util.NotificationHelper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Service that automatically registers the MCP (Model Context Protocol) server with Claude Code CLI.
 *
 * This service is responsible for:
 * - Extracting bundled MCP server JavaScript files to the user's home directory
 * - Registering the servers using Claude Code CLI (`claude mcp add`)
 * - Managing server versions and updates
 * - Handling unregistration when the plugin is disabled (`claude mcp remove`)
 *
 * The plugin deploys two types of MCP servers:
 * 1. **Common server** (`claude-ide-tools`): Handles universal operations like status, rename, and find usages
 *    that work identically across all JetBrains IDEs
 * 2. **IDE-specific server** (e.g., `claude-idea-tools`): Handles operations like move and extract method
 *    that require IDE-specific port configuration
 *
 * ## Usage Example
 * ```kotlin
 * val service = service<McpAutoRegistrationService>()
 * val result = service.ensureRegistered()
 * service.showRegistrationNotification(result)
 * ```
 *
 * @see McpHttpServer The HTTP server that handles incoming refactoring requests
 * @see IdeDetector Utility for detecting the current IDE type and port
 */
@Service
class McpAutoRegistrationService {
    private val logger = Logger.getInstance(McpAutoRegistrationService::class.java)

    companion object {
        /** Resource path for the common MCP server JavaScript file bundled in the plugin. */
        const val COMMON_SERVER_RESOURCE = "/mcp-server/common-server.js"

        /** MCP server name used in Claude Code config for the common server. */
        const val COMMON_SERVER_NAME = "claude-ide-tools"

        /** Directory name in user's home for common server installation. */
        const val COMMON_INSTALL_DIR = ".claude-ide-tools"

        /** Filename for the common server JavaScript file. */
        const val COMMON_SERVER_FILE = "common-server.js"

        /** Resource path for the IDE-specific MCP server JavaScript file. */
        const val IDE_SERVER_RESOURCE = "/mcp-server/ide-server.js"

        /** Filename for the IDE-specific server JavaScript file. */
        const val IDE_SERVER_FILE = "ide-server.js"

        /** Filename for the version tracking file in installation directories. */
        const val VERSION_FILE_NAME = ".version"

        /**
         * Returns the current plugin version from version.properties resource.
         * This version is used to determine if MCP servers need to be re-extracted.
         *
         * @return The version string (e.g., "0.3.1") or "unknown" if resource not found
         */
        fun getCurrentVersion(): String {
            return try {
                McpAutoRegistrationService::class.java.getResourceAsStream("/version.properties")
                    ?.bufferedReader()
                    ?.readLine()
                    ?.substringAfter("version=")
                    ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }
        }

        /**
         * Returns the MCP server name for the current IDE (for IDE-specific server).
         *
         * The name follows the pattern `claude-{ide-short-name}-tools`, for example:
         * - IntelliJ IDEA: `claude-idea-tools`
         * - WebStorm: `claude-webstorm-tools`
         * - PyCharm: `claude-pycharm-tools`
         *
         * @return The MCP server name string for the current IDE
         */
        fun getIdeMcpServerName(): String = IdeDetector.getMcpServerName()

        /**
         * Returns the installation directory name for the current IDE.
         *
         * The directory is created in the user's home directory with the pattern `.claude-{ide}-tools`.
         *
         * @return The installation directory name (without path)
         */
        fun getIdeInstallDirName(): String = IdeDetector.detect().installDirName

        /**
         * Returns the short name for the current IDE.
         *
         * Used as a command-line argument to identify the IDE type.
         * Examples: "idea", "webstorm", "pycharm", "goland"
         *
         * @return The IDE short name string
         */
        fun getIdeShortName(): String = IdeDetector.detect().shortName

        /**
         * Returns the HTTP server port for the current IDE.
         *
         * Each JetBrains IDE uses a unique port to avoid conflicts:
         * - IntelliJ IDEA: 8765
         * - WebStorm: 8766
         * - PyCharm: 8767
         * - GoLand: 8768
         * - etc.
         *
         * @return The port number for the current IDE
         */
        fun getPort(): Int = IdeDetector.getPort()
    }

    /**
     * Result of an MCP server registration attempt.
     *
     * Used to communicate the outcome of [ensureRegistered] to the caller,
     * allowing appropriate user notification.
     */
    enum class RegistrationResult {
        /** First time registration - servers were newly added to Claude Code config. */
        NEWLY_REGISTERED,

        /** Servers were already registered with correct paths - no changes needed. */
        ALREADY_REGISTERED,

        /** Server paths were updated in the existing registration. */
        UPDATED,

        /** Registration failed due to an error (see logs for details). */
        FAILED
    }

    /**
     * Ensures both MCP servers are installed and registered with Claude Code.
     *
     * This method performs the following steps:
     * 1. Extracts the common server JS file to `~/.claude-ide-tools/`
     * 2. Extracts the IDE-specific server JS file to `~/.claude-{ide}-tools/`
     * 3. Updates Claude Code's configuration file to include both servers
     *
     * The method is idempotent - calling it multiple times with the same server version
     * will return [RegistrationResult.ALREADY_REGISTERED] without making changes.
     *
     * ## Server Configuration
     * - **Common server**: Runs without arguments, auto-discovers all IDE ports
     * - **IDE-specific server**: Runs with port and IDE name arguments
     *
     * @return [RegistrationResult] indicating the outcome of the registration attempt
     * @see showRegistrationNotification To display appropriate UI feedback based on result
     */
    fun ensureRegistered(): RegistrationResult {
        return try {
            val homeDir = System.getProperty("user.home")

            // Extract and register common server
            val commonExtraction = extractServer(
                homeDir = homeDir,
                installDirName = COMMON_INSTALL_DIR,
                serverFileName = COMMON_SERVER_FILE,
                resourcePath = COMMON_SERVER_RESOURCE
            )
            if (commonExtraction == null) {
                logger.warn("Failed to extract common MCP server")
                return RegistrationResult.FAILED
            }

            // Extract and register IDE-specific server
            val ideExtraction = extractServer(
                homeDir = homeDir,
                installDirName = getIdeInstallDirName(),
                serverFileName = IDE_SERVER_FILE,
                resourcePath = IDE_SERVER_RESOURCE
            )
            if (ideExtraction == null) {
                logger.warn("Failed to extract IDE-specific MCP server")
                return RegistrationResult.FAILED
            }

            // Register both servers in Claude config
            val configResult = registerBothServers(
                commonServerPath = commonExtraction.serverPath,
                ideServerPath = ideExtraction.serverPath
            )

            // If servers were updated but config didn't change, still notify user
            if (configResult == RegistrationResult.ALREADY_REGISTERED &&
                (commonExtraction.wasUpdated || ideExtraction.wasUpdated)) {
                RegistrationResult.UPDATED
            } else {
                configResult
            }
        } catch (e: Exception) {
            logger.error("Failed to register MCP servers", e)
            RegistrationResult.FAILED
        }
    }

    /**
     * Result of extracting an MCP server file to the filesystem.
     *
     * @property serverPath Absolute path to the extracted server JavaScript file
     * @property wasUpdated True if the server was updated from a previous version
     */
    private data class ExtractionResult(
        val serverPath: String,
        val wasUpdated: Boolean
    )

    /**
     * Extracts a bundled MCP server JavaScript file to the user's home directory.
     *
     * The server file is always overwritten to ensure the latest version is installed.
     * A version file is maintained alongside the server to track updates.
     *
     * @param homeDir The user's home directory path
     * @param installDirName The directory name to create in homeDir (e.g., ".claude-ide-tools")
     * @param serverFileName The name of the server file to create (e.g., "common-server.js")
     * @param resourcePath The classpath resource path to the bundled server file
     * @return [ExtractionResult] with the server path and update status, or null if extraction failed
     */
    private fun extractServer(
        homeDir: String,
        installDirName: String,
        serverFileName: String,
        resourcePath: String
    ): ExtractionResult? {
        val installDir = File(homeDir, installDirName)

        if (!installDir.exists()) {
            installDir.mkdirs()
        }

        val serverFile = File(installDir, serverFileName)
        val versionFile = File(installDir, VERSION_FILE_NAME)

        // Check if update is needed
        val installedVersion = if (versionFile.exists()) versionFile.readText().trim() else null
        val currentVersion = getCurrentVersion()
        val needsUpdate = installedVersion != currentVersion

        // Always overwrite server to ensure latest version
        val resourceStream = javaClass.getResourceAsStream(resourcePath)
        if (resourceStream == null) {
            logger.error("MCP server resource not found: $resourcePath")
            return null
        }

        resourceStream.use { input ->
            Files.copy(input, serverFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        // Write version file
        versionFile.writeText(currentVersion)

        logger.info("MCP server extracted to: ${serverFile.absolutePath}, updated: $needsUpdate")
        return ExtractionResult(serverFile.absolutePath, needsUpdate)
    }

    /**
     * Registers both MCP servers using Claude Code CLI.
     *
     * Uses `claude mcp add` command to register both the common and IDE-specific servers.
     *
     * @param commonServerPath Absolute path to the extracted common server JS file
     * @param ideServerPath Absolute path to the extracted IDE-specific server JS file
     * @return [RegistrationResult] indicating the outcome of the registration
     */
    private fun registerBothServers(
        commonServerPath: String,
        ideServerPath: String
    ): RegistrationResult {
        return try {
            val ideServerName = getIdeMcpServerName()
            val port = getPort()
            val ideShortName = getIdeShortName()

            // Register common server: claude mcp add -s user claude-ide-tools -- node <path>
            val commonResult = runClaudeMcpAdd(
                serverName = COMMON_SERVER_NAME,
                serverPath = commonServerPath,
                args = emptyList()
            )

            // Register IDE-specific server: claude mcp add -s user claude-idea-tools -- node <path> <port> <ide>
            val ideResult = runClaudeMcpAdd(
                serverName = ideServerName,
                serverPath = ideServerPath,
                args = listOf(port.toString(), ideShortName)
            )

            when {
                !commonResult.success || !ideResult.success -> RegistrationResult.FAILED
                commonResult.wasNew || ideResult.wasNew -> RegistrationResult.NEWLY_REGISTERED
                commonResult.wasUpdated || ideResult.wasUpdated -> RegistrationResult.UPDATED
                else -> RegistrationResult.ALREADY_REGISTERED
            }
        } catch (e: Exception) {
            logger.error("Failed to register MCP servers via CLI", e)
            RegistrationResult.FAILED
        }
    }

    /**
     * Result of running `claude mcp add` command.
     */
    private data class McpAddResult(
        val success: Boolean,
        val wasNew: Boolean = false,
        val wasUpdated: Boolean = false
    )

    /**
     * Registers an MCP server using Claude Code CLI.
     *
     * First removes any existing server with the same name to ensure fresh configuration,
     * then adds the server with the specified parameters.
     *
     * @param serverName The name for the MCP server
     * @param serverPath Absolute path to the server JS file
     * @param args Additional arguments for the server
     * @return [McpAddResult] indicating the outcome
     */
    private fun runClaudeMcpAdd(
        serverName: String,
        serverPath: String,
        args: List<String>
    ): McpAddResult {
        return try {
            val normalizedPath = serverPath.replace("\\", "/")
            val isWindows = System.getProperty("os.name").lowercase().contains("win")

            // First, remove existing server (if any) to ensure fresh config
            val removeResult = runClaudeMcpRemove(serverName)
            val existed = removeResult == McpRemoveResult.REMOVED

            // Build command: claude mcp add -s user <name> -- node <path> [args...]
            val command = mutableListOf<String>()
            if (isWindows) {
                command.addAll(listOf("cmd", "/c"))
            }
            command.addAll(listOf("claude", "mcp", "add", "-s", "user", serverName, "--", "node", normalizedPath))
            command.addAll(args)

            logger.info("Running: ${command.joinToString(" ")}")

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            when {
                exitCode == 0 -> {
                    logger.info("MCP server '$serverName' registered: $output")
                    // If server existed before, it's an update; otherwise it's new
                    McpAddResult(success = true, wasNew = !existed, wasUpdated = existed)
                }
                else -> {
                    logger.warn("Failed to add MCP server '$serverName': $output (exit code: $exitCode)")
                    McpAddResult(success = false)
                }
            }
        } catch (e: Exception) {
            logger.error("Error running claude mcp add for '$serverName'", e)
            McpAddResult(success = false)
        }
    }

    /**
     * Shows an appropriate IDE notification based on the registration result.
     *
     * - For [RegistrationResult.NEWLY_REGISTERED] and [RegistrationResult.UPDATED]:
     *   Shows an information notification asking to restart Claude Code CLI
     * - For [RegistrationResult.ALREADY_REGISTERED]: No notification (silent)
     * - For [RegistrationResult.FAILED]: Shows a warning notification
     *
     * @param result The registration result to display notification for
     */
    fun showRegistrationNotification(result: RegistrationResult) {
        when (result) {
            RegistrationResult.NEWLY_REGISTERED, RegistrationResult.UPDATED -> {
                NotificationHelper.info(
                    content = "MCP Bridge installed. Restart Claude Code CLI to start using refactoring tools.",
                    runOnEdt = true
                )
            }
            RegistrationResult.ALREADY_REGISTERED -> {
                // Don't show notification if already registered
                logger.info("MCP server already registered, no notification needed")
            }
            RegistrationResult.FAILED -> {
                NotificationHelper.warning(
                    content = "Failed to register MCP tools. Check IDE logs for details.",
                    runOnEdt = true
                )
            }
        }
    }

    /**
     * Unregisters both MCP servers from the Claude Code configuration.
     *
     * This method is called when the plugin is disabled or uninstalled.
     * It uses the Claude Code CLI to remove the MCP server entries.
     *
     * The extracted server files in the user's home directory are NOT deleted,
     * only the configuration entries are removed.
     *
     * @return `true` if unregistration completed (servers removed or didn't exist),
     *         `false` if an error occurred
     */
    fun unregister(): Boolean {
        val ideServerName = getIdeMcpServerName()
        val serverNames = listOf(COMMON_SERVER_NAME, ideServerName)

        var anyError = false

        for (serverName in serverNames) {
            val result = runClaudeMcpRemove(serverName)
            if (result == McpRemoveResult.ERROR) {
                anyError = true
            }
        }

        return !anyError
    }

    /**
     * Result of running `claude mcp remove` command.
     */
    private enum class McpRemoveResult {
        /** Server existed and was successfully removed. */
        REMOVED,
        /** Server did not exist (nothing to remove). */
        NOT_FOUND,
        /** An error occurred during removal. */
        ERROR
    }

    /**
     * Removes an MCP server from Claude Code configuration using the CLI.
     *
     * Executes `claude mcp remove <serverName> -s user` command to remove
     * the server from the user-scoped configuration.
     *
     * @param serverName The name of the MCP server to remove
     * @return [McpRemoveResult] indicating whether server was removed, not found, or error occurred
     */
    private fun runClaudeMcpRemove(serverName: String): McpRemoveResult {
        return try {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")
            val command = if (isWindows) {
                listOf("cmd", "/c", "claude", "mcp", "remove", serverName, "-s", "user")
            } else {
                listOf("claude", "mcp", "remove", serverName, "-s", "user")
            }

            logger.info("Running: ${command.joinToString(" ")}")

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            when {
                exitCode == 0 -> {
                    logger.info("MCP server '$serverName' removed successfully")
                    McpRemoveResult.REMOVED
                }
                output.contains("not found", ignoreCase = true) ||
                output.contains("does not exist", ignoreCase = true) -> {
                    logger.info("MCP server '$serverName' was not registered")
                    McpRemoveResult.NOT_FOUND
                }
                else -> {
                    logger.warn("Failed to remove MCP server '$serverName': $output")
                    McpRemoveResult.ERROR
                }
            }
        } catch (e: Exception) {
            logger.error("Error running claude mcp remove for '$serverName'", e)
            McpRemoveResult.ERROR
        }
    }
}
