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

val NotificationSettings.priorityDueEnabled: Boolean get() = !priority_due_disabled
val NotificationSettings.collaborationEnabled: Boolean get() = !collaboration_disabled

// --- Write: copy onto a positive value, never construct with a *_disabled field directly ---

fun NotificationSettings.withAllEnabled(enabled: Boolean): NotificationSettings =
  copy(all_disabled = !enabled)

fun NotificationSettings.withPriorityDue(enabled: Boolean): NotificationSettings =
  copy(priority_due_disabled = !enabled)

fun NotificationSettings.withCollaboration(enabled: Boolean): NotificationSettings =
  copy(collaboration_disabled = !enabled)
