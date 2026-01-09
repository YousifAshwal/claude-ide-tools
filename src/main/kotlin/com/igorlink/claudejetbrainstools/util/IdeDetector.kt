package com.igorlink.claudejetbrainstools.util

import com.intellij.openapi.application.ApplicationInfo

/**
 * Enumeration of supported JetBrains IDEs with their configuration properties.
 *
 * Each IDE has a unique port number to allow multiple IDEs to run simultaneously
 * with their own MCP servers. The ports are assigned sequentially starting from 8765.
 *
 * ## Port Assignments
 * | IDE | Port |
 * |-----|------|
 * | IntelliJ IDEA | 8765 |
 * | WebStorm | 8766 |
 * | PyCharm | 8767 |
 * | GoLand | 8768 |
 * | PhpStorm | 8769 |
 * | RubyMine | 8770 |
 * | CLion | 8771 |
 * | Rider | 8772 |
 * | DataGrip | 8773 |
 * | Android Studio | 8774 |
 * | RustRover | 8775 |
 * | Aqua | 8776 |
 * | DataSpell | 8777 |
 * | Unknown | 8780 |
 *
 * @property displayName Human-readable name for notifications (e.g., "IntelliJ IDEA")
 * @property shortName Short identifier for command-line args and config (e.g., "idea")
 * @property port HTTP server port for this IDE
 */
enum class JetBrainsIde(
    val displayName: String,
    val shortName: String,
    val port: Int
) {
    IDEA("IntelliJ IDEA", "idea", 8765),
    WEBSTORM("WebStorm", "webstorm", 8766),
    PYCHARM("PyCharm", "pycharm", 8767),
    GOLAND("GoLand", "goland", 8768),
    PHPSTORM("PhpStorm", "phpstorm", 8769),
    RUBYMINE("RubyMine", "rubymine", 8770),
    CLION("CLion", "clion", 8771),
    RIDER("Rider", "rider", 8772),
    DATAGRIP("DataGrip", "datagrip", 8773),
    ANDROID_STUDIO("Android Studio", "android-studio", 8774),
    RUSTROVER("RustRover", "rustrover", 8775),
    AQUA("Aqua", "aqua", 8776),
    DATASPELL("DataSpell", "dataspell", 8777),
    /** Fallback for unrecognized IDEs. */
    UNKNOWN("Unknown IDE", "unknown", 8780);

    /**
     * Returns the MCP server name for this IDE.
     *
     * Format: `claude-{shortName}-tools`
     *
     * Example: For IntelliJ IDEA, returns `claude-idea-tools`
     */
    val mcpServerName: String
        get() = "claude-$shortName-tools"

    /**
     * Returns the installation directory name for MCP server files.
     *
     * Format: `.claude-{shortName}-tools`
     *
     * This directory is created in the user's home directory to store
     * the extracted MCP server JavaScript files.
     */
    val installDirName: String
        get() = ".$mcpServerName"
}

/**
 * Utility object for detecting the current JetBrains IDE type.
 *
 * This detector uses IntelliJ's [ApplicationInfo] to determine which IDE
 * is currently running. The result is cached for performance since the
 * IDE type doesn't change during runtime.
 *
 * ## Usage Example
 * ```kotlin
 * val ide = IdeDetector.detect()
 * println("Running in ${ide.displayName} on port ${ide.port}")
 * ```
 *
 * @see JetBrainsIde The enumeration of supported IDEs
 */
object IdeDetector {

    /** Cached IDE detection result for performance. */
    @Volatile
    private var cachedIde: JetBrainsIde? = null

    /**
     * Detects the current IDE type based on ApplicationInfo.
     *
     * Examines the application name and version to determine which JetBrains IDE
     * is running. The result is cached after the first call for performance.
     *
     * The detection checks product name and version name against known IDE identifiers.
     * Android Studio is checked first since its name contains "Studio" which might
     * conflict with other checks.
     *
     * @return The detected [JetBrainsIde] enum value, or [JetBrainsIde.UNKNOWN] if unrecognized
     */
    fun detect(): JetBrainsIde {
        cachedIde?.let { return it }

        val appInfo = ApplicationInfo.getInstance()
        val productName = appInfo.fullApplicationName.lowercase()
        val versionName = appInfo.versionName.lowercase()

        val detected = when {
            // Check specific IDEs first (order matters for some)
            productName.contains("android studio") || versionName.contains("android") -> JetBrainsIde.ANDROID_STUDIO
            productName.contains("rustrover") || versionName.contains("rustrover") -> JetBrainsIde.RUSTROVER
            productName.contains("webstorm") || versionName.contains("webstorm") -> JetBrainsIde.WEBSTORM
            productName.contains("pycharm") || versionName.contains("pycharm") -> JetBrainsIde.PYCHARM
            productName.contains("goland") || versionName.contains("goland") -> JetBrainsIde.GOLAND
            productName.contains("phpstorm") || versionName.contains("phpstorm") -> JetBrainsIde.PHPSTORM
            productName.contains("rubymine") || versionName.contains("rubymine") -> JetBrainsIde.RUBYMINE
            productName.contains("clion") || versionName.contains("clion") -> JetBrainsIde.CLION
            productName.contains("rider") || versionName.contains("rider") -> JetBrainsIde.RIDER
            productName.contains("datagrip") || versionName.contains("datagrip") -> JetBrainsIde.DATAGRIP
            productName.contains("dataspell") || versionName.contains("dataspell") -> JetBrainsIde.DATASPELL
            productName.contains("aqua") || versionName.contains("aqua") -> JetBrainsIde.AQUA
            productName.contains("intellij") || versionName.contains("idea") -> JetBrainsIde.IDEA
            else -> JetBrainsIde.UNKNOWN
        }

        cachedIde = detected
        return detected
    }

    /**
     * Returns the HTTP server port for the current IDE.
     *
     * Convenience method equivalent to `detect().port`.
     *
     * @return The port number for the current IDE's HTTP server
     */
    fun getPort(): Int = detect().port

    /**
     * Returns the MCP server name for the current IDE.
     *
     * Convenience method equivalent to `detect().mcpServerName`.
     *
     * @return The MCP server name (e.g., "claude-idea-tools")
     */
    fun getMcpServerName(): String = detect().mcpServerName

    /**
     * Returns the human-readable display name of the current IDE.
     *
     * Convenience method equivalent to `detect().displayName`.
     *
     * @return The display name (e.g., "IntelliJ IDEA")
     */
    fun getDisplayName(): String = detect().displayName

    /**
     * Clears the cached IDE detection result.
     *
     * This method is primarily intended for testing purposes to allow
     * re-detection after changing mock ApplicationInfo.
     */
    fun clearCache() {
        cachedIde = null
    }
}
