package dev.fanfly.wingslog.feature.notifications.viewing

import dev.fanfly.wingslog.feature.notifications.model.PendingNotification

/**
 * Displays a finished [PendingNotification] on the device. Design §5.2.
 *
 * Implementations must honor [PendingNotification.id] as a replace key: posting the same id twice
 * updates the existing tray entry rather than stacking a second one. On Android that means the same
 * (tag, id) pair passed to `NotificationManagerCompat.notify`; on iOS the same
 * `UNNotificationRequest` identifier; on web the same `Notification` `tag`.
 */
interface LocalNotifier {
  suspend fun post(notification: PendingNotification)
  suspend fun cancel(id: String)
}
