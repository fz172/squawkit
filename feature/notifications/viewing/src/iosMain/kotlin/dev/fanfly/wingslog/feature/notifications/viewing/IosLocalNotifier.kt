package dev.fanfly.wingslog.feature.notifications.viewing

import dev.fanfly.wingslog.feature.notifications.model.PendingNotification
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS: `UNUserNotificationCenter`, called directly — same reasoning as
 * `IosNotificationPermission`, a standard system framework Kotlin/Native already has interop for.
 *
 * iOS has no channel concept the way Android does; [PendingNotification.channel] has nothing to
 * register here. `highPriority` is never mapped to `interruptionLevel = .timeSensitive` — the Time
 * Sensitive Notifications entitlement (an App Store review item) was decided against (2026-08-26):
 * a high-priority notification still arrives, it just never pierces Focus (design §5.2).
 *
 * `trigger = null` delivers immediately — every caller already decided *whether* to notify before
 * building a [PendingNotification]; there is no scheduled/deferred case in this design.
 */
class IosLocalNotifier : LocalNotifier {

  private val center = UNUserNotificationCenter.currentNotificationCenter()

  override suspend fun post(notification: PendingNotification) {
    val content = UNMutableNotificationContent().apply {
      setTitle(notification.title)
      setBody(notification.body)
      // Where a tap should land, read back by IosNotificationTapDelegate — the counterpart of the
      // intent data Android hangs on its tap PendingIntent (design §5.3).
      setUserInfo(
        mapOf(
          TAP_URI_USER_INFO_KEY to NotificationTapRouter.encode(notification.tapTarget),
        )
      )
    }
    val request = UNNotificationRequest.requestWithIdentifier(
      identifier = notification.id,
      content = content,
      trigger = null,
    )
    suspendCancellableCoroutine { cont ->
      center.addNotificationRequest(request) { cont.resume(Unit) }
    }
  }

  override suspend fun cancel(id: String) {
    center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
    center.removeDeliveredNotificationsWithIdentifiers(listOf(id))
  }
}
