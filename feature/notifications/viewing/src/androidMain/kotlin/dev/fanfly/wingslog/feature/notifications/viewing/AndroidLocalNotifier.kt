package dev.fanfly.wingslog.feature.notifications.viewing

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
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
        NotificationChannelCompat.Builder(
          channel.channelId(),
          channel.importance()
        )
          .setName(channel.displayName())
          .setDescription(channel.description())
          .build()
      )
    }
  }

  override suspend fun post(notification: PendingNotification) {
    val builder =
      NotificationCompat.Builder(context, notification.channel.channelId())
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(notification.title)
        // Collapsed is a single-line slot, so give it the first paragraph rather than the whole
        // body flattened into a run-on sentence with the tap hint trailing off the end.
        .setContentText(notification.body.substringBefore(PARAGRAPH_BREAK))
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomBigContentView(expandedContent(notification.title, notification.body))
        .setAutoCancel(true)
        .setPriority(
          if (notification.highPriority) NotificationCompat.PRIORITY_HIGH
          else NotificationCompat.PRIORITY_DEFAULT
        )
        .setContentIntent(tapPendingIntent(notification))
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

  /**
   * The expanded title + body, as a view we own rather than `BigTextStyle`.
   *
   * `BigTextStyle` cannot render the paragraph breaks the bodies are written with: the platform
   * collapses consecutive newlines in `EXTRA_BIG_TEXT`, so `a\n\nb` and `a\nb` render identically.
   * That was measured on-device against a framework-built notification carrying byte-identical
   * text, and no filler survives it either — U+00A0, U+200B and U+2007 all collapse the same way.
   * A `TextView` we own is not subject to that, so [body] goes in verbatim, blank lines and all.
   *
   * `DecoratedCustomViewStyle` keeps the system header, icon, timestamp and expand affordance, so
   * this opts out of the text block only — not out of the notification template.
   */
  private fun expandedContent(title: String, body: String): RemoteViews =
    RemoteViews(context.packageName, R.layout.notification_urgency_body).apply {
      setTextViewText(R.id.notification_urgency_title, title)
      setTextViewText(R.id.notification_urgency_body_text, body)
    }

  /**
   * `getLaunchIntentForPackage` rather than a direct `MainActivity` reference — `:viewing` cannot
   * depend on the `app` module (wrong direction; §3), and this is the standard way to target "this
   * app's launcher activity" without one. `MainActivity.handleDeepLink` decodes the URI via
   * [NotificationTapRouter], the same chain `AircraftShareDeepLinks`/`EmailLinkDeepLinks` already use
   * (design §5.3). `null` (no launcher found) means [NotificationCompat.Builder.setContentIntent]
   * gets a no-op — same tray entry, just not tappable, rather than a crash.
   */
  private fun tapPendingIntent(notification: PendingNotification): PendingIntent? {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
      ?: return null
    launchIntent.action = Intent.ACTION_VIEW
    launchIntent.data = Uri.parse(NotificationTapRouter.encode(notification.tapTarget))
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    return PendingIntent.getActivity(
      context,
      notification.id.hashCode(),
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun NotificationChannel.channelId(): String = when (this) {
    NotificationChannel.COLLABORATION -> "collaboration"
    NotificationChannel.URGENCY_UPDATE -> "urgency_update"
    NotificationChannel.GROUNDED -> "grounded"
  }

  private fun NotificationChannel.displayName(): String = context.getString(
    when (this) {
      NotificationChannel.COLLABORATION -> R.string.notification_channel_collaboration_name
      NotificationChannel.URGENCY_UPDATE -> R.string.notification_channel_urgency_update_name
      NotificationChannel.GROUNDED -> R.string.notification_channel_grounded_name
    }
  )

  private fun NotificationChannel.description(): String = context.getString(
    when (this) {
      NotificationChannel.COLLABORATION -> R.string.notification_channel_collaboration_description
      NotificationChannel.URGENCY_UPDATE -> R.string.notification_channel_urgency_update_description
      NotificationChannel.GROUNDED -> R.string.notification_channel_grounded_description
    }
  )

  private fun NotificationChannel.importance(): Int = when (this) {
    NotificationChannel.GROUNDED -> NotificationManager.IMPORTANCE_HIGH
    NotificationChannel.COLLABORATION, NotificationChannel.URGENCY_UPDATE -> NotificationManager.IMPORTANCE_DEFAULT
  }

  companion object {
    private val log = Logger.withTag("AndroidLocalNotifier")

    /** Fixed — the `tag` (per-notification id string) is what makes a slot unique, not this. */
    private const val NOTIFY_ID = 0

    /**
     * How `engine` separates paragraphs in a body (design §6.5). Split on it for the collapsed
     * one-liner, flatten it for the expanded view — see [expandedBody] for why it cannot survive
     * as a blank line.
     */
    private const val PARAGRAPH_BREAK = "\n\n"
  }
}
