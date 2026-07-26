package dev.fanfly.wingslog.feature.tasks.update.compose

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BannerHelpersTest {

  // ── formatEngineHours ────────────────────────────────────────────────────

  @Test
  fun formatEngineHours_integerValue_hasNoDecimal() {
    assertThat(formatEngineHours(100f)).isEqualTo("100")
  }

  @Test
  fun formatEngineHours_decimalValue_keepsOneDecimal() {
    assertThat(formatEngineHours(100.5f)).isEqualTo("100.5")
  }

  @Test
  fun formatEngineHours_roundsToOneDecimal() {
    assertThat(formatEngineHours(100.567f)).isEqualTo("100.6")
  }

  @Test
  fun formatEngineHours_roundsDownToInteger_dropsDecimal() {
    // 100.04 rounds to 100.0 → integer formatting.
    assertThat(formatEngineHours(100.04f)).isEqualTo("100")
  }
}
