package dev.fanfly.wingslog.feature.notifications.model

import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.MaintenanceTaskId
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.id.ThingId

/**
 * Where a tap on a [PendingNotification] should land. Carried over the wire by
 * `NotificationTapRouter` and applied to shell state by `AdaptiveShellViewModel` (design §5.3).
 *
 * Every variant names an [thingId], because every notification this app sends is about one
 * thing — so selecting it is the one thing every tap does, whatever else follows.
 *
 * The record variants land the pilot on the record *in its list*, scrolled to and briefly
 * highlighted, rather than in its edit form: a notification reports that something changed, and the
 * useful destination is the record in the context of everything around it.
 *
 * **`Aircraft.tab` is a plain nullable [String], not a typed tab enum.** Design §5.3 sketches it as
 * an `AircraftTab` — no such type exists anywhere in the tree today (checked; tab selection in
 * `feature/thing/dashboard` is per-composable, not a shared enum), and `:model` could not depend
 * on one even if it did, since `:model` may only depend on `core:model` (§3). It travels as a raw
 * string in the tap URI, so a `String` here matches the wire shape; resolving it to the shell's
 * `ShellSection` is the consumer's job.
 */
sealed interface NotificationTapTarget {
  val thingId: ThingId

  data class Thing(override val thingId: ThingId, val tab: String? = null) :
    NotificationTapTarget

  data class Squawk(override val thingId: ThingId, val squawkId: SquawkId) :
    NotificationTapTarget

  data class Task(
    override val thingId: ThingId,
    val taskId: MaintenanceTaskId
  ) :
    NotificationTapTarget

  data class Log(override val thingId: ThingId, val logId: MaintenanceLogId) :
    NotificationTapTarget

  data class DataLog(override val thingId: ThingId, val dataLogId: DataLogId) :
    NotificationTapTarget

  /**
   * A suggestion run ended (PRD R20): the Thing's suggestions screen, which shows the answer held
   * for review (task population design §9.1, §17).
   */
  data class Suggestions(override val thingId: ThingId) : NotificationTapTarget
}
