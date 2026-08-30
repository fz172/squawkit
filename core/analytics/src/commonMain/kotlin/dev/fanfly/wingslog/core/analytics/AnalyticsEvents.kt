package dev.fanfly.wingslog.core.analytics

import dev.fanfly.wingslog.core.analytics.AnalyticsEvent.Name
import dev.fanfly.wingslog.core.analytics.AnalyticsEvent.Param

// The taxonomy proper. Each event is a data class whose constructor names exactly the parameters
// that event carries, so a call site cannot omit one, misspell a key, or invent a parameter the
// event does not have — the three failures the untyped logEvent map allowed.
//
// Every param value is a String because that is what GA4 receives from logEvent. Counts are
// stringified at the boundary here rather than at the call site so the conversion happens once.

// ---------------------------------------------------------------------------------------------
// Ads — shipped, names fixed. See docs/ads/display_ads_PRD.md §12.
// ---------------------------------------------------------------------------------------------

/** An ad unit rendered into a slot. Fires per fill, including a re-render of the same slot. */
data class AdSlotFilled(
  val surface: String,
  val slotIndex: Int,
  val unitPosition: String,
) : AnalyticsEvent {
  override val name = Name.AD_SLOT_FILLED
  override val params = mapOf(
    Param.SURFACE to surface,
    Param.SLOT_INDEX to slotIndex.toString(),
    Param.UNIT_POSITION to unitPosition,
  )
}

/**
 * A billable impression — once per slot per session, unlike [AdSlotFilled]. Counting a scrolled-back
 * slot twice would inflate the number the ads PRD §12 reads as revenue.
 */
data class AdImpression(
  val surface: String,
  val slotIndex: Int,
  val unitPosition: String,
) : AnalyticsEvent {
  override val name = Name.AD_IMPRESSION
  override val params = mapOf(
    Param.SURFACE to surface,
    Param.SLOT_INDEX to slotIndex.toString(),
    Param.UNIT_POSITION to unitPosition,
  )
}

/** No fill. [reason] comes from the ad SDK, so it is an opaque string rather than an enum. */
data class AdFillFailed(
  val surface: String,
  val reason: String,
) : AnalyticsEvent {
  override val name = Name.AD_FILL_FAILED
  override val params = mapOf(Param.SURFACE to surface, Param.REASON to reason)
}

/** A tap on a filled unit. */
data class AdClick(
  val surface: String,
  val slotIndex: Int,
  val unitPosition: String,
) : AnalyticsEvent {
  override val name = Name.AD_CLICK
  override val params = mapOf(
    Param.SURFACE to surface,
    Param.SLOT_INDEX to slotIndex.toString(),
    Param.UNIT_POSITION to unitPosition,
  )
}

// ---------------------------------------------------------------------------------------------
// Sync — shipped, names fixed.
// ---------------------------------------------------------------------------------------------

/** A write the rules rejected. [shared] separates a borrowed scope from the account's own. */
data class SyncPermissionDeniedWrite(val shared: Boolean) : AnalyticsEvent {
  override val name = Name.SYNC_PERMISSION_DENIED_WRITE
  override val params = mapOf(Param.SCOPE to if (shared) "shared" else "own")
}

/** Share state reconciled after a denial. [trigger] is one of `SyncTelemetry`'s TRIGGER_ constants. */
data class SyncShareReconciled(val trigger: String) : AnalyticsEvent {
  override val name = Name.SYNC_SHARE_RECONCILED
  override val params = mapOf(Param.TRIGGER to trigger)
}

// ---------------------------------------------------------------------------------------------
// Notifications — shipped, names fixed.
// ---------------------------------------------------------------------------------------------

/**
 * One urgency notification posted. One event per notification rather than one per scan carrying a
 * count: params are strings, so a count would land as a GA4 dimension and "share by trigger" would
 * mean summing strings.
 *
 * `shared_fleet` keeps its aviation name because it is already emitting — see the immutability note
 * on [AnalyticsEvent].
 */
data class UrgencyNotificationPosted(
  val trigger: String,
  val sharedFleet: Boolean,
) : AnalyticsEvent {
  override val name = Name.URGENCY_NOTIFICATION_POSTED
  override val params = mapOf(
    Param.TRIGGER to trigger,
    Param.SHARED_FLEET to sharedFleet.toString(),
  )
}

// ---------------------------------------------------------------------------------------------
// Thing lifecycle — the PRD §13 metrics. Not yet emitted; #665 wires the call sites.
// ---------------------------------------------------------------------------------------------

/**
 * A Thing was created. Backs two §13 targets at once: the ≥40% non-airplane share (a split on
 * `template_id`) and the ≥1.8 median Things per account (a count per account).
 *
 * [source] records how the user got here — which preset path produced the Thing — so a low
 * non-airplane share can be read as either "not offered" or "offered and declined".
 */
data class ThingCreated(
  override val templateId: String,
  val source: String,
) : ThingScopedEvent {
  override val name = Name.THING_CREATED
  override val params = mapOf(Param.TEMPLATE_ID to templateId, Param.SOURCE to source)
}

/**
 * Starter tasks were shown for a new Thing. The denominator of the §13 ≥60% acceptance target —
 * without it, a low [StarterTasksAccepted] count cannot be told apart from packs never offered.
 */
data class StarterTasksOffered(
  override val templateId: String,
  val taskCount: Int,
) : ThingScopedEvent {
  override val name = Name.STARTER_TASKS_OFFERED
  override val params = mapOf(
    Param.TEMPLATE_ID to templateId,
    Param.TASK_COUNT to taskCount.toString(),
  )
}

/**
 * At least one starter task was kept. The numerator of the §13 ≥60% target; [taskCount] is how many
 * of the offered set survived.
 */
data class StarterTasksAccepted(
  override val templateId: String,
  val taskCount: Int,
) : ThingScopedEvent {
  override val name = Name.STARTER_TASKS_ACCEPTED
  override val params = mapOf(
    Param.TEMPLATE_ID to templateId,
    Param.TASK_COUNT to taskCount.toString(),
  )
}

/** A maintenance task was completed. Engagement input to the §13 retention guardrail. */
data class TaskCompleted(override val templateId: String) : ThingScopedEvent {
  override val name = Name.TASK_COMPLETED
  override val params = mapOf(Param.TEMPLATE_ID to templateId)
}

/** A defect was reported against a Thing. */
data class DefectCreated(override val templateId: String) : ThingScopedEvent {
  override val name = Name.DEFECT_CREATED
  override val params = mapOf(Param.TEMPLATE_ID to templateId)
}

/** A work-log entry was written. */
data class LogCreated(override val templateId: String) : ThingScopedEvent {
  override val name = Name.LOG_CREATED
  override val params = mapOf(Param.TEMPLATE_ID to templateId)
}

/**
 * An export finished. [thingCount] is how many Things it spanned, so an export is not misread as a
 * single-Thing action; [templateId] is the template of the export's Things.
 */
data class ExportCompleted(
  override val templateId: String,
  val format: String,
  val thingCount: Int,
) : ThingScopedEvent {
  override val name = Name.EXPORT_COMPLETED
  override val params = mapOf(
    Param.TEMPLATE_ID to templateId,
    Param.FORMAT to format,
    Param.THING_COUNT to thingCount.toString(),
  )
}
