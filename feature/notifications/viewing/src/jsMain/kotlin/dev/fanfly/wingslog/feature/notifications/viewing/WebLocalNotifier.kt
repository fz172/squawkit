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
 *
 * `icon` carries the same SquawkIt airplane the other two platforms show — Android's
 * `ic_notification` mask, iOS's app icon. Without it a browser picks its own fallback (Chrome uses
 * the page favicon, Firefox a generic bell), so passing it is what makes the web notification look
 * like this app rather than like the browser.
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
  ): dynamic {
    val icon = NOTIFICATION_ICON
    return js("new Notification(title, { body: body, tag: tag, icon: icon })")
  }

  private fun focusWindow() {
    js("window.focus()")
  }

  private companion object {
    /**
     * The 192² app icon `webApp/src/jsMain/resources/index.html` already links as a favicon — the
     * same airplane, at the size browsers want for a notification badge. Relative, so it
     * resolves against the document base like every other asset the page loads; a root-absolute
     * path would break the moment the app is served from a sub-path.
     */
    const val NOTIFICATION_ICON = "favicon-192.png"
  }
}
