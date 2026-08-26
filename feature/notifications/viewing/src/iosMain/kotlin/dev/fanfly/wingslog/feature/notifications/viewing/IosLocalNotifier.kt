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
 * register here. `highPriority` is deliberately **not** mapped to `interruptionLevel = .timeSensitive`
 * yet — that needs the Time Sensitive Notifications entitlement, an App Store review item sequenced
 * into P5.3, not this task. Until it lands, a high-priority notification still arrives; it just does
 * not pierce Focus (design §5.2).
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
      // TODO(notifications P5.3): setInterruptionLevel(UNNotificationInterruptionLevelTimeSensitive)
      // once the Time Sensitive entitlement is in place, gated on notification.highPriority.
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
