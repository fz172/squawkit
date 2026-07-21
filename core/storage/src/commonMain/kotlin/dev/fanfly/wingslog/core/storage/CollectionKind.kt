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
  /** Stable wire name — persisted as the `collection` column value. Never change once shipped. */
  val wireName: String

  /** Fully qualified proto name — stored alongside payloads as a forensic tag. */
  val schemaName: String

  data object Aircraft : CollectionKind {
    override val wireName = "aircraft"
    override val schemaName = "aircraft.Aircraft"
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

  data object FeatureLab : CollectionKind {
    override val wireName = "feature_lab_settings"
    override val schemaName = "settings.FeatureLabSettings"
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
   * Member-side index of aircraft shared *into* this account. Lives at
   * `users/{uid}/shared_aircraft_ref/{aircraftId}` and drives the sync engine's foreign-scope
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
      Aircraft,
      MaintenanceTask,
      MaintenanceLog,
      MaintenanceOverview,
      Technician,
      UserInfo,
      FeatureLab,
      Subscription,
      Squawk,
      SharedAircraftRef,
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
