package dev.fanfly.wingslog.feature.notifications.viewing

import dev.fanfly.wingslog.feature.notifications.model.PendingNotification

/**
 * Web: `new Notification(...)`, reached via `dynamic`/`js()` rather than `org.w3c.notifications`
 * externals — same house pattern `WebNotificationPermission` uses. [PendingNotification.id] is
 * passed as `tag`; per spec, a matching `tag` **replaces** the existing notification and does so
 * *silently* unless `renotify: true` (deliberately omitted) — the `setOnlyAlertOnce`/passive-
 * interruption handling the other two actuals need is simply the web default (design §7.3, §8.4).
 *
 * `PendingNotification.channel` has no web equivalent (no channel concept), and `highPriority` has
 * no web equivalent either (no priority/interruption API) — both silently no-op here, same honesty
 * as iOS's deferred Time Sensitive mapping.
 *
 * The constructed `Notification` is kept in [live] so [cancel] has something to `.close()` — unlike
 * Android/iOS, the web API has no "cancel by id" call; only the object returned by the constructor
 * can close itself.
 */
class WebLocalNotifier : LocalNotifier {

  private val live = mutableMapOf<String, dynamic>()

  override suspend fun post(notification: PendingNotification) {
    val instance =
      createNotification(notification.title, notification.body, notification.id)
    live[notification.id] = instance
    instance.onclose = { live.remove(notification.id) }
    // Where a click should land (design §5.3). Unlike Android's PendingIntent and iOS's userInfo,
    // nothing has to be serialised into the notification itself — this tab is the one that will
    // handle the click, so the handler simply closes over the target.
    val tapUri = NotificationTapRouter.encode(notification.tapTarget)
    instance.onclick = {
      // The tab is very likely in the background — that is when a notification is worth showing at
      // all — so raise it before routing, or the app navigates somewhere the pilot cannot see.
      focusWindow()
      NotificationTapRouter.deliver(tapUri)
      // A click does not dismiss on every browser (Chrome on desktop leaves it up); close it
      // explicitly so it does not sit there having already been acted on.
      instance.close()
      live.remove(notification.id)
    }
  }

  override suspend fun cancel(id: String) {
    live.remove(id)
      ?.close()
  }

  private fun createNotification(
    title: String,
    body: String,
    tag: String
  ): dynamic =
    js("new Notification(title, { body: body, tag: tag })")

  private fun focusWindow() {
    js("window.focus()")
  }
}
