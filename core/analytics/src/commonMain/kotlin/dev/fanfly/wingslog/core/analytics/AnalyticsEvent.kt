package dev.fanfly.wingslog.core.analytics

/**
 * The complete GA4 event taxonomy, modelled as types rather than strings.
 *
 * ## Why types
 *
 * [AnalyticsManager.logEvent] takes a name and a `Map<String, String>` and passes both through
 * unvalidated. That escape hatch is how every event in the app is emitted today, and it has the same
 * failure mode as an untyped wire format: a typo in a name or a param key produces no compile error,
 * no runtime error, and no event in GA4 — the data is simply missing when someone goes looking for
 * it, months later, with no way to backfill.
 *
 * ## Names are immutable
 *
 * **A GA4 event or parameter name cannot be changed once data has landed against it.** Renaming does
 * not migrate the history; it orphans it and starts a new series, which is the analytics equivalent
 * of changing a wire identity (#638). Treat every string in [Name] and [Param] as append-only:
 * add new entries freely, never edit or delete an existing one.
 *
 * The names below marked *shipped* are already emitting in production and are fixed for that reason,
 * including where they read as aviation-specific (`shared_fleet`) — the same grandfathering that
 * applies to wire names applies here.
 */
sealed interface AnalyticsEvent {
  val name: Name
  val params: Map<Param, String>

  /**
   * Every event name the app may emit. GA4 constrains these to ≤40 characters of
   * `[a-z0-9_]` starting with a letter, and reserves the `firebase_`, `google_` and `ga_`
   * prefixes; `AnalyticsTaxonomyTest` asserts all of it exhaustively over these entries.
   */
  enum class Name(val wire: String) {
    // --- Ads (shipped) ---
    AD_SLOT_FILLED("ad_slot_filled"),
    // NOT "ad_impression"/"ad_click": both are reserved Firebase names, auto-collected from the
    // AdMob integration. Firebase silently renames a custom event using one to its internal `_ai`
    // / `_ac`, merging ours into AdMob's — the event arrives, the metric looks healthy, and the
    // number the ads PRD §12 reads as revenue is conflated with a counter we do not control.
    // Caught on a device in #667; see reservedNames in AnalyticsTaxonomyTest.
    AD_UNIT_IMPRESSION("ad_unit_impression"),
    AD_FILL_FAILED("ad_fill_failed"),
    AD_UNIT_CLICK("ad_unit_click"),

    // --- Sync (shipped) ---
    SYNC_PERMISSION_DENIED_WRITE("sync_permission_denied_write"),
    SYNC_SHARE_RECONCILED("sync_share_reconciled"),

    // --- Notifications (shipped) ---
    URGENCY_NOTIFICATION_POSTED("urgency_notification_posted"),

    // --- Thing lifecycle: the PRD §13 success metrics ---
    THING_CREATED("thing_created"),
    STARTER_TASKS_OFFERED("starter_tasks_offered"),
    STARTER_TASKS_ACCEPTED("starter_tasks_accepted"),
    TASK_COMPLETED("task_completed"),
    DEFECT_CREATED("defect_created"),
    LOG_CREATED("log_created"),
    EXPORT_COMPLETED("export_completed"),
    ;
  }

  /**
   * Every parameter key. Same GA4 name rules as [Name]; values are additionally capped at 100
   * characters, which [AnalyticsEvent.toParams] enforces by truncation rather than by dropping the
   * event.
   */
  enum class Param(val wire: String) {
    // --- Ads (shipped) ---
    SURFACE("surface"),
    SLOT_INDEX("slot_index"),
    UNIT_POSITION("unit_position"),
    REASON("reason"),

    // --- Sync (shipped) ---
    SCOPE("scope"),

    // --- Notifications (shipped) ---
    TRIGGER("trigger"),
    SHARED_FLEET("shared_fleet"),

    // --- Thing-scoped ---
    // #666 wires the value at each call site; the taxonomy defines the slot.
    TEMPLATE_ID("template_id"),
    SOURCE("source"),
    FORMAT("format"),
    THING_COUNT("thing_count"),
    TASK_COUNT("task_count"),
    ;
  }
}

/**
 * A Thing-scoped event carries [templateId] so PRD §13 can split every metric by domain — the
 * ≥40%-non-airplane target and the aviation-cohort guardrail are both a filter on this one property.
 *
 * It is a property of the marker rather than a parameter each event remembers to add, so a new
 * Thing-scoped event cannot be defined without one.
 */
sealed interface ThingScopedEvent : AnalyticsEvent {
  val templateId: String
}

/** Flattens to the shape [AnalyticsManager.logEvent] takes, truncating values to GA4's 100 chars. */
fun AnalyticsEvent.toParams(): Map<String, String> =
  params.entries.associate { (key, value) -> key.wire to value.take(GA4_MAX_PARAM_VALUE_LENGTH) }

/** Logs a taxonomy event. The typed counterpart to [AnalyticsManager.logEvent]. */
fun AnalyticsManager.log(event: AnalyticsEvent) =
  logEvent(event.name.wire, event.toParams())

/** GA4 truncates parameter values beyond this length rather than rejecting the event. */
const val GA4_MAX_PARAM_VALUE_LENGTH = 100
