package dev.fanfly.wingslog.feature.ads.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.ads.model.AdConsentState
import org.junit.Test

class AndroidAdConsentManagerTest {

  @Test
  fun `canRequestAds false is DENIED regardless of the privacy options flag`() {
    assertThat(deriveConsentState(canRequestAds = false, privacyOptionsRequired = true))
      .isEqualTo(AdConsentState.DENIED)
    assertThat(deriveConsentState(canRequestAds = false, privacyOptionsRequired = false))
      .isEqualTo(AdConsentState.DENIED)
  }

  @Test
  fun `a region that requires a privacy choice is non-personalized once resolved`() {
    assertThat(deriveConsentState(canRequestAds = true, privacyOptionsRequired = true))
      .isEqualTo(AdConsentState.NON_PERSONALIZED)
  }

  @Test
  fun `no privacy choice required means personalized`() {
    assertThat(deriveConsentState(canRequestAds = true, privacyOptionsRequired = false))
      .isEqualTo(AdConsentState.PERSONALIZED)
  }
}
