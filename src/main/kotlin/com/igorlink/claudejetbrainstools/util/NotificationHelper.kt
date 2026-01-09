package com.igorlink.claudejetbrainstools.util

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Centralized helper for showing IDE notifications.
 *
 * This utility consolidates all notification logic to avoid duplication and ensure
 * consistent notification appearance across the plugin. All notifications use the
 * "Claude JetBrains Tools" notification group and title.
 *
 * ## Thread Safety
 * Methods accept a `runOnEdt` parameter to handle calls from background threads.
 * When `true`, the notification is posted via `invokeLater` to ensure it runs on
 * the Event Dispatch Thread.
 *
 * ## Usage Examples
 * ```kotlin
 * // Simple info notification
 * NotificationHelper.info("Operation completed successfully")
 *
 * // Warning from background thread
 * NotificationHelper.warning("Something went wrong", runOnEdt = true)
 *
 * // Notification with action button
 * NotificationHelper.showNotificationWithAction(
 *     content = "Update available",
 *     type = NotificationType.INFORMATION,
 *     actionText = "Install Now",
 *     action = { performUpdate() }
 * )
 * ```
 *
 * @see NotificationGroupManager IntelliJ's notification system
 */
object NotificationHelper {
    private val logger = Logger.getInstance(NotificationHelper::class.java)

    /** The notification group ID registered in plugin.xml. */
    private const val NOTIFICATION_GROUP = "Claude JetBrains Tools"

    /** The title displayed on all notifications. */
    private const val NOTIFICATION_TITLE = "Claude JetBrains Tools"

    /**
     * Shows a notification in the IDE.
     *
     * @param content The notification message content
     * @param type The notification type (INFORMATION, WARNING, ERROR)
     * @param project Optional project context for the notification
     * @param runOnEdt Whether to ensure execution on EDT (Event Dispatch Thread).
     *                 Set to true when calling from background threads.
     */
    fun showNotification(
        content: String,
        type: NotificationType,
        project: Project? = null,
        runOnEdt: Boolean = false
    ) {
        val notificationAction = {
            try {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification(NOTIFICATION_TITLE, content, type)
                    .notify(project)
            } catch (e: Exception) {
                logger.warn("Failed to show notification: ${e.message}")
            }
        }

        if (runOnEdt) {
            ApplicationManager.getApplication().invokeLater(notificationAction)
        } else {
            notificationAction()
        }
    }

    /**
     * Shows an information notification.
     *
     * Convenience method for [showNotification] with [NotificationType.INFORMATION].
     *
     * @param content The notification message text
     * @param project Optional project context (affects where notification appears)
     * @param runOnEdt Set to `true` when calling from a background thread
     */
    fun info(content: String, project: Project? = null, runOnEdt: Boolean = false) {
        showNotification(content, NotificationType.INFORMATION, project, runOnEdt)
    }

    /**
     * Shows a warning notification.
     *
     * Convenience method for [showNotification] with [NotificationType.WARNING].
     *
     * @param content The notification message text
     * @param project Optional project context (affects where notification appears)
     * @param runOnEdt Set to `true` when calling from a background thread
     */
    fun warning(content: String, project: Project? = null, runOnEdt: Boolean = false) {
        showNotification(content, NotificationType.WARNING, project, runOnEdt)
    }

    /**
     * Shows an error notification.
     *
     * Convenience method for [showNotification] with [NotificationType.ERROR].
     *
     * @param content The notification message text
     * @param project Optional project context (affects where notification appears)
     * @param runOnEdt Set to `true` when calling from a background thread
     */
    fun error(content: String, project: Project? = null, runOnEdt: Boolean = false) {
        showNotification(content, NotificationType.ERROR, project, runOnEdt)
    }

    /**
     * Shows a notification with an action button.
     *
     * @param content The notification message content
     * @param type The notification type (INFORMATION, WARNING, ERROR)
     * @param actionText Text displayed on the action button
     * @param action Callback executed when the action button is clicked
     * @param project Optional project context for the notification
     * @param runOnEdt Whether to ensure execution on EDT (Event Dispatch Thread).
     *                 Set to true when calling from background threads.
     */
    fun showNotificationWithAction(
        content: String,
        type: NotificationType,
        actionText: String,
        action: () -> Unit,
        project: Project? = null,
        runOnEdt: Boolean = false
    ) {
        val notificationAction = {
            try {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP)
                    .createNotification(NOTIFICATION_TITLE, content, type)
                    .addAction(NotificationAction.createSimple(actionText, action))
                    .notify(project)
            } catch (e: Exception) {
                logger.warn("Failed to show notification with action: ${e.message}")
            }
        }

        if (runOnEdt) {
            ApplicationManager.getApplication().invokeLater(notificationAction)
        } else {
            notificationAction()
        }
    }
}
