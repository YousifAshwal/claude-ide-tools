package com.igorlink.claudeidetools

import com.igorlink.claudeidetools.services.McpAutoRegistrationService
import com.igorlink.claudeidetools.util.NotificationHelper
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger

/**
 * Listener for dynamic plugin unload and disable events.
 *
 * IntelliJ supports dynamic plugin loading/unloading without IDE restart.
 * This listener handles cleanup when the MCP Bridge plugin is:
 * - Disabled via Settings > Plugins
 * - Uninstalled
 * - Updated (special handling to avoid unnecessary unregistration)
 *
 * ## Cleanup Actions
 * When the plugin is disabled or uninstalled (not updated), this listener:
 * 1. Removes MCP server entries from Claude Code configuration
 * 2. Shows a notification about the unregistration
 *
 * The extracted server files are NOT deleted - only the config entries are removed.
 *
 * @see McpAutoRegistrationService.unregister The method called to clean up config
 */
class McpPluginUnloadListener : DynamicPluginListener {
    private val logger = Logger.getInstance(McpPluginUnloadListener::class.java)

    companion object {
        /** The plugin ID as declared in plugin.xml. */
        const val PLUGIN_ID = "com.igorlink.claudeidetools"
    }

    /**
     * Called before a plugin is unloaded.
     *
     * For the MCP Bridge plugin:
     * - If this is an update (new version being installed), does nothing
     * - Otherwise, unregisters MCP servers from Claude Code configuration
     *
     * @param pluginDescriptor Descriptor of the plugin being unloaded
     * @param isUpdate `true` if the plugin is being updated to a new version
     */
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (pluginDescriptor.pluginId.idString != PLUGIN_ID) {
            return
        }

        logger.info("MCP Bridge: Plugin is being unloaded (isUpdate=$isUpdate)")

        // Don't unregister if this is just an update - the new version will re-register
        if (isUpdate) {
            logger.info("MCP Bridge: Skipping unregistration - this is an update")
            return
        }

        try {
            val registrationService = service<McpAutoRegistrationService>()
            val success = registrationService.unregister()

            if (success) {
                logger.info("MCP Bridge: Successfully unregistered from Claude Code")
                NotificationHelper.info(
                    "MCP Bridge unregistered from Claude Code. Restart Claude Code CLI to apply.",
                    runOnEdt = true
                )
            } else {
                logger.warn("MCP Bridge: Failed to unregister from Claude Code")
            }
        } catch (e: Exception) {
            logger.error("MCP Bridge: Error during unregistration", e)
        }
    }

    /**
     * Called after a plugin has been unloaded.
     *
     * At this point, the plugin's services are no longer available.
     * This method only logs the event for debugging purposes.
     *
     * @param pluginDescriptor Descriptor of the plugin that was unloaded
     * @param isUpdate `true` if the plugin was updated to a new version
     */
    override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        // Called after plugin is unloaded - services are no longer available here
        if (pluginDescriptor.pluginId.idString == PLUGIN_ID) {
            logger.info("MCP Bridge: Plugin unloaded")
        }
    }
}
