package dev.fanfly.wingslog.feature.notifications.viewing

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.feature.notifications.model.NotificationChannel
import dev.fanfly.wingslog.feature.notifications.model.PendingNotification

/**
 * Android: `NotificationManagerCompat`, with the three [NotificationChannel]s registered in
 * [init] — before this instance can post anything, since Koin creates it once and every `post()`
 * runs on the same instance. Re-creating an already-existing channel is a documented no-op, so this
 * is safe to run on every process start rather than only the first ever.
 *
 * [PendingNotification.id] is used as `NotificationManagerCompat.notify`'s `tag` parameter, with a
 * fixed numeric id (0) — a given `(tag, id)` pair identifies one tray slot, so the same string tag
 * always replaces rather than stacking. [cancel] uses the identical pair.
 */
class AndroidLocalNotifier(
  private val context: Context,
) : LocalNotifier {

  private val manager = NotificationManagerCompat.from(context)

  init {
    NotificationChannel.entries.forEach { channel ->
      manager.createNotificationChannel(
        NotificationChannelCompat.Builder(channel.channelId(), channel.importance())
          .setName(channel.displayName())
          .setDescription(channel.description())
          .build()
      )
    }
  }

  override suspend fun post(notification: PendingNotification) {
    val builder = NotificationCompat.Builder(context, notification.channel.channelId())
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(notification.title)
      .setContentText(notification.body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
      .setAutoCancel(true)
      .setPriority(
        if (notification.highPriority) NotificationCompat.PRIORITY_HIGH
        else NotificationCompat.PRIORITY_DEFAULT
      )
    try {
      manager.notify(notification.id, NOTIFY_ID, builder.build())
    } catch (e: SecurityException) {
      // NotificationManagerCompat already catches this internally on most versions; kept as a
      // defensive backstop so a permission revoked mid-flight never crashes the caller — the
      // caller checked NotificationPermission before deciding to post at all, and this is a race,
      // not a decision this class should make twice.
      log.w(e) { "Notification post denied at the OS level (id=${notification.id})" }
    }
  }

  override suspend fun cancel(id: String) {
    manager.cancel(id, NOTIFY_ID)
  }

  private fun NotificationChannel.channelId(): String = when (this) {
    NotificationChannel.COLLABORATION -> "collaboration"
    NotificationChannel.URGENCY -> "urgency"
    NotificationChannel.GROUNDED -> "grounded"
  }

  // TODO(notifications P1.10): move these through strings.xml once the module carries any — a
  // channel name is user-visible (system Settings > App notifications > <this name>), so it should
  // localize, but adding a whole classic-Android res/values pipeline for three strings ahead of any
  // other user-facing copy in this module isn't worth it yet.
  private fun NotificationChannel.displayName(): String = when (this) {
    NotificationChannel.COLLABORATION -> "Collaboration"
    NotificationChannel.URGENCY -> "Urgency"
    NotificationChannel.GROUNDED -> "Aircraft grounded"
  }

  private fun NotificationChannel.description(): String = when (this) {
    NotificationChannel.COLLABORATION -> "Someone changes a shared aircraft"
    NotificationChannel.URGENCY -> "A squawk or inspection becomes more urgent"
    NotificationChannel.GROUNDED -> "An aircraft is grounded by an AOG squawk"
  }

  private fun NotificationChannel.importance(): Int = when (this) {
    NotificationChannel.GROUNDED -> NotificationManager.IMPORTANCE_HIGH
    NotificationChannel.COLLABORATION, NotificationChannel.URGENCY -> NotificationManager.IMPORTANCE_DEFAULT
  }

  companion object {
    private val log = Logger.withTag("AndroidLocalNotifier")

    /** Fixed — the `tag` (per-notification id string) is what makes a slot unique, not this. */
    private const val NOTIFY_ID = 0
  }
}
