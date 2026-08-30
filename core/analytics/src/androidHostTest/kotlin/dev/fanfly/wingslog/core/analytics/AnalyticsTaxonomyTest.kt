package dev.fanfly.wingslog.core.analytics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The taxonomy's guard rail.
 *
 * GA4 rejects a malformed event or parameter name silently — the SDK call succeeds, nothing arrives,
 * and the gap is discovered when someone builds the report months later. Nothing in the type system
 * catches a bad string, so it is checked here instead, exhaustively over the enums rather than over
 * a hand-kept list that a new entry can be added without.
 */
class AnalyticsTaxonomyTest {

  // GA4: ≤40 characters, letters/digits/underscore only, must start with a letter.
  private val legalName = Regex("^[a-z][a-z0-9_]{0,39}$")

  // Reserved by Firebase — an event using one of these prefixes is dropped.
  private val reservedPrefixes = listOf("firebase_", "google_", "ga_")

  /**
   * Reserved *names*, which the prefix check cannot see. Firebase auto-collects these, and a custom
   * event using one is silently renamed to its internal short form and merged with the automatic
   * event — it arrives, the metric looks healthy, and it is measuring something else.
   *
   * This is not hypothetical: `ad_impression` and `ad_click` shipped, and a device run for #667
   * caught `Renaming ad_impression to _ai` in the SDK log, conflating our per-slot count with
   * AdMob's. They are now `ad_unit_impression` / `ad_unit_click`.
   *
   * Not exhaustive — Firebase's list is long and grows. These are the ones an app like this one is
   * realistically tempted by; add more as they come up.
   */
  private val reservedNames = listOf(
    "ad_activeview", "ad_click", "ad_exposure", "ad_impression", "ad_query", "ad_reward",
    "adunit_exposure", "app_background", "app_clear_data", "app_exception", "app_remove",
    "app_store_refund", "app_store_subscription_cancel", "app_store_subscription_convert",
    "app_store_subscription_renew", "app_update", "app_upgrade", "error", "first_open",
    "first_visit", "in_app_purchase", "notification_dismiss", "notification_foreground",
    "notification_open", "notification_receive", "os_update", "screen_view", "session_start",
    "user_engagement",
  )

  @Test
  fun everyEventNameIsGa4Legal() {
    val offenders = AnalyticsEvent.Name.entries
      .filterNot { legalName.matches(it.wire) }
      .map { "${it.name} -> \"${it.wire}\"" }

    assertThat(offenders).isEmpty()
  }

  @Test
  fun everyParamNameIsGa4Legal() {
    val offenders = AnalyticsEvent.Param.entries
      .filterNot { legalName.matches(it.wire) }
      .map { "${it.name} -> \"${it.wire}\"" }

    assertThat(offenders).isEmpty()
  }

  @Test
  fun noNameUsesAReservedFirebasePrefix() {
    val offenders = (
      AnalyticsEvent.Name.entries.map { it.wire } +
        AnalyticsEvent.Param.entries.map { it.wire }
      ).filter { wire -> reservedPrefixes.any { wire.startsWith(it) } }

    assertThat(offenders).isEmpty()
  }

  @Test
  fun noEventNameCollidesWithAFirebaseReservedName() {
    // The failure this exists for: a reserved name is not rejected, it is *renamed and merged*.
    // Nothing in the build, the SDK's return value, or GA4's UI says so — only the verbose device
    // log does, and only if someone is watching it.
    val offenders = AnalyticsEvent.Name.entries
      .filter { it.wire in reservedNames }
      .map { "${it.name} -> \"${it.wire}\"" }

    assertThat(offenders).isEmpty()
  }

  @Test
  fun noTwoEntriesShareAWireName() {
    // Two enum entries mapping to one wire name silently merge two different things into one GA4
    // series — worse than a missing event, because the number looks plausible.
    val eventDuplicates = AnalyticsEvent.Name.entries.map { it.wire }
      .groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    val paramDuplicates = AnalyticsEvent.Param.entries.map { it.wire }
      .groupingBy { it }.eachCount().filterValues { it > 1 }.keys

    assertThat(eventDuplicates).isEmpty()
    assertThat(paramDuplicates).isEmpty()
  }

  /**
   * The shipped names, pinned. These are already emitting in production, so changing one orphans its
   * history rather than renaming it — the analytics equivalent of a wire-identity change (#638).
   * A rename shows up here as a failure that has to be argued with, which is the point.
   */
  @Test
  fun shippedEventNamesNeverChange() {
    assertThat(AnalyticsEvent.Name.AD_SLOT_FILLED.wire).isEqualTo("ad_slot_filled")
    // Renamed out of a reserved-name collision found on-device in #667 — see reservedNames.
    // The append-only rule does not protect these two: the old names never had a series of their
    // own to orphan, because Firebase was merging them into its own.
    assertThat(AnalyticsEvent.Name.AD_UNIT_IMPRESSION.wire).isEqualTo("ad_unit_impression")
    assertThat(AnalyticsEvent.Name.AD_FILL_FAILED.wire).isEqualTo("ad_fill_failed")
    assertThat(AnalyticsEvent.Name.AD_UNIT_CLICK.wire).isEqualTo("ad_unit_click")
    assertThat(AnalyticsEvent.Name.SYNC_PERMISSION_DENIED_WRITE.wire)
      .isEqualTo("sync_permission_denied_write")
    assertThat(AnalyticsEvent.Name.SYNC_SHARE_RECONCILED.wire).isEqualTo("sync_share_reconciled")
    assertThat(AnalyticsEvent.Name.URGENCY_NOTIFICATION_POSTED.wire)
      .isEqualTo("urgency_notification_posted")
  }

  @Test
  fun shippedParamNamesNeverChange() {
    assertThat(AnalyticsEvent.Param.SURFACE.wire).isEqualTo("surface")
    assertThat(AnalyticsEvent.Param.SLOT_INDEX.wire).isEqualTo("slot_index")
    assertThat(AnalyticsEvent.Param.UNIT_POSITION.wire).isEqualTo("unit_position")
    assertThat(AnalyticsEvent.Param.REASON.wire).isEqualTo("reason")
    assertThat(AnalyticsEvent.Param.SCOPE.wire).isEqualTo("scope")
    assertThat(AnalyticsEvent.Param.TRIGGER.wire).isEqualTo("trigger")
    // Aviation-specific and staying that way: it is already emitting.
    assertThat(AnalyticsEvent.Param.SHARED_FLEET.wire).isEqualTo("shared_fleet")
  }

  /**
   * The typed events must flatten to exactly the maps the untyped call sites send today, or #665
   * silently changes the shape of live series while it swaps call sites over.
   */
  @Test
  fun shippedEventsFlattenToTheirCurrentWireShape() {
    assertThat(AdSlotFilled(surface = "dashboard", slotIndex = 2, unitPosition = "inline_1")
      .toParams())
      .containsExactlyEntriesIn(
        mapOf("surface" to "dashboard", "slot_index" to "2", "unit_position" to "inline_1")
      )

    assertThat(AdFillFailed(surface = "dashboard", reason = "no_fill").toParams())
      .containsExactlyEntriesIn(mapOf("surface" to "dashboard", "reason" to "no_fill"))

    assertThat(SyncPermissionDeniedWrite(shared = true).toParams())
      .containsExactlyEntriesIn(mapOf("scope" to "shared"))
    assertThat(SyncPermissionDeniedWrite(shared = false).toParams())
      .containsExactlyEntriesIn(mapOf("scope" to "own"))

    assertThat(SyncShareReconciled(trigger = "denied_write").toParams())
      .containsExactlyEntriesIn(mapOf("trigger" to "denied_write"))

    assertThat(UrgencyNotificationPosted(trigger = "scheduled", sharedFleet = true).toParams())
      .containsExactlyEntriesIn(mapOf("trigger" to "scheduled", "shared_fleet" to "true"))
  }

  @Test
  fun everyThingScopedEventCarriesTemplateId() {
    val events: List<ThingScopedEvent> = listOf(
      ThingCreated(templateId = "airplane", source = "picker"),
      StarterTasksOffered(templateId = "airplane", taskCount = 5),
      StarterTasksAccepted(templateId = "airplane", taskCount = 3),
      TaskCompleted(templateId = "airplane"),
      DefectCreated(templateId = "airplane"),
      LogCreated(templateId = "airplane"),
      ExportCompleted(templateId = "airplane", format = "pdf", thingCount = 2),
    )

    events.forEach { event ->
      assertThat(event.toParams()).containsEntry("template_id", "airplane")
    }
  }

  @Test
  fun parameterValuesAreTruncatedToGa4Limit() {
    // GA4 truncates rather than rejecting, so a long value must not be able to take the event with
    // it. An ad SDK's failure reason is the realistic source of one.
    val longReason = "x".repeat(250)

    val flattened = AdFillFailed(surface = "dashboard", reason = longReason).toParams()

    assertThat(flattened["reason"]).hasLength(GA4_MAX_PARAM_VALUE_LENGTH)
  }

  @Test
  fun logSendsTheEventNameAndFlattenedParams() {
    val recorded = mutableListOf<Pair<String, Map<String, String>>>()
    val analytics = object : AnalyticsManager {
      override fun logScreenView(screenName: String, params: Map<String, String>) = Unit
      override fun logEvent(name: String, params: Map<String, String>) {
        recorded += name to params
      }

      override fun setAnalyticsCollectionEnabled(enabled: Boolean) = Unit
    }

    analytics.log(ThingCreated(templateId = "bicycle", source = "picker"))

    assertThat(recorded).containsExactly(
      "thing_created" to mapOf("template_id" to "bicycle", "source" to "picker")
    )
  }
}
