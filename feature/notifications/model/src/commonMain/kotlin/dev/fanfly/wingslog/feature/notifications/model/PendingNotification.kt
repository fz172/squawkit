package dev.fanfly.wingslog.feature.notifications.model

/**
 * A finished notification, ready to display. The contract between the two halves of the feature:
 * `engine` builds one, `viewing` renders it — it carries finished display strings and a tap target,
 * never a task or a squawk, which is what keeps `viewing` free of every other feature (design §5.2).
 */
data class PendingNotification(
  /**
   * Stable and deterministic. Re-posting under the same id replaces the tray entry rather than
   * stacking a second one — that is the whole mechanism §6.5's per-tier batching and §7.3's tray
   * coalescing rely on. Callers derive this from what the notification is *about*
   * (`"urgency:$thingId:$tier"`, `"n1:$thingId:$recordType:$actorUid:$sessionStart"`), never
   * from a random or incrementing value.
   */
  val id: String,
  val channel: NotificationChannel,
  val title: String,
  val body: String,
  /** Overdue only. Maps to `IMPORTANCE_HIGH` on Android; iOS has no equivalent treatment. */
  val highPriority: Boolean,
  val tapTarget: NotificationTapTarget,
)
