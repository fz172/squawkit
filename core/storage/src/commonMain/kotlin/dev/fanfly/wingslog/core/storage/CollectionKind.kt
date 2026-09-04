package dev.fanfly.wingslog.core.storage

import app.cash.sqldelight.ColumnAdapter

/**
 * Type-safe enumeration of every domain stored by [EntityStore]. Adding a new domain is "add a new
 * subtype here, register a codec, done" — never "scatter a string."
 *
 * The on-disk column is `TEXT`, so adding a subtype is a zero-migration change. See
 * docs/storage/storage_r1_design.md §4.2.1 for the rationale.
 */
sealed interface CollectionKind {
  /**
   * Stable wire name — persisted as the `collection` column value. Never change once shipped.
   *
   * **New kinds use Thing vocabulary, never aircraft vocabulary**, unless the thing being named is
   * genuinely and permanently an airplane (`Engine`, `Propeller`, and `EngineHourRule` qualify; a
   * generic per-Thing record does not). The domain is Things now, and an `aircraft`-shaped name on
   * something that will hold a boat or a 3D printer is a lie the compiler cannot catch.
   *
   * This is not a style preference. [wireName] and [schemaName] are **stored data** — the former is
   * a Firestore collection segment, the latter is written into every document's envelope. Renaming
   * either after it ships is a full data migration: a global batch, a grace window, a coordinated
   * client release. Milestone 1 did exactly that for one kind, and issue #638 records the decision
   * not to do it again for the five `aircraft.*` schemaNames still below. Those are grandfathered,
   * not exemplary — do not copy them.
   *
   * Getting the name right costs nothing here and everything later.
   */
  val wireName: String

  /**
   * Fully qualified proto name — stored alongside payloads as a forensic tag.
   *
   * Same rule and the same reason as [wireName]: new entries name Things. The `aircraft.*` values
   * below predate the pivot and stay only because moving them is not worth a migration (#638).
   */
  val schemaName: String

  data object Thing : CollectionKind {
    override val wireName = "thing"
    override val schemaName = "thing.Thing"
  }

  data object MaintenanceTask : CollectionKind {
    override val wireName = "maintenance_task"
    override val schemaName = "aircraft.MaintenanceTask"
  }

  data object MaintenanceLog : CollectionKind {
    override val wireName = "maintenance_log"
    override val schemaName = "aircraft.MaintenanceLog"
  }

  data object MaintenanceOverview : CollectionKind {
    override val wireName = "maintenance_overview"
    override val schemaName = "aircraft.MaintenanceOverview"
  }

  data object Technician : CollectionKind {
    override val wireName = "technician"
    override val schemaName = "aircraft.Technician"
  }

  data object UserInfo : CollectionKind {
    override val wireName = "user_info"
    override val schemaName = "userinfo.UserInfo"
  }

  data object DeveloperOptions : CollectionKind {
    override val wireName = "developer_settings"
    override val schemaName = "settings.DeveloperSettings"
  }

  /**
   * Account-level subscription entitlement (SquawkIt Pro). Server-authoritative: written only by
   * Cloud Functions at the top-level `subscriptions/{uid}` doc and mirrored read-only into the
   * local store. See docs/subscription/subscription_design.html §3.
   */
  data object Subscription : CollectionKind {
    override val wireName = "subscription"
    override val schemaName = "settings.Subscription"
  }

  data object Squawk : CollectionKind {
    override val wireName = "squawk"
    override val schemaName = "aircraft.Squawk"
  }

  /**
   * Account-level notification preferences (docs/notifications/notifications_design.md §4). In
   * `TOP_LEVEL_KINDS` — unlike [DeveloperOptions] — so it hydrates onto a second device; see
   * `NotificationPrefsManager`'s hydration-resolution rule for why that distinction matters here.
   */
  data object NotificationSettings : CollectionKind {
    override val wireName = "notification_settings"
    override val schemaName = "settings.NotificationSettings"
  }

  /**
   * Comments on a Thing's records (squawks, maintenance tasks). One collection per Thing, with the
   * thread selected by the payload's `parent_type` + `parent_id` — see comment.proto for why the
   * comments are their own rows rather than a repeated field on the parent.
   */
  data object Comment : CollectionKind {
    override val wireName = "comment"
    override val schemaName = "thing.Comment"
  }

  /**
   * Member-side index of thing shared *into* this account. Lives at
   * `users/{uid}/shared_aircraft_ref/{thingId}` and drives the sync engine's foreign-scope
   * fan-out. See docs/sharing §2.2.
   */
  data object SharedAircraftRef : CollectionKind {
    override val wireName = "shared_aircraft_ref"
    override val schemaName = "sharing.SharedAircraftRef"
  }

  companion object {
    /**
     * The complete, ordered list of [CollectionKind] subtypes. The CollectionKindCoverageTest in
     * commonTest asserts that this list matches `CollectionKind::class.sealedSubclasses`, so a
     * forgotten entry fails the build rather than corrupting data at runtime.
     */
    val ALL: List<CollectionKind> = listOf(
      Thing,
      MaintenanceTask,
      MaintenanceLog,
      MaintenanceOverview,
      Technician,
      UserInfo,
      DeveloperOptions,
      Subscription,
      Squawk,
      Comment,
      SharedAircraftRef,
      NotificationSettings,
    )

    private val byWire: Map<String, CollectionKind> =
      ALL.associateBy { it.wireName }

    /** @throws IllegalStateException if [wire] does not name a registered [CollectionKind]. */
    fun fromWire(wire: String): CollectionKind =
      byWire[wire]
        ?: error("Unknown collection '$wire' — register it in CollectionKind")
  }
}

/** SQLDelight `ColumnAdapter` that maps the `TEXT` `collection` column to [CollectionKind]. */
val collectionKindAdapter: ColumnAdapter<CollectionKind, String> =
  object : ColumnAdapter<CollectionKind, String> {
    override fun decode(databaseValue: String): CollectionKind =
      CollectionKind.fromWire(databaseValue)

    override fun encode(value: CollectionKind): String = value.wireName
  }
