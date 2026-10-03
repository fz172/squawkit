package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import com.google.common.truth.Truth.assertThat
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import org.junit.Test

class SkippedTextTest {

  private val now = Instant.parse("2026-10-03T09:00:00Z")

  @Test
  fun `later today reads as the time alone`() {
    assertThat(Instant.parse("2026-10-03T15:10:00Z").toDisplayWhen(now, TimeZone.UTC)).isEqualTo("03:10 PM")
  }

  @Test
  fun `tomorrow carries its date`() {
    assertThat(Instant.parse("2026-10-04T08:59:00Z").toDisplayWhen(now, TimeZone.UTC)).isEqualTo("10/04/2026 08:59 AM")
  }
}
