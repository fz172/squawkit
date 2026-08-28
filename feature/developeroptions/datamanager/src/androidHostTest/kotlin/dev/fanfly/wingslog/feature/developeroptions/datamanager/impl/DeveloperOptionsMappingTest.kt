package dev.fanfly.wingslog.feature.developeroptions.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags
import org.junit.Test

/**
 * Guards the seam between [DeveloperFlags] and the `DeveloperSettings` proto.
 *
 * These are independent types, so adding a field to the data class alone compiles cleanly and then
 * silently drops the value on write and reads back the default. The symptom is a Developer Options
 * toggle that flips and immediately snaps back — which is what `forceAds` did until the proto gained
 * its field. Nothing about that is visible at compile time, so it needs a test.
 */
class DeveloperOptionsMappingTest {

  @Test
  fun `every flag survives a round trip through the proto`() {
    // Deliberately all-non-default: a mapper that drops a field cannot pass by accident, which it
    // could if the fixture happened to use the value the proto defaults to.
    val flags = DeveloperFlags(
      forceSubscriptionStatus = Subscription.Status.STATUS_PRO,
      forceAds = true,
      adConsentTestDeviceHashedId = "33BE2250B43518CCDA7DE426D04EE231",
    )

    assertThat(
      flags.toProto()
        .toDeveloperFlags()
    ).isEqualTo(flags)
  }

  @Test
  fun `defaults survive a round trip`() {
    val flags = DeveloperFlags()
    assertThat(
      flags.toProto()
        .toDeveloperFlags()
    ).isEqualTo(flags)
  }

  @Test
  fun `force ads round trips independently of the tier override`() {
    // The bug: forceAds was dropped while forceSubscriptionStatus mapped fine, so the flags object
    // looked half-persisted rather than broken.
    val adsOnly = DeveloperFlags(forceAds = true)
    assertThat(
      adsOnly.toProto()
        .toDeveloperFlags().forceAds
    ).isTrue()

    val tierOnly =
      DeveloperFlags(forceSubscriptionStatus = Subscription.Status.STATUS_FREE)
    assertThat(
      tierOnly.toProto()
        .toDeveloperFlags().forceAds
    ).isFalse()
  }

  @Test
  fun `a blank test device hash maps back to null, not empty string`() {
    assertThat(
      DeveloperFlags().toProto()
        .toDeveloperFlags().adConsentTestDeviceHashedId
    ).isNull()

    val set = DeveloperFlags(adConsentTestDeviceHashedId = "ABC123")
    assertThat(
      set.toProto()
        .toDeveloperFlags().adConsentTestDeviceHashedId
    ).isEqualTo("ABC123")
  }

  @Test
  fun `each forced tier maps both ways`() {
    listOf(
      null,
      Subscription.Status.STATUS_FREE,
      Subscription.Status.STATUS_PRO,
    ).forEach { status ->
      val flags = DeveloperFlags(forceSubscriptionStatus = status)
      assertThat(
        flags.toProto()
          .toDeveloperFlags().forceSubscriptionStatus
      ).isEqualTo(status)
    }
  }
}
