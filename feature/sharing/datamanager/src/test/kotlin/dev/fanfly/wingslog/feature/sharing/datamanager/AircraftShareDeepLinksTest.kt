package dev.fanfly.wingslog.feature.sharing.datamanager

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

private const val HOST = "w202CfT3czaX1JOSZrV39S0VYS33"
private const val AC = "af5d2572-bc2f-4802-8837-6e9b80f4e37c"
private const val SECRET = "HteZKufQAFiQ3TmVGbof8g"

class AircraftShareDeepLinksTest {

  @After
  fun tearDown() = AircraftShareDeepLinks.consume()

  @Test
  fun parses_a_valid_share_link() {
    val invite = AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#$HOST.$AC.$SECRET")

    assertThat(invite).isEqualTo(ShareInvite(HOST, AC, SECRET))
  }

  @Test
  fun uuid_hyphens_and_base64url_survive_the_split() {
    // Uids, UUIDs and base64url secrets contain no dots, so splitting on them is unambiguous.
    val invite = AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#$HOST.$AC.$SECRET")

    assertThat(invite?.hostUid).isEqualTo(HOST)
    assertThat(invite?.aircraftId).isEqualTo(AC)
    assertThat(invite?.secret).isEqualTo(SECRET)
  }

  @Test
  fun tolerates_query_params_and_a_trailing_slash() {
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share?utm=x#h.ac.secret"))
      .isEqualTo(ShareInvite("h", "ac", "secret"))
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share/#h.ac.secret"))
      .isEqualTo(ShareInvite("h", "ac", "secret"))
  }

  @Test
  fun rejects_a_legacy_link_that_names_no_host() {
    // Pre-#204 links were `{aircraftId}.{secret}`. The ACL they pointed at has moved, and guessing a
    // host would be worse than failing: it would build a path into whichever share matched the id.
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#$AC.$SECRET")).isNull()
  }

  @Test
  fun rejects_non_share_links() {
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/finishSignIn?apiKey=x")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/other#h.ac.secret")).isNull()
  }

  @Test
  fun rejects_missing_or_blank_parts() {
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#h.ac")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#.ac.secret")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#h..secret")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#h.ac.")).isNull()
  }

  @Test
  fun deliver_parks_a_share_link_and_ignores_others() {
    assertThat(AircraftShareDeepLinks.deliver("https://squawkit.fanfly.dev/finishSignIn")).isFalse()
    assertThat(AircraftShareDeepLinks.pendingInvite.value).isNull()

    assertThat(AircraftShareDeepLinks.deliver("https://squawkit.fanfly.dev/share#h.ac.secret")).isTrue()
    assertThat(AircraftShareDeepLinks.pendingInvite.value).isEqualTo(ShareInvite("h", "ac", "secret"))

    AircraftShareDeepLinks.consume()
    assertThat(AircraftShareDeepLinks.pendingInvite.value).isNull()
  }
}
