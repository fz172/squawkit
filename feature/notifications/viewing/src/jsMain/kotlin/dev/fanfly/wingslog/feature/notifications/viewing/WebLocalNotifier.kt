package dev.fanfly.wingslog.feature.notifications.viewing

import dev.fanfly.wingslog.feature.notifications.model.PendingNotification

/**
 * Web: `new Notification(...)`, reached via `dynamic`/`js()` rather than `org.w3c.notifications`
 * externals — same house pattern `WebNotificationPermission` uses. [PendingNotification.id] is
 * passed as `tag`; per spec, a matching `tag` **replaces** the existing notification and does so
 * *silently* unless `renotify: true` — the `setOnlyAlertOnce`/passive-interruption handling the
 * other two actuals need is largely the web default (design §7.3, §8.4).
 *
 * **Largely, not entirely, and the gap was a silent outage.** Silent replacement is right while the
 * notification is still on screen: "3 changes" becoming "4 changes" should not buzz twice. It is
 * wrong once the pilot has *dismissed* it — the tag then replaces something that is not there, so
 * every later write in the session lands silently and the tray stays empty until `sessionStart`
 * rolls at `ACTIVITY_WINDOW`. Half an hour of a collaborator's edits, invisible, because the first
 * one was read and swiped away.
 *
 * [live] already answers the question that separates those two cases — it holds the instance for
 * each tag and drops it on `onclose` — so `renotify` is set to exactly "the previous one is gone".
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
    // Nothing live under this tag means the previous notification was dismissed, or there never was
    // one — either way this is news and should alert. Something live means it is still on screen and
    // this is an update to it, which must not buzz again.
    val renotify = !live.containsKey(notification.id)
    val instance =
      createNotification(notification.title, notification.body, notification.id, renotify)
    live[notification.id] = instance
    // Identity-checked, because a replaced notification may fire `close` *after* its replacement is
    // already recorded here. Removing blindly would drop the live entry for a notification that is
    // still on screen, and the next update would then re-alert instead of replacing quietly.
    instance.onclose = { if (live[notification.id] === instance) live.remove(notification.id) }
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
    tag: String,
    renotify: Boolean,
  ): dynamic {
    val icon = NOTIFICATION_ICON
    return js(
      "new Notification(title, { body: body, tag: tag, icon: icon, renotify: renotify })"
    )
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
