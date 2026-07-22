package dev.fanfly.wingslog.feature.developeroptions.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.settings.Subscription
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperFlags
import org.junit.Test

class DeveloperSettingsMappingTest {

  @Test
  fun `force subscription status round-trips through the proto`() {
    val values = listOf(null, Subscription.Status.STATUS_FREE, Subscription.Status.STATUS_PRO)
    for (status in values) {
      val flags = DeveloperFlags(forceSubscriptionStatus = status)
      assertThat(flags.toProto().toDeveloperFlags().forceSubscriptionStatus).isEqualTo(status)
    }
  }
}
