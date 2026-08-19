package dev.fanfly.wingslog.feature.notifications.model

import dev.fanfly.wingslog.core.model.settings.NotificationSettings

/**
 * Readable, positive names for [NotificationSettings]' inverted `*_disabled` fields (design §4.1),
 * and the mutators to go with them.
 *
 * This is the ONE place the inversion is spelled out. There is no Kotlin mirror data class —
 * `NotificationSettings` is what callers pass around, `PrefsState.Resolved` carries it, and
 * `NotificationPrefsManager.update` mutates it directly. A mirror would restate the proto's
 * all-on-by-default guarantee in a second place that can silently disagree with it, and would need
 * its own round-trip mapping test to catch a field added to one side and not the other —
 * `DeveloperOptionsMappingTest` exists precisely because that failure mode "looks exactly like a
 * toggle that refuses to turn on." An extension property cannot drift that way: a new proto field
 * either gets one here or the call site fails to compile.
 *
 * `SubscriptionManager`'s own doc comment already settled this question for a proto the Cloud
 * Functions also read — "never a forked Kotlin copy" — and notification preferences are read by the
 * fan-out trigger the same way (design §7.4).
 */

// --- Read: positive names, never the *_disabled fields directly ---

val NotificationSettings.allEnabled: Boolean get() = !all_disabled

val NotificationSettings.aogEnabled: Boolean get() = !aog_disabled
val NotificationSettings.squawkPriorityEnabled: Boolean get() = !squawk_priority_disabled
val NotificationSettings.overdueEnabled: Boolean get() = !overdue_disabled
val NotificationSettings.dueSoonEnabled: Boolean get() = !due_soon_disabled

val NotificationSettings.aircraftActivityEnabled: Boolean get() = !aircraft_activity_disabled
val NotificationSettings.squawkActivityEnabled: Boolean get() = !squawk_activity_disabled
val NotificationSettings.taskActivityEnabled: Boolean get() = !task_activity_disabled
val NotificationSettings.logActivityEnabled: Boolean get() = !log_activity_disabled

// --- Write: copy onto a positive value, never construct with a *_disabled field directly ---

fun NotificationSettings.withAllEnabled(enabled: Boolean): NotificationSettings =
  copy(all_disabled = !enabled)

fun NotificationSettings.withAog(enabled: Boolean): NotificationSettings =
  copy(aog_disabled = !enabled)

fun NotificationSettings.withSquawkPriority(enabled: Boolean): NotificationSettings =
  copy(squawk_priority_disabled = !enabled)

fun NotificationSettings.withOverdue(enabled: Boolean): NotificationSettings =
  copy(overdue_disabled = !enabled)

fun NotificationSettings.withDueSoon(enabled: Boolean): NotificationSettings =
  copy(due_soon_disabled = !enabled)

fun NotificationSettings.withAircraftActivity(enabled: Boolean): NotificationSettings =
  copy(aircraft_activity_disabled = !enabled)

fun NotificationSettings.withSquawkActivity(enabled: Boolean): NotificationSettings =
  copy(squawk_activity_disabled = !enabled)

fun NotificationSettings.withTaskActivity(enabled: Boolean): NotificationSettings =
  copy(task_activity_disabled = !enabled)

fun NotificationSettings.withLogActivity(enabled: Boolean): NotificationSettings =
  copy(log_activity_disabled = !enabled)
