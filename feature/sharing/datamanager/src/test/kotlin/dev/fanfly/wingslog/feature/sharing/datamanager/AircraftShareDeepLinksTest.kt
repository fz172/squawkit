package dev.fanfly.wingslog.feature.sharing.datamanager

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

private const val CODE = "EFA2GGTH"

class AircraftShareDeepLinksTest {

  @After
  fun tearDown() = AircraftShareDeepLinks.consume()

  @Test
  fun parses_a_code_link() {
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#$CODE"))
      .isEqualTo(ShareInvite(CODE))
  }

  @Test
  fun a_link_names_no_aircraft_and_no_host() {
    // The entire point of #164. An aircraft id in an invite is a capability: whoever holds one can
    // fabricate a same-id aircraft and read the victim's ACL and technician certificate numbers
    // (#202), or re-claim an abandoned share (#204). The code names nothing real.
    val invite = AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#$CODE")

    assertThat(invite?.code).isEqualTo(CODE)
    assertThat(ShareInvite::class.members.map { it.name }).doesNotContain("aircraftId")
    assertThat(ShareInvite::class.members.map { it.name }).doesNotContain("hostUid")
  }

  @Test
  fun accepts_the_displayed_grouping_and_lowercase() {
    // A link and a hand-typed code go down exactly one path, so "EFA2-GGTH" pasted from a text
    // message works as well as the raw code.
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#efa2-ggth"))
      .isEqualTo(ShareInvite(CODE))
  }

  @Test
  fun tolerates_query_params_and_a_trailing_slash() {
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share?utm=x#$CODE"))
      .isEqualTo(ShareInvite(CODE))
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share/#$CODE"))
      .isEqualTo(ShareInvite(CODE))
  }

  @Test
  fun rejects_legacy_links_rather_than_guessing_at_them() {
    // Pre-#164 links carried {aircraftId}.{secret} or {hostUid}.{aircraftId}.{secret}. The mechanism
    // they addressed is gone. Dots are not in the code alphabet, so they normalize to nothing.
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#ac-1.sEcReT")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#host.ac-1.sEcReT")).isNull()
  }

  @Test
  fun rejects_non_share_links_and_malformed_codes() {
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/finishSignIn?apiKey=x")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/other#$CODE")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#SHORT")).isNull()
    assertThat(AircraftShareDeepLinks.parse("https://squawkit.fanfly.dev/share#TOOLONGCODE")).isNull()
  }

  @Test
  fun deliver_parks_a_share_link_and_ignores_others() {
    assertThat(AircraftShareDeepLinks.deliver("https://squawkit.fanfly.dev/finishSignIn")).isFalse()
    assertThat(AircraftShareDeepLinks.pendingInvite.value).isNull()

    assertThat(AircraftShareDeepLinks.deliver("https://squawkit.fanfly.dev/share#$CODE")).isTrue()
    assertThat(AircraftShareDeepLinks.pendingInvite.value).isEqualTo(ShareInvite(CODE))

    AircraftShareDeepLinks.consume()
    assertThat(AircraftShareDeepLinks.pendingInvite.value).isNull()
  }

  @Test
  fun deliverCode_parks_a_typed_code_down_the_same_path() {
    // #209: a hand-typed code lands on the same channel a link fills, normalized the same way —
    // grouping and case included, so what the field shows and what parks agree. It parks as
    // auto-accept: typing the code was the consent, so the redeem flow skips the confirm dialog.
    assertThat(AircraftShareDeepLinks.deliverCode("efa2-ggth")).isTrue()
    assertThat(AircraftShareDeepLinks.pendingInvite.value)
      .isEqualTo(ShareInvite(CODE, autoAccept = true))
  }

  @Test
  fun deliverCode_refuses_a_malformed_code_and_parks_nothing() {
    // A refused code keeps the entry screen open rather than parking nonsense that would only
    // surface an error sheet.
    assertThat(AircraftShareDeepLinks.deliverCode("SHORT")).isFalse()
    assertThat(AircraftShareDeepLinks.pendingInvite.value).isNull()
  }
}
