package com.igorlink.claudeidetools.services

import com.igorlink.claudeidetools.util.IdeDetector
import com.igorlink.claudeidetools.util.NotificationHelper
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Service that automatically registers the MCP (Model Context Protocol) server with Claude Code CLI.
 *
 * This service is responsible for:
 * - Extracting bundled MCP server JavaScript files to the user's home directory
 * - Registering the servers in Claude Code's configuration file
 * - Managing server versions and updates
 * - Handling unregistration when the plugin is disabled
 *
 * The plugin deploys two types of MCP servers:
 * 1. **Common server** (`claude-ide-tools`): Handles universal operations like status, rename, and find usages
 *    that work identically across all JetBrains IDEs
 * 2. **IDE-specific server** (e.g., `claude-idea-tools`): Handles operations like move and extract method
 *    that require IDE-specific port configuration
 *
 * ## Configuration Locations
 * The service looks for Claude Code configuration in the following locations (in order):
 * - `~/.claude.json`
 * - `~/.claude/claude.json`
 * - `~/AppData/Roaming/Claude/claude_desktop_config.json` (Windows)
 * - `~/.config/claude-code/config.json` (Linux)
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
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

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

        /**
         * Returns a list of possible Claude Code configuration file locations.
         *
         * The order matters - the first existing file found will be used for reading and writing.
         * If no file exists, the first location in the list will be used for creating a new config.
         *
         * @param homeDir The user's home directory path
         * @return List of potential configuration file locations
         */
        private fun getConfigLocations(homeDir: String): List<File> = listOf(
            File(homeDir, ".claude.json"),
            File(homeDir, ".claude/claude.json"),
            File(homeDir, "AppData/Roaming/Claude/claude_desktop_config.json"),
            File(homeDir, ".config/claude-code/config.json")
        )
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
     * Registers both MCP servers in the Claude Code configuration file.
     *
     * Finds or creates the Claude Code configuration file and adds/updates
     * the mcpServers entries for both the common and IDE-specific servers.
     *
     * @param commonServerPath Absolute path to the extracted common server JS file
     * @param ideServerPath Absolute path to the extracted IDE-specific server JS file
     * @return [RegistrationResult] indicating the outcome of the registration
     */
    private fun registerBothServers(
        commonServerPath: String,
        ideServerPath: String
    ): RegistrationResult {
        val homeDir = System.getProperty("user.home")
        val configLocations = getConfigLocations(homeDir)

        // Use first existing config file, or default to first location
        val configFile = configLocations.find { it.exists() } ?: configLocations.first()

        return try {
            updateClaudeConfigWithBothServers(configFile, commonServerPath, ideServerPath)
        } catch (e: Exception) {
            logger.error("Failed to update Claude config at ${configFile.absolutePath}", e)
            RegistrationResult.FAILED
        }
    }

    /**
     * Builds an updated config JsonObject by copying all properties from existingConfig
     * except "mcpServers", then adding the new mcpServers map.
     *
     * @param existingConfig The original config to copy properties from
     * @param mcpServers The updated MCP servers map
     * @param omitIfEmpty If true, mcpServers key is omitted when the map is empty
     */
    private fun buildUpdatedConfig(
        existingConfig: JsonObject,
        mcpServers: Map<String, JsonElement>,
        omitIfEmpty: Boolean = false
    ): JsonObject {
        return buildJsonObject {
            existingConfig.forEach { (key, value) ->
                if (key != "mcpServers") {
                    put(key, value)
                }
            }
            if (!omitIfEmpty || mcpServers.isNotEmpty()) {
                put("mcpServers", JsonObject(mcpServers))
            }
        }
    }

    /**
     * Updates the Claude Code configuration file with both MCP server entries.
     *
     * Creates or modifies the mcpServers section in the config file:
     * - **Common server** (`claude-ide-tools`): Configured with just the server path,
     *   auto-discovers all running IDE instances
     * - **IDE-specific server** (e.g., `claude-idea-tools`): Configured with server path,
     *   port number, and IDE short name as arguments
     *
     * @param configFile The Claude Code configuration file to update
     * @param commonServerPath Absolute path to the common server JS file
     * @param ideServerPath Absolute path to the IDE-specific server JS file
     * @return [RegistrationResult] indicating what changes were made
     */
    private fun updateClaudeConfigWithBothServers(
        configFile: File,
        commonServerPath: String,
        ideServerPath: String
    ): RegistrationResult {
        val normalizedCommonPath = commonServerPath.replace("\\", "/")
        val normalizedIdePath = ideServerPath.replace("\\", "/")
        val ideServerName = getIdeMcpServerName()
        val port = getPort()
        val ideShortName = getIdeShortName()

        // Read existing config or create new one
        val existingConfig: JsonObject = if (configFile.exists()) {
            try {
                json.parseToJsonElement(configFile.readText()).jsonObject
            } catch (e: Exception) {
                logger.warn("Could not parse existing config, creating new one")
                JsonObject(emptyMap())
            }
        } else {
            JsonObject(emptyMap())
        }

        // Get or create mcpServers object
        val mcpServers = existingConfig["mcpServers"]?.jsonObject?.toMutableMap()
            ?: mutableMapOf()

        var anyUpdated = false
        var allAlreadyRegistered = true

        // Check and update common server
        val existingCommon = mcpServers[COMMON_SERVER_NAME]?.jsonObject
        val commonArgs = existingCommon?.get("args")?.jsonArray
        val existingCommonPath = commonArgs?.getOrNull(0)?.jsonPrimitive?.contentOrNull

        if (existingCommonPath != normalizedCommonPath) {
            allAlreadyRegistered = false
            if (existingCommon != null) anyUpdated = true

            val commonConfig = buildJsonObject {
                put("command", "node")
                putJsonArray("args") {
                    add(normalizedCommonPath)
                }
            }
            mcpServers[COMMON_SERVER_NAME] = commonConfig
            logger.info("Common MCP server '$COMMON_SERVER_NAME' configured")
        }

        // Check and update IDE-specific server
        val existingIde = mcpServers[ideServerName]?.jsonObject
        val ideArgs = existingIde?.get("args")?.jsonArray
        val existingIdePath = ideArgs?.getOrNull(0)?.jsonPrimitive?.contentOrNull
        val existingIdePort = ideArgs?.getOrNull(1)?.jsonPrimitive?.contentOrNull
        val existingIdeName = ideArgs?.getOrNull(2)?.jsonPrimitive?.contentOrNull

        if (existingIdePath != normalizedIdePath ||
            existingIdePort != port.toString() ||
            existingIdeName != ideShortName
        ) {
            allAlreadyRegistered = false
            if (existingIde != null) anyUpdated = true

            val ideConfig = buildJsonObject {
                put("command", "node")
                putJsonArray("args") {
                    add(normalizedIdePath)
                    add(port.toString())
                    add(ideShortName)
                }
            }
            mcpServers[ideServerName] = ideConfig
            logger.info("IDE-specific MCP server '$ideServerName' configured on port $port")
        }

        if (allAlreadyRegistered) {
            logger.info("Both MCP servers already registered with correct paths")
            return RegistrationResult.ALREADY_REGISTERED
        }

        // Build updated config
        val updatedConfig = buildUpdatedConfig(existingConfig, mcpServers)

        // Write config
        configFile.parentFile?.mkdirs()
        configFile.writeText(json.encodeToString(updatedConfig))

        logger.info("MCP servers registered in ${configFile.absolutePath}")
        return if (anyUpdated) RegistrationResult.UPDATED else RegistrationResult.NEWLY_REGISTERED
    }

    /**
     * Shows an appropriate IDE notification based on the registration result.
     *
     * - For [RegistrationResult.NEWLY_REGISTERED] and [RegistrationResult.UPDATED]:
     *   Shows an information notification with a "Restart IDE" action button
     * - For [RegistrationResult.ALREADY_REGISTERED]: No notification (silent)
     * - For [RegistrationResult.FAILED]: Shows a warning notification
     *
     * @param result The registration result to display notification for
     */
    fun showRegistrationNotification(result: RegistrationResult) {
        when (result) {
            RegistrationResult.NEWLY_REGISTERED, RegistrationResult.UPDATED -> {
                NotificationHelper.showNotificationWithAction(
                    content = "MCP Bridge installed. Restart IDE for Claude Code CLI to start using refactoring tools.",
                    type = NotificationType.INFORMATION,
                    actionText = "Restart IDE",
                    action = { ApplicationManager.getApplication().restart() },
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
     * It searches all possible Claude Code configuration file locations
     * and removes the MCP server entries from each.
     *
     * The extracted server files in the user's home directory are NOT deleted,
     * only the configuration entries are removed.
     *
     * @return `true` if unregistration succeeded for all found config files,
     *         `false` if any error occurred during unregistration
     */
    fun unregister(): Boolean {
        val homeDir = System.getProperty("user.home")
        val configLocations = getConfigLocations(homeDir)
        val ideServerName = getIdeMcpServerName()
        val serverNames = listOf(COMMON_SERVER_NAME, ideServerName)

        var allSucceeded = true
        var anyFound = false

        for (configFile in configLocations) {
            if (!configFile.exists()) {
                continue
            }

            try {
                val existingConfig = json.parseToJsonElement(configFile.readText()).jsonObject
                val mcpServers = existingConfig["mcpServers"]?.jsonObject?.toMutableMap()
                    ?: continue

                var removedAny = false
                for (serverName in serverNames) {
                    if (mcpServers.containsKey(serverName)) {
                        mcpServers.remove(serverName)
                        logger.info("MCP server '$serverName' removed from ${configFile.absolutePath}")
                        removedAny = true
                        anyFound = true
                    }
                }

                if (removedAny) {
                    val updatedConfig = buildUpdatedConfig(existingConfig, mcpServers, omitIfEmpty = true)
                    configFile.writeText(json.encodeToString(updatedConfig))
                }
            } catch (e: Exception) {
                logger.error("Failed to unregister MCP servers from ${configFile.absolutePath}", e)
                allSucceeded = false
            }
        }

        if (!anyFound) {
            logger.info("MCP servers were not registered in any config file")
        }

        return allSucceeded
    }
}
