package dev.fanfly.wingslog.feature.notifications.analytics

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import dev.fanfly.wingslog.feature.notifications.model.ScanTrigger
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * Design §12.3's gate is the point of this class, so it is what the tests are about: N2 is
 * otherwise entirely on-device, and this telemetry is the one thing that could put a scan on the
 * network for a pilot whose fleet has never left the phone.
 */
class AnalyticsUrgencyTelemetryTest {

  private class RecordingAnalytics : AnalyticsManager {
    val events = mutableListOf<Pair<String, Map<String, String>>>()
    override fun logScreenView(screenName: String, params: Map<String, String>) = Unit
    override fun logEvent(name: String, params: Map<String, String>) {
      events += name to params
    }

    override fun setAnalyticsCollectionEnabled(enabled: Boolean) = Unit
  }

  private val analytics = RecordingAnalytics()

  private fun telemetry(
    signedIn: Boolean = true,
    anonymous: Boolean = false,
    cloudSync: Boolean = true,
  ): AnalyticsUrgencyTelemetry {
    val user = mockk<FirebaseUser> { every { isAnonymous } returns anonymous }
    return AnalyticsUrgencyTelemetry(
      analytics = analytics,
      auth = mockk<FirebaseAuth> { every { currentUser } returns if (signedIn) user else null },
      cloudSync = CloudSyncSetting { cloudSync },
    )
  }

  // --- the gate ---

  @Test
  fun anonymousAccount_reportsNothing() {
    telemetry(anonymous = true).urgencyNotificationsPosted(ScanTrigger.SCHEDULED, 2, false)
    assertThat(analytics.events).isEmpty()
  }

  @Test
  fun cloudSyncOff_reportsNothing() {
    telemetry(cloudSync = false).urgencyNotificationsPosted(ScanTrigger.SCHEDULED, 2, false)
    assertThat(analytics.events).isEmpty()
  }

  @Test
  fun signedOut_reportsNothing() {
    telemetry(signedIn = false).urgencyNotificationsPosted(ScanTrigger.SCHEDULED, 2, false)
    assertThat(analytics.events).isEmpty()
  }

  /** Both conditions have to hold, not either. */
  @Test
  fun anonymousWithCloudSyncOn_stillReportsNothing() {
    telemetry(anonymous = true, cloudSync = true)
      .urgencyNotificationsPosted(ScanTrigger.SCHEDULED, 1, false)
    assertThat(analytics.events).isEmpty()
  }

  // --- what it reports when allowed ---

  @Test
  fun signedInWithCloudSync_reportsOneEventPerNotification() {
    telemetry().urgencyNotificationsPosted(ScanTrigger.SESSION_BOUNDARY, 3, sharedFleet = true)

    assertThat(analytics.events).hasSize(3)
    assertThat(analytics.events.map { it.first }.distinct())
      .containsExactly("urgency_notification_posted")
  }

  /** The trigger split is the number that decides whether iOS can claim a cadence at all (§6.6). */
  @Test
  fun reportsTheTriggerAndWhetherTheFleetIsShared() {
    telemetry().urgencyNotificationsPosted(ScanTrigger.SCHEDULED, 1, sharedFleet = false)

    assertThat(analytics.events.single().second)
      .containsExactly("trigger", "scheduled", "shared_fleet", "false")
  }

  @Test
  fun sharedFleet_isReportedAsTrue() {
    telemetry().urgencyNotificationsPosted(ScanTrigger.MANUAL, 1, sharedFleet = true)

    assertThat(analytics.events.single().second["shared_fleet"]).isEqualTo("true")
  }

  /** A scan that posted nothing is not an event — the metric counts notifications, not scans. */
  @Test
  fun zeroNotifications_reportsNothing() {
    telemetry().urgencyNotificationsPosted(ScanTrigger.SCHEDULED, 0, false)
    assertThat(analytics.events).isEmpty()
  }
}
