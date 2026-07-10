package dev.fanfly.wingslog.feature.sharing.datamanager

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class AircraftShareDeepLinksTest {

  @After
  fun tearDown() = AircraftShareDeepLinks.consume()

  @Test
  fun parses_a_valid_share_link() {
    val invite = AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#ac-123.SEKret_xyz")
    assertThat(invite).isEqualTo(ShareInvite("ac-123", "SEKret_xyz"))
  }

  @Test
  fun splits_on_the_first_dot_so_uuid_hyphens_survive() {
    val invite = AircraftShareDeepLinks.parse(
      "https://squawkit.fanfly.dev/share#af5d2572-bc2f-4802-8837-6e9b80f4e37c.HteZKufQAFiQ3TmVGbof8g",
    )
    assertThat(invite?.aircraftId).isEqualTo("af5d2572-bc2f-4802-8837-6e9b80f4e37c")
    assertThat(invite?.secret).isEqualTo("HteZKufQAFiQ3TmVGbof8g")
  }

  @Test
  fun tolerates_query_params_and_a_trailing_slash() {
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share?utm=x#ac.secret"))
      .isEqualTo(ShareInvite("ac", "secret"))
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share/#ac.secret"))
      .isEqualTo(ShareInvite("ac", "secret"))
  }

  @Test
  fun rejects_non_share_links() {
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/finishSignIn?apiKey=x")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/other#ac.secret")).isNull()
  }

  @Test
  fun rejects_missing_fragment_or_secret() {
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#ac")).isNull() // no dot/secret
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#.secret")).isNull() // blank id
  }

  @Test
  fun deliver_parks_a_share_link_and_ignores_others() {
    assertThat(AircraftShareDeepLinks.deliver("https://squawkit.fanfly.dev/finishSignIn")).isFalse()
    assertThat(AircraftShareDeepLinks.pendingInvite.value).isNull()

    assertThat(AircraftShareDeepLinks.deliver("https://squawkit.fanfly.dev/share#ac.secret")).isTrue()
    assertThat(AircraftShareDeepLinks.pendingInvite.value).isEqualTo(ShareInvite("ac", "secret"))

    AircraftShareDeepLinks.consume()
    assertThat(AircraftShareDeepLinks.pendingInvite.value).isNull()
  }
}
